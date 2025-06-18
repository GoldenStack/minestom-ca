package net.minestom.ca.backends.lazy;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minestom.ca.Automata;
import net.minestom.ca.Neighbors;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;
import net.minestom.server.network.packet.server.play.MultiBlockChangePacket;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.*;
import java.util.stream.Collectors;

import static net.minestom.ca.CoordConversionPro.*;
import static net.minestom.ca.backends.lazy.LazyWorld.LSection.BLOCKS_PER_SECTION;
import static net.minestom.server.coordinate.CoordConversion.globalToChunk;
import static net.minestom.server.coordinate.CoordConversion.globalToSectionRelative;

@SuppressWarnings("UnstableApiUsage")
public final class LazyWorld implements Automata.World {
    private final Instance instance;
    private final ContextImpl context = new ContextImpl();
    private final QueryImpl query = new QueryImpl();
    private final int sectionCount;
    private final int minY;

    private List<Automata.RuleConfig> ruleConfigs;
    private List<Automata.CellRule> rules;
    private List<Automata.CellRule.State> orderedStates;
    private Map<Automata.CellRule.State, Integer> rulesMapping;
    private StateLayout stateLayout;

    private List<RuleContext> ruleContexts;

    private int lightSpeed;

    private final HashedWheelTimer<ScheduledChange> wheelTimer = new HashedWheelTimer<>(255);
    private final Long2ObjectMap<LSection> loadedSections = new Long2ObjectOpenHashMap<>();

    private final class RuleContext {
        private final Set<LSection> trackedSections = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Automata.RuleConfig ruleConfig;

        RuleContext(Automata.RuleConfig ruleConfig) {
            this.ruleConfig = ruleConfig;
        }

        boolean interested(int index, long value) {
            for (Automata.CellRule.Interest interest : ruleConfig.rule().interests()) {
                final int interestIndex = query.stateIndex(interest.state());
                if (interestIndex != index) continue;
                if (interest.predicate().test(value)) return true;
            }
            return false;
        }
    }

    // Layout information for states
    private static final class StateLayout {
        final int[] stateBitSizes;   // Bit size for each state
        final long[] stateMasks;     // Bit mask for each state
        final int[] bitsPerLong;     // How many values fit in one long for each state
        final long[] segmentSizes;   // Size in bytes for each state's segment

        StateLayout(List<Automata.CellRule.State> states) {
            final int stateCount = states.size();
            this.stateBitSizes = new int[stateCount];
            this.stateMasks = new long[stateCount];
            this.bitsPerLong = new int[stateCount];
            this.segmentSizes = new long[stateCount];

            for (int i = 0; i < stateCount; i++) {
                final Automata.CellRule.State state = states.get(i);
                final int bitSize = state.bitSize();
                this.stateBitSizes[i] = bitSize;
                this.stateMasks[i] = (1L << bitSize) - 1;
                this.bitsPerLong[i] = 64 / bitSize; // How many values per long

                // Calculate required longs and segment size
                final long valuesPerLong = this.bitsPerLong[i];
                final long requiredLongs = (BLOCKS_PER_SECTION + valuesPerLong - 1) / valuesPerLong;
                this.segmentSizes[i] = requiredLongs * Long.BYTES;
            }
        }
    }

    final class LSection {
        static final long BLOCKS_PER_SECTION = 16 * 16 * 16;
        private final long index;
        private MemorySegment[] stateSegments; // One segment per state
        // Block indexes to track next tick, one per rule
        private BitSet[] trackedBlocks;

        LSection(final long index) {
            this.index = index;
            // Initialize with null segments - allocate lazily
            this.stateSegments = new MemorySegment[stateLayout.segmentSizes.length];
            this.trackedBlocks = new BitSet[rules.size()];
        }

        private MemorySegment ensureSegmentAllocated(int stateIndex) {
            MemorySegment segment = stateSegments[stateIndex];
            if (stateSegments[stateIndex] == null) {
                segment = stateSegments[stateIndex] = Arena.ofAuto().allocate(stateLayout.segmentSizes[stateIndex]);
            }
            return segment;
        }

