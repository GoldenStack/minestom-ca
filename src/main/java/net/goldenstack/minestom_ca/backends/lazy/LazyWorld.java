package net.goldenstack.minestom_ca.backends.lazy;

import net.goldenstack.minestom_ca.AutomataQuery;
import net.goldenstack.minestom_ca.AutomataWorld;
import net.goldenstack.minestom_ca.CellRule;
import net.goldenstack.minestom_ca.Neighbors;
import net.minestom.server.coordinate.CoordConversion;
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

@SuppressWarnings("UnstableApiUsage")
public final class LazyWorld implements AutomataWorld {
    private static final int LIGHT_SPEED = 1;
    private final Instance instance;
    private final CellRule rules;
    private final AutomataQuery query = new QueryImpl();
    private final int sectionCount;
    private final int minY;

    private final Map<Long, LSection> loadedSections = new HashMap<>();
    private final Queue<LSection> trackedSections = new ArrayDeque<>();

    private static int floorDiv(int x, int y) {
        int r = x / y;
        // if the signs are different and modulo not zero, round down
        if ((x ^ y) < 0 && (x % y != 0)) {
            r--;
        }
        return r;
    }

    public static long packSectionIndex(int sectionX, int sectionY, int sectionZ) {
        // Use 21 bits for each, with sign extension
        long x = sectionX & 0x1FFFFF;
        long y = sectionY & 0x1FFFFF;
        long z = sectionZ & 0x1FFFFF;
        return (x << 42) | (y << 21) | z;
    }

    public static int unpackSectionX(long packed) {
        int x = (int) (packed >> 42) & 0x1FFFFF;
        // Sign extension for 21 bits
        if ((x & 0x100000) != 0) x |= ~0x1FFFFF;
        return x;
    }

    public static int unpackSectionY(long packed) {
        int y = (int) (packed >> 21) & 0x1FFFFF;
        if ((y & 0x100000) != 0) y |= ~0x1FFFFF;
        return y;
    }

    public static int unpackSectionZ(long packed) {
        int z = (int) packed & 0x1FFFFF;
        if ((z & 0x100000) != 0) z |= ~0x1FFFFF;
        return z;
    }

    long sectionIndexGlobal(int x, int y, int z) {
        final int sectionX = floorDiv(x, 16);
        final int sectionY = floorDiv(y, 16);
        final int sectionZ = floorDiv(z, 16);
        return packSectionIndex(sectionX, sectionY, sectionZ);
    }

    private final class LSection {
        private static final long BLOCKS_PER_SECTION = 16 * 16 * 16;
        private final long index;
        private final MemorySegment states;
        // Block indexes to track next tick
        private final Set<Integer> trackedBlocks = new HashSet<>();

        LSection(final long index) {
            this.index = index;
            final long singleBlockSize = (long) rules.states().size() * Long.BYTES;
            final long totalSize = BLOCKS_PER_SECTION * singleBlockSize;
            this.states = Arena.ofAuto().allocate(totalSize);
        }

        long getState(int x, int y, int z, int stateIndex) {
            final int blockIndex = index(x, y, z);
            final long offset = ((long) blockIndex * rules.states().size() + (stateIndex - 1)) * Long.BYTES;
            return states.get(ValueLayout.JAVA_LONG, offset);
        }

        void setState(int x, int y, int z, int stateIndex, long value) {
            final int blockIndex = index(x, y, z);
            final long offset = ((long) blockIndex * rules.states().size() + (stateIndex - 1)) * Long.BYTES;
            states.set(ValueLayout.JAVA_LONG, offset, value);
        }

        int index(int x, int y, int z) {
            assertLocal(x, y, z);
            return (y * 16 + z) * 16 + x;
        }

        void assertLocal(int x, int y, int z) {
            if (x < 0 || x >= 16 || y < 0 || y >= 16 || z < 0 || z >= 16) {
                throw new IndexOutOfBoundsException("Coordinates out of bounds for section: " + x + ", " + y + ", " + z);
            }
        }
    }

