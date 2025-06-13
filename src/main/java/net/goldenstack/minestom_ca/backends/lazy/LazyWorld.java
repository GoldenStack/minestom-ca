package net.goldenstack.minestom_ca.backends.lazy;

import net.goldenstack.minestom_ca.*;
import net.goldenstack.minestom_ca.lang.Program;
import net.goldenstack.minestom_ca.lang.Rule;
import net.goldenstack.minestom_ca.lang.RuleAnalysis;
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
    private final Program program;
    private final List<Rule> rules;
    private final int sectionCount;
    private final int minY;

    private final int stateCount;
    private final boolean[] trackedStates = new boolean[Short.MAX_VALUE];

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
            final long singleBlockSize = (long) stateCount * Integer.BYTES;
            final long totalSize = BLOCKS_PER_SECTION * singleBlockSize;
            this.states = Arena.ofAuto().allocate(totalSize);
        }

        int getState(int x, int y, int z, int stateIndex) {
            final int blockIndex = index(x, y, z);
            final long offset = ((long) blockIndex * stateCount + (stateIndex - 1)) * Integer.BYTES;
            return states.get(ValueLayout.JAVA_INT, offset);
        }

        void setState(int x, int y, int z, int stateIndex, int value) {
            final int blockIndex = index(x, y, z);
            final long offset = ((long) blockIndex * stateCount + (stateIndex - 1)) * Integer.BYTES;
            states.set(ValueLayout.JAVA_INT, offset, value);
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

    LSection sectionGlobal(int x, int y, int z) {
        final long sectionIndex = sectionIndexGlobal(x, y, z);
        return loadedSections.get(sectionIndex);
    }

    LSection sectionGlobalCompute(int x, int y, int z) {
        final long sectionIndex = sectionIndexGlobal(x, y, z);
        return loadedSections.computeIfAbsent(sectionIndex, LSection::new);
    }

    public LazyWorld(Instance instance, Program program) {
        this.instance = instance;
        this.program = program;
        this.rules = program.rules();
        this.sectionCount = instance.getCachedDimensionType().height() / 16;
        this.minY = instance.getCachedDimensionType().minY();

        this.stateCount = RuleAnalysis.stateCount(rules);
        for (Rule rule : rules) {
            RuleAnalysis.queryExpression(rule.condition(), Rule.Expression.Literal.class, literal -> {
                if (literal.value() < 0 || literal.value() >= trackedStates.length) return;
                this.trackedStates[literal.value()] = true;
            });
        }

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

    private record BlockChange(int x, int y, int z, Map<Integer, Integer> blockData) {
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
                final Map<Integer, Integer> updatedStates = executeRules(x, y, z);
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
        List<Long> blockChanges = new ArrayList<>();
        for (BlockChange currentChange : sectionChange.blockChanges()) {
            final int x = currentChange.x();
            final int y = currentChange.y();
            final int z = currentChange.z();

            final int localX = CoordConversion.globalToSectionRelative(x);
            final int localY = CoordConversion.globalToSectionRelative(y);
            final int localZ = CoordConversion.globalToSectionRelative(z);
            // Set states
            for (Map.Entry<Integer, Integer> changeEntry : currentChange.blockData().entrySet()) {
                final int stateIndex = changeEntry.getKey();
                final int value = changeEntry.getValue();
                if (stateIndex == 0) {
                    sectionChange.palette().set(localX, localY, localZ, value);
                    // Encode block change for packet
                    final long blockState = ((long) value) << 12;
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

    private Map<Integer, Integer> executeRules(int x, int y, int z) {
        Map<Integer, Integer> block = null;
        for (Rule rule : rules) {
            if (!verifyCondition(x, y, z, rule.condition())) continue;
            if (block == null) block = new HashMap<>(stateCount);
            for (Rule.Result result : rule.results()) {
                switch (result) {
                    case Rule.Result.SetIndex set -> {
                        final int index = set.stateIndex();
                        final int value = expression(x, y, z, set.expression());
                        block.put(index, value);
                    }
                    case Rule.Result.BlockCopy blockCopy -> {
                        final int blockX = x + blockCopy.x();
                        final int blockY = y + blockCopy.y();
                        final int blockZ = z + blockCopy.z();
                        // Copy block state
                        block.put(0, blockState(blockX, blockY, blockZ));
                        // Copy other states
                        LSection section = sectionGlobal(blockX, blockY, blockZ);
                        if (section == null) break;
                        final int localX = CoordConversion.globalToSectionRelative(blockX);
                        final int localY = CoordConversion.globalToSectionRelative(blockY);
                        final int localZ = CoordConversion.globalToSectionRelative(blockZ);
                        for (int i = 1; i < stateCount; i++) {
                            final int value = section.getState(localX, localY, localZ, i);
                            block.put(i, value);
                        }
                    }
                    case Rule.Result.TriggerEvent triggerEvent -> {
                        final String eventName = triggerEvent.event();
                        final Rule.Expression eventExpression = triggerEvent.expression();
                        if (eventExpression != null) {
                            final int value = expression(x, y, z, eventExpression);
                            System.out.println("Event: " + eventName + "=" + value);
                        } else {
                            System.out.println("Event: " + eventName);
                        }
                    }
                }
            }
        }
        return block;
    }

    private boolean verifyCondition(int x, int y, int z, Rule.Condition condition) {
        return switch (condition) {
            case Rule.Condition.And and -> {
                for (Rule.Condition c : and.conditions()) {
                    if (!verifyCondition(x, y, z, c)) yield false;
                }
                yield true;
            }
            case Rule.Condition.Equal equal -> {
                final int first = expression(x, y, z, equal.first());
                final int second = expression(x, y, z, equal.second());
                yield first == second;
            }
            case Rule.Condition.Not not -> !verifyCondition(x, y, z, not.condition());
        };
    }

    private int expression(int x, int y, int z, Rule.Expression expression) {
        return switch (expression) {
            case Rule.Expression.Index index -> {
                final int stateIndex = index.stateIndex();
                if (stateIndex == 0) {
                    yield blockState(x, y, z);
                } else {
                    final LSection section = sectionGlobal(x, y, z);
                    if (section == null) yield 0;
                    final int localX = CoordConversion.globalToSectionRelative(x);
                    final int localY = CoordConversion.globalToSectionRelative(y);
                    final int localZ = CoordConversion.globalToSectionRelative(z);
                    yield section.getState(localX, localY, localZ, stateIndex);
                }
            }
            case Rule.Expression.NeighborIndex index -> expression(
                    x + index.x(), y + index.y(), z + index.z(),
                    new Rule.Expression.Index(index.stateIndex()));
            case Rule.Expression.Literal literal -> literal.value();
            case Rule.Expression.NeighborsCount neighborsCount -> {
                int count = 0;
                for (Point offset : neighborsCount.offsets()) {
                    final int nX = x + offset.blockX();
                    final int nY = y + offset.blockY();
                    final int nZ = z + offset.blockZ();
                    if (verifyCondition(nX, nY, nZ, neighborsCount.condition())) count++;
                }
                yield count;
            }
            case Rule.Expression.Compare compare -> {
                final int first = expression(x, y, z, compare.first());
                final int second = expression(x, y, z, compare.second());
                yield (int) Math.signum(first - second);
            }
            case Rule.Expression.Operation operation -> {
                final int first = expression(x, y, z, operation.first());
                final int second = expression(x, y, z, operation.second());
                yield switch (operation.type()) {
                    case ADD -> first + second;
                    case SUBTRACT -> first - second;
                    case MULTIPLY -> first * second;
                    case DIVIDE -> first / second;
                    case MODULO -> first % second;
                };
            }
        };
    }

    @Override
    public void handlePlacement(int x, int y, int z, Map<Integer, Integer> properties) {
        LSection section = sectionGlobalCompute(x, y, z);
        assert section != null;
        final int localX = CoordConversion.globalToSectionRelative(x);
        final int localY = CoordConversion.globalToSectionRelative(y);
        final int localZ = CoordConversion.globalToSectionRelative(z);
        for (int i = 1; i < stateCount; i++) {
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
                if (!trackedStates[value]) return;
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

    @Override
    public Map<Integer, Integer> queryIndexes(int x, int y, int z) {
        final LSection section = sectionGlobal(x, y, z);
        if (section == null) {
            return Map.of(0, blockState(x, y, z));
        }
        final int localX = CoordConversion.globalToSectionRelative(x);
        final int localY = CoordConversion.globalToSectionRelative(y);
        final int localZ = CoordConversion.globalToSectionRelative(z);
        Map<Integer, Integer> indexes = new HashMap<>();
        indexes.put(0, blockState(x, y, z));
        for (int i = 1; i < stateCount; i++) {
            final int value = section.getState(localX, localY, localZ, i);
            indexes.put(i, value);
        }
        return Map.copyOf(indexes);
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
    public Program program() {
        return program;
    }
}