        long getState(int x, int y, int z, int stateIndex) {
            final MemorySegment segment = stateSegments[stateIndex];
            if (segment == null) return 0; // Segment not allocated = all zeros

            final int blockIndex = sectionBlockIndex(x, y, z);
            final int bitSize = stateLayout.stateBitSizes[stateIndex];
            final int valuesPerLong = stateLayout.bitsPerLong[stateIndex];
            final long mask = stateLayout.stateMasks[stateIndex];

            final int longIndex = blockIndex / valuesPerLong;
            final int bitOffset = (blockIndex % valuesPerLong) * bitSize;
            final long offset = (long) longIndex * Long.BYTES;

            final long packed = segment.get(ValueLayout.JAVA_LONG, offset);
            return (packed >>> bitOffset) & mask;
        }

        void setState(int x, int y, int z, int stateIndex, long value) {
            MemorySegment segment = stateSegments[stateIndex];
            if (value == 0 && segment == null) return; // No need to allocate for zero values

            segment = ensureSegmentAllocated(stateIndex);

            final int blockIndex = sectionBlockIndex(x, y, z);
            final int bitSize = stateLayout.stateBitSizes[stateIndex];
            final int valuesPerLong = stateLayout.bitsPerLong[stateIndex];
            final long mask = stateLayout.stateMasks[stateIndex];

            final int longIndex = blockIndex / valuesPerLong;
            final int bitOffset = (blockIndex % valuesPerLong) * bitSize;
            final long offset = (long) longIndex * Long.BYTES;

            final long packed = segment.get(ValueLayout.JAVA_LONG, offset);
            final long cleared = packed & ~(mask << bitOffset);
            final long updated = cleared | ((value & mask) << bitOffset);
            segment.set(ValueLayout.JAVA_LONG, offset, updated);
        }

        boolean anyState(int x, int y, int z) {
            final int blockIndex = sectionBlockIndex(x, y, z);
            for (int stateIndex = 0; stateIndex < stateSegments.length; stateIndex++) {
                if (getStateByBlockIndex(blockIndex, stateIndex) != 0) return true;
            }
            return false;
        }

        private long getStateByBlockIndex(int blockIndex, int stateIndex) {
            if (stateSegments[stateIndex] == null) return 0; // Segment not allocated = all zeros

            final int bitSize = stateLayout.stateBitSizes[stateIndex];
            final int valuesPerLong = stateLayout.bitsPerLong[stateIndex];
            final long mask = stateLayout.stateMasks[stateIndex];

            final int longIndex = blockIndex / valuesPerLong;
            final int bitOffset = (blockIndex % valuesPerLong) * bitSize;
            final long offset = (long) longIndex * Long.BYTES;

            final long packed = stateSegments[stateIndex].get(ValueLayout.JAVA_LONG, offset);
            return (packed >>> bitOffset) & mask;
        }
    }

    Section sectionAt(int sectionX, int sectionY, int sectionZ) {
        final Chunk chunk = instance.getChunk(sectionX, sectionZ);
        return chunk != null ? chunk.getSection(sectionY) : null;
    }

    Palette paletteAtSection(int sectionX, int sectionY, int sectionZ) {
        final Section section = sectionAt(sectionX, sectionY, sectionZ);
        return section != null ? section.blockPalette() : null;
    }

    private final class ContextImpl implements Automata.Context {
        // Tick state
        private long tick = 0;
        // Block state
        LSection section;
        Palette palette;
        int localX, localY, localZ;
        long[] localStates;

        void updateTick() {
            this.tick++;
        }

        void updateLocal(LSection section, Palette palette, int x, int y, int z) {
            this.section = section;
            this.palette = palette;
            this.localX = x;
            this.localY = y;
            this.localZ = z;
            this.localStates = null;
        }

        @Override
        public long tick() {
            return tick;
        }
    }

    private final class QueryImpl implements Automata.Query {
        @Override
        public int stateIndex(Automata.CellRule.State state) {
            final int index = rulesMapping.getOrDefault(state, -1);
            if (index >= 0) return index;
            throw new IllegalArgumentException("Unknown state: " + state);
        }

        @Override
        public long state(int index) {
            if (context.localStates != null) return context.localStates[index];
            final int localX = globalToSectionRelative(context.localX);
            final int localY = globalToSectionRelative(context.localY);
            final int localZ = globalToSectionRelative(context.localZ);
            if (index == 0) return context.palette.get(localX, localY, localZ);
            return context.section.getState(localX, localY, localZ, index - 1);
        }