    private final class QueryImpl implements AutomataQuery {
        @Override
        public long stateAt(int x, int y, int z, int index) {
            if (index == 0) return blockState(x, y, z);
            final LSection section = sectionGlobal(x, y, z);
            if (section == null) return 0;
            final int localX = CoordConversion.globalToSectionRelative(x);
            final int localY = CoordConversion.globalToSectionRelative(y);
            final int localZ = CoordConversion.globalToSectionRelative(z);
            return section.getState(localX, localY, localZ, index);
        }

        @Override
        public Map<Integer, Long> queryIndexes(int x, int y, int z) {
            final LSection section = sectionGlobal(x, y, z);
            if (section == null) return Map.of(0, (long) blockState(x, y, z));
            final int localX = CoordConversion.globalToSectionRelative(x);
            final int localY = CoordConversion.globalToSectionRelative(y);
            final int localZ = CoordConversion.globalToSectionRelative(z);
            Map<Integer, Long> indexes = new HashMap<>();
            indexes.put(0, (long) blockState(x, y, z));
            for (int i = 0; i < rules.states().size(); i++) {
                final long value = section.getState(localX, localY, localZ, i);
                indexes.put(i + 1, value);
            }
            return Map.copyOf(indexes);
        }

        public Map<String, Long> queryNames(int x, int y, int z) {
            Map<Integer, String> variables = new HashMap<>();
            List<CellRule.State> states = rules().states();
            for (int i = 0; i < states.size(); i++) {
                final CellRule.State state = states.get(i);
                final String name = state.name();
                variables.put(i + 1, name);
            }
            final Map<Integer, Long> indexes = queryIndexes(x, y, z);
            Map<String, Long> names = new HashMap<>();
            for (Map.Entry<Integer, Long> entry : indexes.entrySet()) {
                final String name = variables.get(entry.getKey());
                if (name != null) names.put(name, entry.getValue());
                else names.put("var" + entry.getKey(), entry.getValue());
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

        // Register loaded chunks
        System.out.println("Registering loaded chunks...");
        for (Chunk c : instance.getChunks()) {
            handleChunkLoad(c.getChunkX(), c.getChunkZ());
        }
        System.out.println("Loaded chunks registered");
    }

    @Override
    public void tick() {
        for (int i = 0; i < LIGHT_SPEED; i++) {
            singleTick();
        }
    }

    private record BlockChange(int x, int y, int z, Map<Integer, Long> blockData) {
    }

    private record SectionChange(long index, Palette palette, List<BlockChange> blockChanges) {
    }

    private void singleTick() {
        Queue<SectionChange> changes = new ArrayDeque<>();
        computeChanges(changes);
        applyChanges(changes);
    }

    private void computeChanges(Queue<SectionChange> changes) {
        for (LSection section : trackedSections) {
            List<BlockChange> blockChanges = new ArrayList<>();
            final long sectionIndex = section.index;
            final int sectionX = unpackSectionX(sectionIndex);
            final int sectionY = unpackSectionY(sectionIndex);
            final int sectionZ = unpackSectionZ(sectionIndex);
            Palette palette = palette(sectionX, sectionY, sectionZ);
            for (int blockIndex : section.trackedBlocks) {
                final int localX = CoordConversion.chunkBlockIndexGetX(blockIndex);
                final int localY = CoordConversion.chunkBlockIndexGetY(blockIndex);
                final int localZ = CoordConversion.chunkBlockIndexGetZ(blockIndex);
                final int x = localX + sectionX * 16;
                final int y = localY;
                final int z = localZ + sectionZ * 16;
                final Map<Integer, Long> updatedStates = rules.process(x, y, z, query);
                if (updatedStates != null) blockChanges.add(new BlockChange(x, y, z, updatedStates));
            }
            if (!blockChanges.isEmpty()) {
                changes.offer(new SectionChange(sectionIndex, palette, blockChanges));
            }
            section.trackedBlocks.clear();
        }
        trackedSections.clear();
    }

    private void applyChanges(Queue<SectionChange> changes) {
        while (!changes.isEmpty()) {
            final SectionChange sectionChange = changes.poll();
            applySectionChanges(sectionChange);
        }
    }

    Palette palette(int sectionX, int sectionY, int sectionZ) {
        final Chunk chunk = instance.getChunk(sectionX, sectionZ);
        if (chunk == null) return null;
        final Section section = chunk.getSection(sectionY);
        return section.blockPalette();
    }

    private void applySectionChanges(SectionChange sectionChange) {
        final long sectionIndex = sectionChange.index;
        final LSection section = loadedSections.get(sectionIndex);
        Palette palette = sectionChange.palette();
        List<Long> blockChanges = new ArrayList<>();
        for (BlockChange currentChange : sectionChange.blockChanges()) {
            final int x = currentChange.x();
            final int y = currentChange.y();
            final int z = currentChange.z();

            final int localX = CoordConversion.globalToSectionRelative(x);
            final int localY = CoordConversion.globalToSectionRelative(y);
            final int localZ = CoordConversion.globalToSectionRelative(z);
            // Set states
            for (Map.Entry<Integer, Long> changeEntry : currentChange.blockData().entrySet()) {
                final int stateIndex = changeEntry.getKey();
                final long value = changeEntry.getValue();
                if (stateIndex == 0) {
                    if (palette != null) palette.set(localX, localY, localZ, (int) value);
                    // Encode block change for packet
                    final long blockState = value << 12;
                    final long pos = ((long) localX << 8 | (long) localZ << 4 | localY);
                    blockChanges.add(blockState | pos);
                } else {
                    if (section == null) continue;
                    section.setState(localX, localY, localZ, stateIndex, value);
                }
            }
            // Register the point for the next tick
            register(x, y, z, section);
        }
        trackedSections.offer(section);
        if (!blockChanges.isEmpty()) {
            final int sectionX = unpackSectionX(sectionIndex);
            final int sectionY = unpackSectionY(sectionIndex);
            final int sectionZ = unpackSectionZ(sectionIndex);
            final Chunk chunk = instance.getChunk(sectionX, sectionZ);
            if (chunk != null) {
                chunk.invalidate();
                final long[] blocksArray = blockChanges.stream().mapToLong(Long::longValue).toArray();
                chunk.sendPacketToViewers(new MultiBlockChangePacket(sectionX, sectionY, sectionZ, blocksArray));
            }
        }
    }

    @Override
    public void handlePlacement(int x, int y, int z, Map<Integer, Integer> properties) {
        LSection section = sectionGlobalCompute(x, y, z);
        assert section != null;
        final int localX = CoordConversion.globalToSectionRelative(x);
        final int localY = CoordConversion.globalToSectionRelative(y);
        final int localZ = CoordConversion.globalToSectionRelative(z);
        for (int i = 0; i < rules.states().size(); i++) {
            final int value = properties.getOrDefault(i, 0);
            section.setState(localX, localY, localZ, i, value);
        }
        register(x, y, z, null);
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
            trackedSections.offer(startSection);
            section.blockPalette().getAllPresent((x, y, z, value) -> {
                if (!rules.tracked(value)) return;
                final int blockX = globalX + x;
                final int blockY = globalY + y;
                final int blockZ = globalZ + z;
                register(blockX, blockY, blockZ, startSection);
            });
        }
    }

    private void register(int x, int y, int z, LSection startSection) {
        for (Point offset : Neighbors.MOORE_3D_SELF) {
            final int nX = x + offset.blockX();
            final int nY = y + offset.blockY();
            final int nZ = z + offset.blockZ();
            LSection section = sectionGlobalCompute(nX, nY, nZ);
            if (startSection == null || startSection.index != section.index) {
                trackedSections.offer(section);
            }
            final int localX = CoordConversion.globalToSectionRelative(nX);
            final int localZ = CoordConversion.globalToSectionRelative(nZ);
            final int blockIndex = CoordConversion.chunkBlockIndex(localX, nY, localZ);
            section.trackedBlocks.add(blockIndex);
        }
    }

    @Override
    public void handleChunkUnload(int chunkX, int chunkZ) {
        for (int sectionY = 0; sectionY < sectionCount; sectionY++) {
            final long sectionIndex = packSectionIndex(chunkX, sectionY, chunkZ);
            this.loadedSections.remove(sectionIndex);
        }
    }

    private int blockState(int x, int y, int z) {
        try {
            final Block block = instance.getBlock(x, y, z, Block.Getter.Condition.TYPE);
            assert block != null;
            return block.stateId();
        } catch (NullPointerException e) {
            return 0;
        }
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
