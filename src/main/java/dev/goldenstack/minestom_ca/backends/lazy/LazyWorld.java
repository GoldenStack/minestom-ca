package dev.goldenstack.minestom_ca.backends.lazy;

import dev.goldenstack.minestom_ca.AutomataWorld;
import dev.goldenstack.minestom_ca.Neighbors;
import dev.goldenstack.minestom_ca.Rule;
import dev.goldenstack.minestom_ca.RuleAnalysis;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.chunk.ChunkUtils;

import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public final class LazyWorld implements AutomataWorld {
    private final Instance instance;
    private final List<Rule> rules;
    private final int minY;
    private final boolean[] trackedStates = new boolean[Short.MAX_VALUE];

    private final Map<Long, LChunk> loadedChunks = new HashMap<>();

    final class LChunk {
        // Block indexes to track next tick
        private final Set<Integer> trackedBlocks = new HashSet<>();
    }

    public LazyWorld(Instance instance, List<Rule> rules) {
        this.instance = instance;
        this.rules = rules;
        this.minY = instance.getDimensionType().getMinY();

        for (Rule rule : rules) {
            RuleAnalysis.queryExpression(rule.condition(), Rule.Expression.Literal.class, literal -> {
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
        Map<Point, Block> changes = new HashMap<>();
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
                final Block updated = executeRules(x, y, z);
                if (updated != null) {
                    changes.put(new Vec(x, y, z), updated);
                }
            }
        }
        // Apply changes
        for (Map.Entry<Point, Block> entry : changes.entrySet()) {
            final Point point = entry.getKey();
            final Block block = entry.getValue();
            try {
                this.instance.setBlock(point, block);
            } catch (IllegalStateException ignored) {
            }
        }
        // Prepare for next tick
        for (LChunk lchunk : loadedChunks.values()) lchunk.trackedBlocks.clear();
        for (Map.Entry<Point, Block> entry : changes.entrySet()) {
            final Point point = entry.getKey();
            final int blockX = point.blockX();
            final int blockY = point.blockY();
            final int blockZ = point.blockZ();
            register(blockX, blockY, blockZ);
        }
    }

    private Block executeRules(int x, int y, int z) {
        Block block = null;
        for (Rule rule : rules) {
            final boolean condition = verifyCondition(x, y, z, rule.condition());
            if (condition) {
                block = runResult(x, y, z, rule.result());
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
            case Rule.Condition.Or or -> {
                for (Rule.Condition c : or.conditions()) {
                    if (verifyCondition(x, y, z, c)) yield true;
                }
                yield false;
            }
        };
    }

    private Block runResult(int x, int y, int z, Rule.Result result) {
        return switch (result) {
            case Rule.Result.Set set -> {
                // TODO: internal states
                final Rule.Expression blockStateExpression = set.expressions().get(0);
                final int state = expression(x, y, z, blockStateExpression);
                yield Block.fromStateId((short) state);
            }
        };
    }

    private int expression(int x, int y, int z, Rule.Expression expression) {
        return switch (expression) {
            case Rule.Expression.Index index -> {
                final int blockX = x + index.x();
                final int blockY = y + index.y();
                final int blockZ = z + index.z();
                // TODO: internal states
                try {
                    yield instance.getBlock(blockX, blockY, blockZ).stateId();
                } catch (NullPointerException e) {
                    yield 0;
                }
            }
            case Rule.Expression.Literal literal -> literal.value();
            case Rule.Expression.NeighborsCount neighborsCount -> {
                int count = 0;
                for (Point offset : neighborsCount.offsets()) {
                    final int nX = x + offset.blockX();
                    final int nY = y + offset.blockY();
                    final int nZ = z + offset.blockZ();
                    final boolean valid = verifyCondition(nX, nY, nZ, neighborsCount.condition());
                    if (valid) count++;
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
    public void handlePlacement(Point point, Block block) {
        if (!trackedStates[block.stateId()]) return;
        // Add neighbors
        final int blockX = point.blockX();
        final int blockY = point.blockY();
        final int blockZ = point.blockZ();
        register(blockX, blockY, blockZ);
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
}