        @Override
        public long[] queryIndexes() {
            if (context.localStates != null) return context.localStates;
            final int localX = globalToSectionRelative(context.localX);
            final int localY = globalToSectionRelative(context.localY);
            final int localZ = globalToSectionRelative(context.localZ);
            final int blockState = context.palette.get(localX, localY, localZ);
            final List<Automata.CellRule.State> states = orderedStates;
            long[] indexes = new long[states.size() + 1];
            indexes[0] = blockState;
            for (int i = 0; i < states.size(); i++) {
                final long value = context.section.getState(localX, localY, localZ, i);
                indexes[i + 1] = value;
            }
            context.localStates = indexes;
            return indexes;
        }

        LSection querySection(int sectionX, int sectionY, int sectionZ) {
            final long sectionIndex = sectionIndex(sectionX, sectionY, sectionZ);
            if (context.section != null && sectionIndex == context.section.index) return context.section;
            return loadedSections.get(sectionIndex);
        }

        Palette queryPalette(int sectionX, int sectionY, int sectionZ) {
            final long sectionIndex = sectionIndex(sectionX, sectionY, sectionZ);
            if (context.section != null && sectionIndex == context.section.index) return context.palette;
            return paletteAtSection(sectionX, sectionY, sectionZ);
        }

        @Override
        public long stateAt(int x, int y, int z, int index) {
            final int gX = context.localX + x, gY = context.localY + y, gZ = context.localZ + z;
            final int sectionX = globalToChunk(gX), sectionY = globalToChunk(gY), sectionZ = globalToChunk(gZ);
            final int localX = globalToSectionRelative(gX), localY = globalToSectionRelative(gY), localZ = globalToSectionRelative(gZ);
            if (index == 0) {
                final Palette palette = queryPalette(sectionX, sectionY, sectionZ);
                if (palette == null) return 0;
                return palette.get(localX, localY, localZ);
            } else {
                final LSection section = querySection(sectionX, sectionY, sectionZ);
                if (section == null) return 0;
                return section.getState(localX, localY, localZ, index - 1);
            }
        }

        static final long[] EMPTY_INDEXES = new long[]{0};

        @Override
        public long[] queryIndexes(int x, int y, int z) {
            final int gX = context.localX + x, gY = context.localY + y, gZ = context.localZ + z;
            final int sectionX = globalToChunk(gX), sectionY = globalToChunk(gY), sectionZ = globalToChunk(gZ);
            final int localX = globalToSectionRelative(gX), localY = globalToSectionRelative(gY), localZ = globalToSectionRelative(gZ);
            final LSection section = querySection(sectionX, sectionY, sectionZ);
            final Palette palette = queryPalette(sectionX, sectionY, sectionZ);
            if (section == null || palette == null) return EMPTY_INDEXES;
            final List<Automata.CellRule.State> states = orderedStates;
            long[] indexes = new long[states.size() + 1];
            indexes[0] = palette.get(localX, localY, localZ);
            for (int i = 0; i < states.size(); i++) {
                final long value = section.getState(localX, localY, localZ, i);
                indexes[i + 1] = value;
            }
            return indexes;
        }

        @Override
        public Map<String, Long> queryNames(int x, int y, int z) {
            final long[] indexes = queryIndexes(x, y, z);
            if (indexes.length == 1) return Map.of(Automata.CellRule.BLOCK_STATE.name(), indexes[0]);
            Map<Integer, String> variables = new HashMap<>();
            for (Map.Entry<Automata.CellRule.State, Integer> entry : rulesMapping.entrySet()) {
                final Automata.CellRule.State state = entry.getKey();
                final int index = entry.getValue();
                variables.put(index, state.name());
            }
            Map<String, Long> names = new HashMap<>();
            for (int i = 0; i < variables.size(); i++) {
                final String name = variables.get(i);
                final long value = indexes[i];
                assert name != null;
                names.put(name, value);
            }
            return names;
        }
    }

    LSection sectionGlobalCompute(int x, int y, int z) {
        final long sectionIndex = sectionIndexGlobal(x, y, z);
        return loadedSections.computeIfAbsent(sectionIndex, LSection::new);
    }

