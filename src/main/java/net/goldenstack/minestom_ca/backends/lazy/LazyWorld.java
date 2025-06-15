package net.goldenstack.minestom_ca.backends.lazy;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.goldenstack.minestom_ca.Automata;
import net.goldenstack.minestom_ca.Neighbors;
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

import static net.goldenstack.minestom_ca.CoordConversionPro.*;
import static net.minestom.server.coordinate.CoordConversion.globalToSectionRelative;

@SuppressWarnings("UnstableApiUsage")
public final class LazyWorld implements Automata.World {
    private static final int LIGHT_SPEED = 1;
    private final Instance instance;
    private final QueryImpl query = new QueryImpl();
    private final int sectionCount;
    private final int minY;

    private Automata.CellRule rules;
    private List<Automata.CellRule.State> orderedStates;
    private Map<Automata.CellRule.State, Integer> rulesMapping;
    private StateLayout stateLayout;

    private final HashedWheelTimer<ScheduledChange> wheelTimer = new HashedWheelTimer<>(255);
    private final Long2ObjectMap<LSection> loadedSections = new Long2ObjectOpenHashMap<>();
    private final Set<LSection> trackedSections = Collections.newSetFromMap(new IdentityHashMap<>());

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
                final long requiredLongs = (LSection.BLOCKS_PER_SECTION + valuesPerLong - 1) / valuesPerLong;
                this.segmentSizes[i] = requiredLongs * Long.BYTES;
            }
        }
    }

    private final class LSection {
        private static final long BLOCKS_PER_SECTION = 16 * 16 * 16;
        private final long index;
        private MemorySegment[] stateSegments; // One segment per state
        // Block indexes to track next tick
        private final BitSet trackedBlocks = new BitSet((int) BLOCKS_PER_SECTION);

        LSection(final long index) {
            this.index = index;
            this.stateSegments = new MemorySegment[stateLayout.segmentSizes.length];
            for (int i = 0; i < stateLayout.segmentSizes.length; i++) {
                this.stateSegments[i] = Arena.ofAuto().allocate(stateLayout.segmentSizes[i]);
            }
        }

        long getState(int x, int y, int z, int stateIndex) {
            final int blockIndex = sectionBlockIndex(x, y, z);
            final int bitSize = stateLayout.stateBitSizes[stateIndex];
            final int valuesPerLong = stateLayout.bitsPerLong[stateIndex];
            final long mask = stateLayout.stateMasks[stateIndex];

            final int longIndex = blockIndex / valuesPerLong;
            final int bitOffset = (blockIndex % valuesPerLong) * bitSize;
            final long offset = (long) longIndex * Long.BYTES;

            final long packed = stateSegments[stateIndex].get(ValueLayout.JAVA_LONG, offset);
            return (packed >>> bitOffset) & mask;
        }

        void setState(int x, int y, int z, int stateIndex, long value) {
            final int blockIndex = sectionBlockIndex(x, y, z);
            final int bitSize = stateLayout.stateBitSizes[stateIndex];
            final int valuesPerLong = stateLayout.bitsPerLong[stateIndex];
            final long mask = stateLayout.stateMasks[stateIndex];

            final int longIndex = blockIndex / valuesPerLong;
            final int bitOffset = (blockIndex % valuesPerLong) * bitSize;
            final long offset = (long) longIndex * Long.BYTES;

            final long packed = stateSegments[stateIndex].get(ValueLayout.JAVA_LONG, offset);
            final long cleared = packed & ~(mask << bitOffset);
            final long updated = cleared | ((value & mask) << bitOffset);
            stateSegments[stateIndex].set(ValueLayout.JAVA_LONG, offset, updated);
        }

        boolean anyState(int x, int y, int z) {
            final int blockIndex = sectionBlockIndex(x, y, z);
            for (int stateIndex = 0; stateIndex < stateSegments.length; stateIndex++) {
                if (getStateByBlockIndex(blockIndex, stateIndex) != 0) return true;
            }
            return false;
        }

        private long getStateByBlockIndex(int blockIndex, int stateIndex) {
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

    Palette paletteAtSection(int sectionX, int sectionY, int sectionZ) {
        final Chunk chunk = instance.getChunk(sectionX, sectionZ);
        if (chunk == null) return null;
        final Section section = chunk.getSection(sectionY);
        return section.blockPalette();
    }

    private final class QueryImpl implements Automata.Query {
        LSection section;
        Palette palette;
        int localX, localY, localZ;
        // Local cache
        long[] localStates;

        void updateLocal(LSection section, Palette palette, int x, int y, int z) {
            this.section = section;
            this.palette = palette;
            this.localX = x;
            this.localY = y;
            this.localZ = z;
            this.localStates = null;
        }

        @Override
        public int stateIndex(Automata.CellRule.State state) {
            final int index = rulesMapping.getOrDefault(state, -1);
            if (index >= 0) return index;
            throw new IllegalArgumentException("Unknown state: " + state);
        }

        @Override
        public long state(int index) {
            if (this.localStates != null) return localStates[index];
            final int localX = globalToSectionRelative(this.localX);
            final int localY = globalToSectionRelative(this.localY);
            final int localZ = globalToSectionRelative(this.localZ);
            if (index == 0) return palette.get(localX, localY, localZ);
            return section.getState(localX, localY, localZ, index - 1);
        }

        @Override
        public long[] queryIndexes() {
            if (this.localStates != null) return localStates;
            final int localX = globalToSectionRelative(this.localX);
            final int localY = globalToSectionRelative(this.localY);
            final int localZ = globalToSectionRelative(this.localZ);
            final int blockState = palette.get(localX, localY, localZ);
            final List<Automata.CellRule.State> states = orderedStates;
            long[] indexes = new long[states.size() + 1];
            indexes[0] = blockState;
            for (int i = 0; i < states.size(); i++) {
                final long value = section.getState(localX, localY, localZ, i);
                indexes[i + 1] = value;
            }
            this.localStates = indexes;
            return indexes;
        }

        boolean sameSection(int x, int y, int z) {
            return section != null && sectionIndexGlobal(x, y, z) == section.index;
        }

        LSection querySection(int x, int y, int z) {
            return sameSection(x, y, z) ? this.section : sectionGlobal(x, y, z);
        }

        int queryBlockState(int x, int y, int z) {
            if (!sameSection(x, y, z)) return globalBlockState(x, y, z);
            if (palette == null) return 0;
            final int localX = globalToSectionRelative(x);
            final int localY = globalToSectionRelative(y);
            final int localZ = globalToSectionRelative(z);
            return palette.get(localX, localY, localZ);
        }

        @Override
        public long stateAt(int x, int y, int z, int index) {
            x += localX;
            y += localY;
            z += localZ;
            if (index == 0) return queryBlockState(x, y, z);
            final LSection section = querySection(x, y, z);
            if (section == null) return 0;
            final int localX = globalToSectionRelative(x);
            final int localY = globalToSectionRelative(y);
            final int localZ = globalToSectionRelative(z);
            return section.getState(localX, localY, localZ, index - 1);
        }

        static final long[] EMPTY_INDEXES = new long[]{0};

        @Override
        public long[] queryIndexes(int x, int y, int z) {
            x += localX;
            y += localY;
            z += localZ;
            final LSection section = querySection(x, y, z);
            if (section == null) return EMPTY_INDEXES;
            final int localX = globalToSectionRelative(x);
            final int localY = globalToSectionRelative(y);
            final int localZ = globalToSectionRelative(z);
            final List<Automata.CellRule.State> states = orderedStates;
            long[] indexes = new long[states.size() + 1];
            indexes[0] = queryBlockState(x, y, z);
            for (int i = 0; i < states.size(); i++) {
                final long value = section.getState(localX, localY, localZ, i);
                indexes[i + 1] = value;
            }
            return indexes;
        }

        public Map<String, Long> queryNames(int x, int y, int z) {
            x += localX;
            y += localY;
            z += localZ;
            Map<Integer, String> variables = new HashMap<>();
            for (Map.Entry<Automata.CellRule.State, Integer> entry : rulesMapping.entrySet()) {
                final Automata.CellRule.State state = entry.getKey();
                final int index = entry.getValue();
                variables.put(index, state.name());
            }
            final long[] indexes = queryIndexes(x, y, z);
            if (indexes.length == 1) return Map.of(Automata.CellRule.BLOCK_STATE.name(), indexes[0]);
            Map<String, Long> names = new HashMap<>();
            for (int i = 0; i < variables.size(); i++) {
                final long value = indexes[i];
                final String name = variables.get(i);
                names.put(name, value);
            }
            return Map.copyOf(names);
        }
    }

    LSection sectionGlobal(int x, int y, int z) {
        final long sectionIndex = sectionIndexGlobal(x, y, z);
        return loadedSections.get(sectionIndex);
    }

    LSection sectionGlobalCompute(int x, int y, int z) {
        final long sectionIndex = sectionIndexGlobal(x, y, z);
        return loadedSections.computeIfAbsent(sectionIndex, LSection::new);
    }

    public LazyWorld(Instance instance, Automata.CellRule rules) {
        this.instance = instance;
        this.sectionCount = instance.getCachedDimensionType().height() / 16;
        this.minY = instance.getCachedDimensionType().minY();
        initRules(rules, new ArrayList<>(rules.states()));
    }

    void initRules(Automata.CellRule rules, List<Automata.CellRule.State> orderedStates) {
        this.orderedStates = orderedStates;
        // Initialize state layout
        this.stateLayout = new StateLayout(orderedStates);

        // Initialize variables id
        Map<Automata.CellRule.State, Integer> mapping = new HashMap<>();
        for (int i = 0; i < orderedStates.size(); i++) {
            final Automata.CellRule.State state = orderedStates.get(i);
            mapping.put(state, i + 1); // index 0 is reserved for block state
        }
        rules.init(mapping);
        this.rules = rules;
        this.rulesMapping = mapping;
    }

    @Override
    public Automata.Metrics tick() {
        if (trackedSections.isEmpty() && wheelTimer.isEmpty()) return Automata.Metrics.EMPTY;
        Automata.Metrics metrics = Automata.Metrics.EMPTY;
        for (int i = 0; i < LIGHT_SPEED; i++) {
            final Automata.Metrics tickMetrics = singleTick();
            metrics = metrics.add(tickMetrics);
        }
        return metrics;
    }

    private record BlockChange(int sectionBlockIndex, List<Automata.CellRule.Action> actions) {
    }

    private record ScheduledChange(LSection section, BlockChange change) {
    }

    private record SectionChange(LSection section, Palette palette, List<BlockChange> blockChanges) {
    }

    private Automata.Metrics singleTick() {
        Queue<SectionChange> changes = new ArrayDeque<>();
        final Automata.Metrics metrics = computeChanges(changes);
        computeTimedChanges(changes);
        applyChanges(changes);
        return metrics;
    }

    private Automata.Metrics computeChanges(Queue<SectionChange> changes) {
        final int processedSections = trackedSections.size();
        int processedBlocks = 0;
        int modifiedBlocks = 0;
        for (LSection section : trackedSections) {
            List<BlockChange> blockChanges = new ArrayList<>();
            final long sectionIndex = section.index;
            final int sectionX = sectionIndexGetX(sectionIndex);
            final int sectionY = sectionIndexGetY(sectionIndex);
            final int sectionZ = sectionIndexGetZ(sectionIndex);
            BitSet trackedBlocks = section.trackedBlocks;
            Palette palette = paletteAtSection(sectionX, sectionY, sectionZ);
            if (palette == null) {
                trackedBlocks.clear();
                continue;
            }
            for (int blockIndex = trackedBlocks.nextSetBit(0);
                 blockIndex >= 0;
                 blockIndex = trackedBlocks.nextSetBit(blockIndex + 1)) {
                processedBlocks++;
                final int x = sectionBlockIndexGetX(blockIndex) + sectionX * 16;
                final int y = sectionBlockIndexGetY(blockIndex) + sectionY * 16;
                final int z = sectionBlockIndexGetZ(blockIndex) + sectionZ * 16;
                query.updateLocal(section, palette, x, y, z);
                final List<Automata.CellRule.Action> actions = rules.process(query);
                if (actions != null) {
                    modifiedBlocks++;
                    blockChanges.add(new BlockChange(blockIndex, actions));
                }
            }
            if (!blockChanges.isEmpty()) {
                changes.offer(new SectionChange(section, palette, blockChanges));
            }
            trackedBlocks.clear();
        }
        trackedSections.clear();
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

    private void applyChanges(Queue<SectionChange> changes) {
        while (!changes.isEmpty()) {
            final SectionChange sectionChange = changes.poll();
            applySectionChanges(sectionChange);
        }
    }

    private void applySectionChanges(SectionChange sectionChange) {
        final LSection section = sectionChange.section();
        Palette palette = sectionChange.palette();
        LongList blockChanges = new LongArrayList();
        for (BlockChange currentChange : sectionChange.blockChanges()) {
            for (Automata.CellRule.Action action : currentChange.actions()) {
                processSectionAction(section, palette, currentChange.sectionBlockIndex(), action, blockChanges);
            }
        }
        trackedSections.add(section);
        if (!blockChanges.isEmpty()) {
            final long sectionIndex = section.index;
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
            for (int i = 0; i < rules.states().size(); i++) {
                section.setState(localX, localY, localZ, i, 0);
            }
            if (palette != null) palette.set(localX, localY, localZ, 0);
            blockChanges.add(encodeSectionBlockChange(localX, localY, localZ, 0));
        }
        // Set states
        final Int2LongMap updatedStates = action.updatedStates();
        if (updatedStates != null) {
            for (Int2LongMap.Entry changeEntry : updatedStates.int2LongEntrySet()) {
                final int stateIndex = changeEntry.getIntKey();
                final long value = changeEntry.getLongValue();
                if (stateIndex == 0) {
                    if (palette != null) palette.set(localX, localY, localZ, (int) value);
                    blockChanges.add(encodeSectionBlockChange(localX, localY, localZ, value));
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
        query.updateLocal(section, palette, x, y, z);
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
        LSection section = sectionGlobalCompute(x, y, z);
        assert section != null;
        final int localX = globalToSectionRelative(x);
        final int localY = globalToSectionRelative(y);
        final int localZ = globalToSectionRelative(z);
        for (Automata.CellRule.State state : rules.states()) {
            final int index = rulesMapping.get(state);
            if (index == 0) continue;
            final long value = properties.getOrDefault(state, 0L);
            section.setState(localX, localY, localZ, index - 1, value);
        }
        trackedSections.add(section);
        register(x, y, z, section, Neighbors.MOORE_3D_SELF);
    }

    @Override
    public void handleChunkLoad(int chunkX, int chunkZ) {
        final Chunk chunk = instance.getChunk(chunkX, chunkZ);
        assert chunk != null;
        final int globalX = chunk.getChunkX() * 16;
        final int globalZ = chunk.getChunkZ() * 16;
        final int startSectionY = minY / 16;
        for (int i = startSectionY; i < sectionCount + startSectionY; i++) {
            final int globalY = i * 16;
            final Section section = chunk.getSection(i);
            LSection startSection = sectionGlobalCompute(globalX, globalY, globalZ);
            trackedSections.add(startSection);
            section.blockPalette().getAllPresent((x, y, z, value) -> {
                if (value > 0 && !rules.tracked(Block.fromStateId(value))) return;
                final int blockX = globalX + x;
                final int blockY = globalY + y;
                final int blockZ = globalZ + z;
                register(blockX, blockY, blockZ, startSection, Neighbors.MOORE_3D_SELF);
            });
        }
    }

    private LSection sectionCache(LSection startSection, LSection sectionCache, int x, int y, int z) {
        final long sectionIndex = sectionIndexGlobal(x, y, z);
        if (startSection.index == sectionIndex) return startSection;
        if (sectionCache == null || sectionCache.index != sectionIndex) {
            sectionCache = sectionGlobalCompute(x, y, z);
            trackedSections.add(sectionCache);
        }
        return sectionCache;
    }

    private void register(int x, int y, int z, LSection startSection, List<Point> wakePoints) {
        if (startSection == null) {
            startSection = sectionGlobalCompute(x, y, z);
            trackedSections.add(startSection);
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
            section.trackedBlocks.set(blockIndex);
        }
    }

    @Override
    public void handleChunkUnload(int chunkX, int chunkZ) {
        for (int sectionY = 0; sectionY < sectionCount; sectionY++) {
            final long sectionIndex = sectionIndex(chunkX, sectionY, chunkZ);
            this.loadedSections.remove(sectionIndex);
        }
    }

    private int globalBlockState(int x, int y, int z) {
        final Chunk chunk = instance.getChunkAt(x, z);
        if (chunk == null) return 0;
        final Block block = chunk.getBlock(x, y, z, Block.Getter.Condition.TYPE);
        assert block != null;
        return block.stateId();
    }

    @Override
    public Instance instance() {
        return instance;
    }

    @Override
    public Automata.CellRule rules() {
        return rules;
    }

    @Override
    public Automata.Query query() {
        return query;
    }

    @Override
    public void updateRules(Automata.CellRule newRules) {
        final List<Automata.CellRule.State> orderedStates = new ArrayList<>(newRules.states());
        // Map old state indices to new ones
        final Int2IntMap oldToNewIndex = indexMap(orderedStates);

        // Update section state buffers
        final StateLayout newStateLayout = new StateLayout(orderedStates);
        for (LSection section : new ArrayList<>(loadedSections.values())) {
            section.stateSegments = migrateSection(section, oldToNewIndex, newStateLayout);
            section.trackedBlocks.clear();
        }

        // Initialize new rules
        initRules(newRules, orderedStates);

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
        trackedSections.clear();
        for (LSection section : loadedSections.values()) {
            final int sectionX = sectionIndexGetX(section.index);
            final int sectionY = sectionIndexGetY(section.index);
            final int sectionZ = sectionIndexGetZ(section.index);
            final Palette palette = paletteAtSection(sectionX, sectionY, sectionZ);
            if (palette == null) continue;
            if (palette.count() == 0 && emptySegments(section.stateSegments)) continue;
            palette.getAll((x, y, z, value) -> {
                final boolean tracked = value > 0 && newRules.tracked(Block.fromStateId(value));
                if (tracked || section.anyState(x, y, z)) {
                    final int globalX = sectionX * 16 + x;
                    final int globalY = sectionY * 16 + y;
                    final int globalZ = sectionZ * 16 + z;
                    register(globalX, globalY, globalZ, section, Neighbors.MOORE_3D_SELF);
                }
            });
            if (!section.trackedBlocks.isEmpty()) {
                trackedSections.add(section);
            }
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
            newStateSegments[newIndex] = section.stateSegments[oldIndex];
        }
        // Initialize new segments for states that didn't exist in the old rules
        for (int i = 0; i < newLayout.segmentSizes.length; i++) {
            if (newStateSegments[i] == null) {
                newStateSegments[i] = Arena.ofAuto().allocate(newLayout.segmentSizes[i]);
            }
        }
        return newStateSegments;
    }

    private boolean emptySegments(MemorySegment[] segments) {
        for (MemorySegment segment : segments) {
            for (long i = 0; i < segment.byteSize(); i += Long.BYTES) {
                if (segment.get(ValueLayout.JAVA_LONG, i) != 0) return false;
            }
        }
        return true;
    }
}
