package net.goldenstack.minestom_ca.backends.lazy;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.goldenstack.minestom_ca.AutomataQuery;
import net.goldenstack.minestom_ca.AutomataWorld;
import net.goldenstack.minestom_ca.CellRule;
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
import static net.minestom.server.coordinate.CoordConversion.globalToChunk;
import static net.minestom.server.coordinate.CoordConversion.globalToSectionRelative;

@SuppressWarnings("UnstableApiUsage")
public final class LazyWorld implements AutomataWorld {
    private static final int LIGHT_SPEED = 1;
    private final Instance instance;
    private final CellRule rules;
    private final QueryImpl query = new QueryImpl();
    private final int sectionCount;
    private final int minY;

    private final HashedWheelTimer<ScheduledChange> wheelTimer = new HashedWheelTimer<>(255);
    private final Long2ObjectMap<LSection> loadedSections = new Long2ObjectOpenHashMap<>();
    private final Set<LSection> trackedSections = Collections.newSetFromMap(new IdentityHashMap<>());

    private final class LSection {
        private static final long BLOCKS_PER_SECTION = 16 * 16 * 16;
        private final long index;
        private final MemorySegment states;
        // Block indexes to track next tick
        private final BitSet trackedBlocks = new BitSet((int) BLOCKS_PER_SECTION);

        LSection(final long index) {
            this.index = index;
            final long singleBlockSize = (long) rules.states().size() * Long.BYTES;
            final long totalSize = BLOCKS_PER_SECTION * singleBlockSize;
            this.states = Arena.ofAuto().allocate(totalSize);
        }

        long getState(int x, int y, int z, int stateIndex) {
            final long index = index(x, y, z, stateIndex);
            return states.get(ValueLayout.JAVA_LONG, index);
        }

        void setState(int x, int y, int z, int stateIndex, long value) {
            final long index = index(x, y, z, stateIndex);
            states.set(ValueLayout.JAVA_LONG, index, value);
        }

        long index(int x, int y, int z, int stateIndex) {
            if (x < 0 || x >= 16 || y < 0 || y >= 16 || z < 0 || z >= 16) {
                throw new IndexOutOfBoundsException("Coordinates out of bounds for section: " + x + ", " + y + ", " + z);
            }
            final int blockIndex = (y * 16 + z) * 16 + x;
            return ((long) blockIndex * rules.states().size() + stateIndex) * Long.BYTES;
        }
    }

    Palette paletteAtSection(int sectionX, int sectionY, int sectionZ) {
        final Chunk chunk = instance.getChunk(sectionX, sectionZ);
        if (chunk == null) return null;
        final Section section = chunk.getSection(sectionY);
        return section.blockPalette();
    }

    private final class QueryImpl implements AutomataQuery {
        Palette palette;
        int localX, localY, localZ;
        int localChunkX, localChunkY, localChunkZ;

        void updateLocal(Palette palette, int x, int y, int z) {
            this.palette = palette;
            this.localX = x;
            this.localY = y;
            this.localZ = z;
            this.localChunkX = globalToChunk(x);
            this.localChunkY = globalToChunk(y);
            this.localChunkZ = globalToChunk(z);
            if (this.palette == null) {
                this.palette = paletteAtSection(localChunkX, localChunkY, localChunkZ);
            }
        }

        int queryBlockState(int x, int y, int z) {
            if (globalToChunk(x) != localChunkX ||
                    globalToChunk(y) != localChunkY ||
                    globalToChunk(z) != localChunkZ) {
                return globalBlockState(x, y, z);
            }
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
            final LSection section = sectionGlobal(x, y, z);
            if (section == null) return 0;
            final int localX = globalToSectionRelative(x);
            final int localY = globalToSectionRelative(y);
            final int localZ = globalToSectionRelative(z);
            return section.getState(localX, localY, localZ, index - 1);
        }

        @Override
        public Int2LongMap queryIndexes(int x, int y, int z) {
            x += localX;
            y += localY;
            z += localZ;
            final LSection section = sectionGlobal(x, y, z);
            final int blockState = queryBlockState(x, y, z);
            if (section == null) return CellRule.stateMap(0, blockState);
            final int localX = globalToSectionRelative(x);
            final int localY = globalToSectionRelative(y);
            final int localZ = globalToSectionRelative(z);
            Int2LongMap indexes = new Int2LongOpenHashMap();
            indexes.put(0, blockState);
            for (int i = 0; i < rules.states().size(); i++) {
                final long value = section.getState(localX, localY, localZ, i);
                indexes.put(i + 1, value);
            }
            return indexes;
        }