    public LazyWorld(Instance instance, List<Automata.RuleConfig> ruleConfigs) {
        this.instance = instance;
        this.sectionCount = instance.getCachedDimensionType().height() / 16;
        this.minY = instance.getCachedDimensionType().minY();
        // Extract rules from configs
        List<Automata.CellRule> extractedRules = ruleConfigs.stream()
                .map(Automata.RuleConfig::rule)
                .collect(Collectors.toList());

        this.ruleConfigs = new ArrayList<>(ruleConfigs);
        this.rules = extractedRules;

        initRules(extractedRules, ordered(extractedRules));
    }

    private static List<Automata.CellRule.State> ordered(List<Automata.CellRule> rules) {
        Set<Automata.CellRule.State> states = new HashSet<>();
        for (Automata.CellRule rule : rules) states.addAll(rule.states());
        return new ArrayList<>(states);
    }

    void initRules(List<Automata.CellRule> rules, List<Automata.CellRule.State> orderedStates) {
        this.orderedStates = orderedStates;
        // Initialize state layout
        this.stateLayout = new StateLayout(orderedStates);

        // Initialize variables id
        Map<Automata.CellRule.State, Integer> mapping = new HashMap<>();
        mapping.put(Automata.CellRule.BLOCK_STATE, 0);
        for (int i = 0; i < orderedStates.size(); i++) {
            final Automata.CellRule.State state = orderedStates.get(i);
            mapping.put(state, i + 1); // index 0 is reserved for block state
        }
        for (Automata.CellRule rule : rules) rule.init(mapping);
        this.rules = rules;
        this.rulesMapping = mapping;
        this.ruleContexts = ruleConfigs.stream()
                .map(RuleContext::new)
                .toList();
    }

    private record BlockChange(int sectionBlockIndex, List<Automata.CellRule.Action> actions) {
    }

    private record ScheduledChange(LSection section, BlockChange change) {
    }

    private record SectionChange(LSection section, Palette palette, List<BlockChange> blockChanges) {
    }

    @Override
    public Automata.Metrics tick() {
        Automata.Metrics totalMetrics = Automata.Metrics.EMPTY;
        final int iterations = Math.max(1, lightSpeed);
        for (int i = 0; i < iterations; i++) {
            Automata.Metrics iterationMetrics = singleTick();
            totalMetrics = totalMetrics.add(iterationMetrics);
        }
        return totalMetrics;
    }

    private Automata.Metrics singleTick() {
        final List<RuleContext> rulesToTick = rulesToTick();
        if (rulesToTick.isEmpty() && wheelTimer.isEmpty()) {
            this.context.updateTick();
            return Automata.Metrics.EMPTY;
        }
        Long2ObjectMap<LongList> tickBlockChanges = new Long2ObjectOpenHashMap<>();
        Automata.Metrics metrics = Automata.Metrics.EMPTY;

        Queue<SectionChange> changes = new ArrayDeque<>();
        // Process timed changes first to ensure they're available for this tick
        computeTimedChanges(changes);
        // Process rules according to their configurations
        for (RuleContext ruleContext : rulesToTick) {
            final int tickCount = ruleContext.ruleConfig.executionsPerTick();
            for (int execution = 0; execution < tickCount; execution++) {
                Automata.Metrics ruleMetrics = computeChangesForRule(changes, ruleContext);
                metrics = metrics.add(ruleMetrics);
            }
            ruleContext.trackedSections.clear();
        }

        applyChanges(changes, tickBlockChanges);
        sendPackets(tickBlockChanges);
        this.context.updateTick();
        return metrics;
    }

    private List<RuleContext> rulesToTick() {
        final long tick = this.context.tick();
        return ruleContexts.stream()
                .filter(context -> !context.trackedSections.isEmpty() && tick % context.ruleConfig.tickInterval() == 0)
                .toList();
    }

