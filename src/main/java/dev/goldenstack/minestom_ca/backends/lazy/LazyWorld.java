package dev.goldenstack.minestom_ca.backends.lazy;

import dev.goldenstack.minestom_ca.*;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;
import net.minestom.server.utils.chunk.ChunkUtils;

import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public final class LazyWorld implements AutomataWorld {
    private final Instance instance;
    private final Program program;
    private final List<Rule> rules;
    private final int minY;

    private final int stateCount;
    private final boolean[] trackedStates = new boolean[Short.MAX_VALUE];

    private final Map<Long, LChunk> loadedChunks = new HashMap<>();

    final class LChunk {
        // Block indexes to track next tick
        private final Set<Integer> trackedBlocks = new HashSet<>();
        private final Palette[] states = new Palette[stateCount - 1];

        {
            Arrays.setAll(states, i -> Palette.blocks());
        }
    }

    public LazyWorld(Instance instance, Program program) {
        this.instance = instance;
        this.program = program;
        this.rules = program.rules();
        this.minY = instance.getDimensionType().getMinY();

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
        Map<Point, Map<Integer, Integer>> changes = new HashMap<>();
        // Retrieve changes
        for (var entry : loadedChunks.entrySet()) {
            final long chunkIndex = entry.getKey();
            final LChunk lChunk = entry.getValue();
            final int chunkX = ChunkUtils.getChunkCoordX(chunkIndex);
            final int chunkZ = ChunkUtils.getChunkCoordZ(chunkIndex);
            for (int blockIndex : lChunk.trackedBlocks) {
                final int localX = ChunkUtils.blockIndexToChunkPositionX(blockIndex);
                final int localY = ChunkUtils.blockIndexToChunkPositionY(blockIndex);
                final int localZ = ChunkUtils.blockIndexToChunkPositionZ(blockIndex);
                final int x = localX + chunkX * 16;
                final int y = localY;
                final int z = localZ + chunkZ * 16;
                final Map<Integer, Integer> updated = executeRules(x, y, z);
                if (updated != null) changes.put(new Vec(x, y, z), updated);
            }
        }
        // Apply changes
        for (Map.Entry<Point, Map<Integer, Integer>> entry : changes.entrySet()) {
            final Point point = entry.getKey();
            final Map<Integer, Integer> blockChange = entry.getValue();
            for (Map.Entry<Integer, Integer> changeEntry : blockChange.entrySet()) {
                final int stateIndex = changeEntry.getKey();
                final int value = changeEntry.getValue();
                if (stateIndex == 0) {
                    try {
                        final Block block = Block.fromStateId((short) value);
                        assert block != null;
                        this.instance.setBlock(point, block);
                    } catch (IllegalStateException ignored) {
                    }
                } else {
                    final LChunk lChunk = loadedChunks.get(ChunkUtils.getChunkIndex(
                            ChunkUtils.getChunkCoordinate(point.blockX()),
                            ChunkUtils.getChunkCoordinate(point.blockZ())));
                    if (lChunk == null) continue;
                    final int localX = ChunkUtils.toSectionRelativeCoordinate(point.blockX());
                    final int localY = ChunkUtils.toSectionRelativeCoordinate(point.blockY());
                    final int localZ = ChunkUtils.toSectionRelativeCoordinate(point.blockZ());
                    Palette palette = lChunk.states[stateIndex - 1];
                    palette.set(localX, localY, localZ, value);
                }
            }
        }
        // Prepare for next tick
        for (LChunk lchunk : loadedChunks.values()) lchunk.trackedBlocks.clear();
        for (Point point : changes.keySet()) {
            final int blockX = point.blockX();
            final int blockY = point.blockY();
            final int blockZ = point.blockZ();
            register(blockX, blockY, blockZ);
        }
    }

    private Map<Integer, Integer> executeRules(int x, int y, int z) {
        Map<Integer, Integer> block = new HashMap<>();
        for (Rule rule : rules) {
            if (!verifyCondition(x, y, z, rule.condition())) continue;
            for (Rule.Result result : rule.results()) {
                switch (result) {
                    case Rule.Result.SetIndex set -> {
                        final int index = set.stateIndex();
                        final int value = expression(x, y, z, set.expression());
                        block.put(index, value);
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
        if (block.isEmpty()) return null;
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
                    try {
                        yield instance.getBlock(x, y, z).stateId();
                    } catch (NullPointerException e) {
                        yield 0;
                    }
                } else {
                    final LChunk lChunk = loadedChunks.get(ChunkUtils.getChunkIndex(
                            ChunkUtils.getChunkCoordinate(x),
                            ChunkUtils.getChunkCoordinate(z)));
                    if (lChunk == null) yield 0;
                    final int localX = ChunkUtils.toSectionRelativeCoordinate(x);
                    final int localY = ChunkUtils.toSectionRelativeCoordinate(y);
                    final int localZ = ChunkUtils.toSectionRelativeCoordinate(z);
                    final Palette palette = lChunk.states[stateIndex - 1];
                    yield palette.get(localX, localY, localZ);
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
        LChunk lChunk = loadedChunks.get(ChunkUtils.getChunkIndex(
                ChunkUtils.getChunkCoordinate(x),
                ChunkUtils.getChunkCoordinate(z)));
        final int localX = ChunkUtils.toSectionRelativeCoordinate(x);
        final int localZ = ChunkUtils.toSectionRelativeCoordinate(z);
        for(int i = 1; i < stateCount; i++) {
            Palette palette = lChunk.states[i - 1];
            final int value = properties.getOrDefault(i, 0);
            palette.set(localX, y, localZ, value);
        }
        register(x, y, z);
    }

    @Override
    public void handleChunkLoad(int chunkX, int chunkZ) {
        final Chunk chunk = instance.getChunk(chunkX, chunkZ);
        assert chunk != null;
        int sectionY = 0;
        final int localX = chunk.getChunkX() * 16;
        final int localZ = chunk.getChunkZ() * 16;
        for (Section section : chunk.getSections()) {
            final int localY = sectionY++ * 16 - minY;
            section.blockPalette().getAllPresent((x, y, z, value) -> {
                if (!trackedStates[value]) return;
                final int blockX = localX + x;
                final int blockY = localY + y;
                final int blockZ = localZ + z;
                register(blockX, blockY, blockZ);
            });
        }
    }

    private void register(int x, int y, int z) {
        for (Point offset : Neighbors.MOORE_3D_SELF) {
            final int nX = x + offset.blockX();
            final int nY = y + offset.blockY();
            final int nZ = z + offset.blockZ();

            final int chunkX = ChunkUtils.getChunkCoordinate(nX);
            final int chunkZ = ChunkUtils.getChunkCoordinate(nZ);
            final long chunkIndex = ChunkUtils.getChunkIndex(chunkX, chunkZ);
            LChunk lChunk = this.loadedChunks.computeIfAbsent(chunkIndex,
                    k -> new LChunk());

            final int localX = ChunkUtils.toSectionRelativeCoordinate(nX);
            final int localZ = ChunkUtils.toSectionRelativeCoordinate(nZ);
            final int blockIndex = ChunkUtils.getBlockIndex(localX, nY, localZ);
            lChunk.trackedBlocks.add(blockIndex);
        }
    }

    @Override
    public void handleChunkUnload(int chunkX, int chunkZ) {
        final long chunkIndex = ChunkUtils.getChunkIndex(chunkX, chunkZ);
        this.loadedChunks.remove(chunkIndex);
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