        public Map<String, Long> queryNames(int x, int y, int z) {
            x += localX;
            y += localY;
            z += localZ;
            Map<Integer, String> variables = new HashMap<>();
            List<CellRule.State> states = rules().states();
            for (int i = 0; i < states.size(); i++) {
                final CellRule.State state = states.get(i);
                final String name = state.name();
                variables.put(i + 1, name);
            }
            final Int2LongMap indexes = queryIndexes(x, y, z);
            Map<String, Long> names = new HashMap<>();
            for (Int2LongMap.Entry entry : indexes.int2LongEntrySet()) {
                final int key = entry.getIntKey();
                final long value = entry.getLongValue();
                final String name = variables.get(key);
                if (name != null) names.put(name, value);
                else names.put("var" + key, value);
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

    public LazyWorld(Instance instance, CellRule rules) {
        this.instance = instance;
        this.rules = rules;
        this.sectionCount = instance.getCachedDimensionType().height() / 16;
        this.minY = instance.getCachedDimensionType().minY();
    }

    @Override
    public void tick() {
        for (int i = 0; i < LIGHT_SPEED; i++) {
            singleTick();
        }
    }

    private record BlockChange(int sectionBlockIndex, CellRule.Action action) {
    }

    private record ScheduledChange(LSection section, BlockChange change) {
    }

    private record SectionChange(LSection section, Palette palette, List<BlockChange> blockChanges) {
    }

    private void singleTick() {
        Queue<SectionChange> changes = new ArrayDeque<>();
        computeChanges(changes);
        computeTimedChanges(changes);
        applyChanges(changes);
    }

    private void computeChanges(Queue<SectionChange> changes) {
        for (LSection section : trackedSections) {
            List<BlockChange> blockChanges = new ArrayList<>();
            final long sectionIndex = section.index;
            final int sectionX = sectionIndexGetX(sectionIndex);
            final int sectionY = sectionIndexGetY(sectionIndex);
            final int sectionZ = sectionIndexGetZ(sectionIndex);
            Palette palette = paletteAtSection(sectionX, sectionY, sectionZ);
            BitSet trackedBlocks = section.trackedBlocks;
            for (int blockIndex = trackedBlocks.nextSetBit(0);
                 blockIndex >= 0;
                 blockIndex = trackedBlocks.nextSetBit(blockIndex + 1)) {
                final int x = sectionBlockIndexGetX(blockIndex) + sectionX * 16;
                final int y = sectionBlockIndexGetY(blockIndex) + sectionY * 16;
                final int z = sectionBlockIndexGetZ(blockIndex) + sectionZ * 16;
                query.updateLocal(palette, x, y, z);
                final CellRule.Action action = rules.process(query);
                if (action != null) blockChanges.add(new BlockChange(blockIndex, action));
            }
            if (!blockChanges.isEmpty()) {
                changes.offer(new SectionChange(section, palette, blockChanges));
            }
            trackedBlocks.clear();
        }
        trackedSections.clear();
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
            processSectionAction(section, palette, currentChange, blockChanges);
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

    void processSectionAction(LSection section, Palette palette, BlockChange change, LongList blockChanges) {
        final int sectionBlockIndex = change.sectionBlockIndex();
        final CellRule.Action action = change.action();
        if (action.scheduleTick() > 0) {
            wheelTimer.schedule(() -> new ScheduledChange(section, new BlockChange(sectionBlockIndex, action.immediate())), action.scheduleTick());
            return;
        }
        final int localX = sectionBlockIndexGetX(sectionBlockIndex);
        final int localY = sectionBlockIndexGetY(sectionBlockIndex);
        final int localZ = sectionBlockIndexGetZ(sectionBlockIndex);
        final int globalX = sectionIndexGetX(section.index) * 16 + localX;
        final int globalY = sectionIndexGetY(section.index) * 16 + localY;
        final int globalZ = sectionIndexGetZ(section.index) * 16 + localZ;
        if (!actionPredicate(globalX, globalY, globalZ, action)) return;
        // Set states
        for (Int2LongMap.Entry changeEntry : action.updatedStates().int2LongEntrySet()) {
            final int stateIndex = changeEntry.getIntKey();
            final long value = changeEntry.getLongValue();
            if (stateIndex == 0) {
                if (palette != null) palette.set(localX, localY, localZ, (int) value);
                // Encode block change for packet
                final long blockState = value << 12;
                final long pos = ((long) localX << 8 | (long) localZ << 4 | localY);
                blockChanges.add(blockState | pos);
            } else {
                section.setState(localX, localY, localZ, stateIndex - 1, value);
            }
        }
        // Register the point for the next tick
        register(globalX, globalY, globalZ, section, action.wakePoints());
    }

    boolean actionPredicate(int x, int y, int z, CellRule.Action action) {
        final Int2LongMap conditionStates = action.conditionStates();
        if (conditionStates == null || conditionStates.isEmpty()) return true;
        query.updateLocal(null, x, y, z);
        final Int2LongMap currentStates = query.queryIndexes(0, 0, 0);
        for (Int2LongMap.Entry entry : conditionStates.int2LongEntrySet()) {
            final int stateIndex = entry.getIntKey();
            final long value = entry.getLongValue();
            if (!Objects.equals(currentStates.get(stateIndex), value)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void handlePlacement(int x, int y, int z, Int2LongMap properties) {
        LSection section = sectionGlobalCompute(x, y, z);
        assert section != null;
        final int localX = globalToSectionRelative(x);
        final int localY = globalToSectionRelative(y);
        final int localZ = globalToSectionRelative(z);
        for (int i = 0; i < rules.states().size(); i++) {
            final long value = properties.getOrDefault(i + 1, 0);
            section.setState(localX, localY, localZ, i, value);
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
                if (!rules.tracked(value)) return;
                final int blockX = globalX + x;
                final int blockY = globalY + y;
                final int blockZ = globalZ + z;
                register(blockX, blockY, blockZ, startSection, Neighbors.MOORE_3D_SELF);
            });
        }
    }

    private void register(int x, int y, int z, LSection startSection, List<Point> wakePoints) {
        if (startSection == null) {
            startSection = sectionGlobalCompute(x, y, z);
            trackedSections.add(startSection);
        }
        final boolean boundary = globalSectionBoundary(x, y, z);
        for (Point point : wakePoints) {
            final int nX = x + point.blockX();
            final int nY = y + point.blockY();
            final int nZ = z + point.blockZ();
            LSection section;
            if (!boundary) {
                section = startSection;
            } else {
                section = sectionGlobalCompute(nX, nY, nZ);
                trackedSections.add(section);
            }
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
    public CellRule rules() {
        return rules;
    }

    @Override
    public AutomataQuery query() {
        return query;
    }
}