    private Automata.Metrics computeChangesForRule(Queue<SectionChange> changes, RuleContext ruleContext) {
        Set<LSection> trackedSections = ruleContext.trackedSections;
        final Automata.RuleConfig ruleConfig = ruleContext.ruleConfig;
        final int processedSections = trackedSections.size();
        int processedBlocks = 0;
        int modifiedBlocks = 0;

        Automata.CellRule rule = ruleConfig.rule();
        final int ruleIndex = rules.indexOf(rule);
        if (ruleIndex == -1) return Automata.Metrics.EMPTY; // Rule not found

        for (LSection section : trackedSections) {
            final long sectionIndex = section.index;
            final int sectionX = sectionIndexGetX(sectionIndex);
            final int sectionY = sectionIndexGetY(sectionIndex);
            final int sectionZ = sectionIndexGetZ(sectionIndex);
            Palette palette = paletteAtSection(sectionX, sectionY, sectionZ);
            BitSet trackedBlocks = section.trackedBlocks[ruleIndex];
            if (trackedBlocks == null || palette == null) continue;

            // Process the rule for tracked blocks
            BitSet processedBlocksCopy = (BitSet) trackedBlocks.clone(); // Clone to avoid modification issues
            trackedBlocks.clear(); // Clear for next tick

            List<BlockChange> blockChanges = new ArrayList<>();
            for (int blockIndex = processedBlocksCopy.nextSetBit(0);
                 blockIndex >= 0;
                 blockIndex = processedBlocksCopy.nextSetBit(blockIndex + 1)) {
                processedBlocks++;
                final int x = sectionBlockIndexGetX(blockIndex) + sectionX * 16;
                final int y = sectionBlockIndexGetY(blockIndex) + sectionY * 16;
                final int z = sectionBlockIndexGetZ(blockIndex) + sectionZ * 16;
                context.updateLocal(section, palette, x, y, z);
                final List<Automata.CellRule.Action> actions = rule.process(context, query);
                if (actions != null) {
                    modifiedBlocks++;
                    blockChanges.add(new BlockChange(blockIndex, actions));
                }
            }
            if (!blockChanges.isEmpty()) {
                changes.offer(new SectionChange(section, palette, blockChanges));
            }
        }

        return new Automata.Metrics(processedSections, processedBlocks, modifiedBlocks);
    }

    private void computeTimedChanges(Queue<SectionChange> changes) {
        wheelTimer.tick(scheduledChange -> {
            if (scheduledChange == null) return;
            final BlockChange blockChange = scheduledChange.change;
            LSection section = scheduledChange.section;
            // Find section in changes
            final long sectionIndex = section.index;
            SectionChange sectionChange = changes.stream()
                    .filter(change -> change.section().index == sectionIndex)
                    .findFirst()
                    .orElse(null);
            if (sectionChange == null) {
                final int sectionX = sectionIndexGetX(sectionIndex);
                final int sectionY = sectionIndexGetY(sectionIndex);
                final int sectionZ = sectionIndexGetZ(sectionIndex);
                sectionChange = new SectionChange(section, paletteAtSection(sectionX, sectionY, sectionZ), new ArrayList<>());
                changes.offer(sectionChange);
            }
            sectionChange.blockChanges.add(blockChange);
        });
    }

    private void applyChanges(Queue<SectionChange> changes, Long2ObjectMap<LongList> tickBlockChanges) {
        while (!changes.isEmpty()) {
            final SectionChange sectionChange = changes.poll();
            applySectionChanges(sectionChange, tickBlockChanges);
        }
    }

    private void applySectionChanges(SectionChange sectionChange, Long2ObjectMap<LongList> tickBlockChanges) {
        final LSection section = sectionChange.section();
        Palette palette = sectionChange.palette();
        LongList blockChanges = tickBlockChanges.computeIfAbsent(section.index, _ -> new LongArrayList());
        for (BlockChange currentChange : sectionChange.blockChanges()) {
            for (Automata.CellRule.Action action : currentChange.actions()) {
                processSectionAction(section, palette, currentChange.sectionBlockIndex(), action, blockChanges);
            }
        }
        trackSection(section);
    }

    void trackSection(LSection section) {
        for (RuleContext ruleContext : ruleContexts) ruleContext.trackedSections.add(section);
    }

    private void sendPackets(Long2ObjectMap<LongList> tickBlockChanges) {
        for (Long2ObjectMap.Entry<LongList> entry : tickBlockChanges.long2ObjectEntrySet()) {
            final long sectionIndex = entry.getLongKey();
            final LongList blockChanges = entry.getValue();
            if (blockChanges.isEmpty()) continue;
            final int sectionX = sectionIndexGetX(sectionIndex);
            final int sectionY = sectionIndexGetY(sectionIndex);
            final int sectionZ = sectionIndexGetZ(sectionIndex);
            final Chunk chunk = instance.getChunk(sectionX, sectionZ);
            if (chunk != null) {
                chunk.invalidate();
                final long[] blocksArray = blockChanges.toLongArray();
                chunk.sendPacketToViewers(new MultiBlockChangePacket(sectionX, sectionY, sectionZ, blocksArray));
            }
        }
    }

    void processSectionAction(LSection section, Palette palette, int sectionBlockIndex, Automata.CellRule.Action action, LongList blockChanges) {
        if (action.scheduleTick() > 0) {
            wheelTimer.schedule(() -> new ScheduledChange(section, new BlockChange(sectionBlockIndex, List.of(action.immediate()))), action.scheduleTick());
            return;
        }
        final int localX = sectionBlockIndexGetX(sectionBlockIndex);
        final int localY = sectionBlockIndexGetY(sectionBlockIndex);
        final int localZ = sectionBlockIndexGetZ(sectionBlockIndex);
        final int globalX = sectionIndexGetX(section.index) * 16 + localX;
        final int globalY = sectionIndexGetY(section.index) * 16 + localY;
        final int globalZ = sectionIndexGetZ(section.index) * 16 + localZ;
        if (!actionPredicate(section, palette, globalX, globalY, globalZ, action)) return;
        // Clear states
        if (action.clear()) {
            for (int i = 0; i < orderedStates.size(); i++) {
                section.setState(localX, localY, localZ, i, 0);
            }
            if (palette != null) palette.set(localX, localY, localZ, 0);
            blockChanges.add(encodeSectionBlockChange(sectionBlockIndex, 0));
        }
        // Set states
        final Map<String, String> updatedProperties = action.updatedProperties();
        if (updatedProperties != null && palette != null) {
            Block block = Block.fromStateId(palette.get(localX, localY, localZ));
            assert block != null;
            try {
                block = block.withProperties(updatedProperties);
                final int value = block.stateId();
                palette.set(localX, localY, localZ, value);
                blockChanges.add(encodeSectionBlockChange(sectionBlockIndex, value));
            } catch (IllegalArgumentException e) {
                // Ignore invalid properties
            }
        }
        final Int2LongMap updatedStates = action.updatedStates();
        if (updatedStates != null) {
            for (Int2LongMap.Entry changeEntry : updatedStates.int2LongEntrySet()) {
                final int stateIndex = changeEntry.getIntKey();
                final long value = changeEntry.getLongValue();
                if (stateIndex == 0) {
                    if (palette != null) palette.set(localX, localY, localZ, (int) value);
                    blockChanges.add(encodeSectionBlockChange(sectionBlockIndex, value));
                } else {
                    section.setState(localX, localY, localZ, stateIndex - 1, value);
                }
            }
        }
        // Register the point for the next tick
        register(globalX, globalY, globalZ, section, action.wakePoints());
    }

    boolean actionPredicate(LSection section, Palette palette, int x, int y, int z, Automata.CellRule.Action action) {
        final Int2LongMap conditionStates = action.conditionStates();
        if (conditionStates == null || conditionStates.isEmpty()) return true;
        context.updateLocal(section, palette, x, y, z);
        final long[] currentStates = query.queryIndexes();
        for (Int2LongMap.Entry entry : conditionStates.int2LongEntrySet()) {
            final int stateIndex = entry.getIntKey();
            final long value = entry.getLongValue();
            if (currentStates[stateIndex] != value) return false;
        }
        return true;
    }

    @Override
    public void handlePlacement(int x, int y, int z, Map<Automata.CellRule.State, Long> properties) {
        clearAt(x, y, z);
        updateAt(x, y, z, properties);
    }

    @Override
    public void handleChunkLoad(int chunkX, int chunkZ) {
        final Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) return;
        final int globalX = chunk.getChunkX() * 16;
        final int globalZ = chunk.getChunkZ() * 16;
        final int startSectionY = minY / 16;
        for (int i = startSectionY; i < sectionCount + startSectionY; i++) {
            final int globalY = i * 16;
            final Section section = chunk.getSection(i);
            LSection startSection = sectionGlobalCompute(globalX, globalY, globalZ);
            registerSection(startSection, section);
        }
    }

    private LSection sectionCache(LSection startSection, LSection sectionCache, int x, int y, int z) {
        final long sectionIndex = sectionIndexGlobal(x, y, z);
        if (startSection.index == sectionIndex) return startSection;
        if (sectionCache == null || sectionCache.index != sectionIndex) {
            sectionCache = sectionGlobalCompute(x, y, z);
            trackSection(sectionCache);
        }
        return sectionCache;
    }

    private void register(int x, int y, int z, LSection startSection, List<Point> wakePoints) {
        if (startSection == null) {
            startSection = sectionGlobalCompute(x, y, z);
            trackSection(startSection);
        }
        LSection sectionCache = null;
        final boolean boundary = globalSectionBoundary(x, y, z);
        for (Point point : wakePoints) {
            final int nX = x + point.blockX();
            final int nY = y + point.blockY();
            final int nZ = z + point.blockZ();
            LSection section = startSection;
            if (boundary) section = sectionCache = sectionCache(startSection, sectionCache, nX, nY, nZ);
            final int localX = globalToSectionRelative(nX);
            final int localY = globalToSectionRelative(nY);
            final int localZ = globalToSectionRelative(nZ);
            final int blockIndex = sectionBlockIndex(localX, localY, localZ);
            for (int i = 0; i < rules.size(); i++) {
                BitSet trackedBlocks = section.trackedBlocks[i];
                if (trackedBlocks == null)
                    trackedBlocks = section.trackedBlocks[i] = new BitSet((int) BLOCKS_PER_SECTION);
                trackedBlocks.set(blockIndex);
            }
        }
    }

    private void registerSection(LSection startSection, Section section) {
        if (section == null) return;
        final int sectionX = sectionIndexGetX(startSection.index),
                sectionY = sectionIndexGetY(startSection.index),
                sectionZ = sectionIndexGetZ(startSection.index);
        section.blockPalette().getAllPresent((x, y, z, value) -> {
            final int blockX = sectionX * 16 + x;
            final int blockY = sectionY * 16 + y;
            final int blockZ = sectionZ * 16 + z;
            for (RuleContext rule : ruleContexts) {
                if (value > 0 && !rule.interested(0, value)) return;
                register(blockX, blockY, blockZ, startSection, Neighbors.MOORE_3D_SELF);
            }
        });
        trackSection(startSection);
    }

    @Override
    public void handleChunkUnload(int chunkX, int chunkZ) {
        for (int sectionY = 0; sectionY < sectionCount; sectionY++) {
            final long sectionIndex = sectionIndex(chunkX, sectionY, chunkZ);
            this.loadedSections.remove(sectionIndex);
        }
    }

    @Override
    public void clearAt(int x, int y, int z) {
        final LSection section = sectionGlobalCompute(x, y, z);
        final int localX = globalToSectionRelative(x);
        final int localY = globalToSectionRelative(y);
        final int localZ = globalToSectionRelative(z);
        for (int i = 0; i < orderedStates.size(); i++) {
            section.setState(localX, localY, localZ, i, 0);
        }
        //if (palette != null) palette.set(localX, localY, localZ, 0);
        trackSection(section);
        register(x, y, z, section, Neighbors.MOORE_3D_SELF);
    }

    @Override
    public void updateAt(int x, int y, int z, Map<Automata.CellRule.State, Long> properties) {
        final LSection section = sectionGlobalCompute(x, y, z);
        final int localX = globalToSectionRelative(x);
        final int localY = globalToSectionRelative(y);
        final int localZ = globalToSectionRelative(z);
        for (Automata.CellRule.State state : orderedStates) {
            final int index = rulesMapping.get(state);
            if (index == 0) continue; // Skip block state
            final long value = properties.getOrDefault(state, 0L);
            section.setState(localX, localY, localZ, index - 1, value);
        }
        trackSection(section);
        register(x, y, z, section, Neighbors.MOORE_3D_SELF);
    }

    @Override
    public Instance instance() {
        return instance;
    }

    @Override
    public List<Automata.CellRule> rules() {
        return rules;
    }

    @Override
    public List<Automata.CellRule.State> states() {
        return orderedStates;
    }

    @Override
    public Automata.Query query() {
        return query;
    }

    @Override
    public void updateRules(List<Automata.RuleConfig> newRuleConfigs) {
        List<Automata.CellRule> newRules = newRuleConfigs.stream()
                .map(Automata.RuleConfig::rule)
                .toList();

        final List<Automata.CellRule.State> orderedStates = ordered(newRules);
        // Map old state indices to new ones
        final Int2IntMap oldToNewIndex = indexMap(orderedStates);

        // Update section state buffers
        final StateLayout newStateLayout = new StateLayout(orderedStates);
        for (LSection section : new ArrayList<>(loadedSections.values())) {
            section.stateSegments = migrateSection(section, oldToNewIndex, newStateLayout);
            section.trackedBlocks = new BitSet[newRules.size()];
        }

        // Initialize new rules
        initRules(newRules, orderedStates);
        this.ruleConfigs = newRuleConfigs;
        this.rules = newRules;

        // Reschedule all changes with updated actions
        Map<Integer, List<ScheduledChange>> scheduledChanges = wheelTimer.drainAll();
        for (Map.Entry<Integer, List<ScheduledChange>> entry : scheduledChanges.entrySet()) {
            final int remainingTicks = entry.getKey();
            for (ScheduledChange change : entry.getValue()) {
                BlockChange oldChange = change.change();
                List<Automata.CellRule.Action> newActions = new ArrayList<>();
                for (Automata.CellRule.Action action : oldChange.actions()) {
                    Int2LongMap updatedStates = remapStateIndices(action.updatedStates(), oldToNewIndex);
                    Int2LongMap conditionStates = remapStateIndices(action.conditionStates(), oldToNewIndex);
                    final Automata.CellRule.Action newAction = new Automata.CellRule.Action(
                            action.updatedProperties(),
                            updatedStates,
                            action.clear(),
                            action.wakePoints(),
                            conditionStates,
                            action.scheduleTick()
                    );
                    newActions.add(newAction);
                }
                BlockChange newBlockChange = new BlockChange(oldChange.sectionBlockIndex(), newActions);
                wheelTimer.schedule(() -> new ScheduledChange(change.section(), newBlockChange), remainingTicks);
            }
        }
        for (RuleContext ruleContext : ruleContexts) ruleContext.trackedSections.clear();
        for (LSection section : loadedSections.values()) {
            final int sectionX = sectionIndexGetX(section.index);
            final int sectionY = sectionIndexGetY(section.index);
            final int sectionZ = sectionIndexGetZ(section.index);
            registerSection(section, sectionAt(sectionX, sectionY, sectionZ));
        }
    }

    private Int2IntMap indexMap(List<Automata.CellRule.State> newStates) {
        // Create mapping from old index to new index
        Int2IntMap oldToNewIndex = new Int2IntOpenHashMap();
        oldToNewIndex.defaultReturnValue(-1);
        for (int i = 0; i < newStates.size(); i++) {
            final Automata.CellRule.State state = newStates.get(i);
            final Integer oldIndex = rulesMapping.get(state);
            if (oldIndex != null) oldToNewIndex.put(oldIndex - 1, i);
        }
        return oldToNewIndex;
    }

    private Int2LongMap remapStateIndices(Int2LongMap original, Int2IntMap indexMapping) {
        if (original == null) return null;
        Int2LongMap remapped = new Int2LongOpenHashMap();
        for (Int2LongMap.Entry entry : original.int2LongEntrySet()) {
            final int oldIndex = entry.getIntKey();
            final int newIndex = indexMapping.get(oldIndex);
            // Only include states that exist in the new rules
            if (newIndex != -1) {
                remapped.put(newIndex, entry.getLongValue());
            }
        }
        return remapped;
    }

    private MemorySegment[] migrateSection(LSection section, Int2IntMap indexMapping, StateLayout newLayout) {
        MemorySegment[] newStateSegments = new MemorySegment[newLayout.segmentSizes.length];
        // Reuse existing segments for states that exist in both old and new rules
        for (Int2IntMap.Entry entry : indexMapping.int2IntEntrySet()) {
            final int oldIndex = entry.getIntKey();
            final int newIndex = entry.getIntValue();
            // Only copy non-null segments
            if (section.stateSegments[oldIndex] != null) {
                newStateSegments[newIndex] = section.stateSegments[oldIndex];
            }
        }
        // New states remain null - they will be allocated when first accessed
        return newStateSegments;
    }

    @Override
    public int lightSpeed() {
        return lightSpeed;
    }

    @Override
    public void updateLightSpeed(int lightSpeed) {
        this.lightSpeed = lightSpeed;
    }
}
