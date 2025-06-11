package net.goldenstack.minestom_ca.backends.lazy;

import net.goldenstack.minestom_ca.*;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;

import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public final class LazyWorld implements AutomataWorld {
    private final Instance instance;
    private final Program program;
    private final List<Rule> rules;
    private final int sectionCount;
    private final int minY;

    private final int stateCount;
    private final boolean[] trackedStates = new boolean[Short.MAX_VALUE];

    private final Map<Long, LChunk> loadedChunks = new HashMap<>();

    final class LChunk {
        // Block indexes to track next tick
        private final Set<Integer> trackedBlocks = new HashSet<>();
        private final LSection[] sections = new LSection[sectionCount];

        {
            Arrays.setAll(sections, i -> new LSection());
        }

        int getState(int x, int y, int z, int stateIndex) {
            final LSection section = sections[(y - minY) / 16];
            final Palette palette = section.states[stateIndex - 1];
            return palette.get(CoordConversion.globalToSectionRelative(x),
                    CoordConversion.globalToSectionRelative(y),
                    CoordConversion.globalToSectionRelative(z));
        }

        void setState(int x, int y, int z, int stateIndex, int value) {
            final LSection section = sections[(y - minY) / 16];
            final Palette palette = section.states[stateIndex - 1];
            palette.set(CoordConversion.globalToSectionRelative(x),
                    CoordConversion.globalToSectionRelative(y),
                    CoordConversion.globalToSectionRelative(z),
                    value);
        }

        private final class LSection {
            private final Palette[] states = new Palette[stateCount - 1];

            {
                Arrays.setAll(states, _ -> Palette.blocks());
            }
        }
    }

    private record BlockChange(int x, int y, int z, Map<Integer, Integer> blockData) {
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
        Queue<BlockChange> changesToProcess = new LinkedList<>();
        // Retrieve changes
        for (var entry : loadedChunks.entrySet()) {
            final long chunkIndex = entry.getKey();
            final LChunk lChunk = entry.getValue();
            final int chunkX = CoordConversion.chunkIndexGetX(chunkIndex);
            final int chunkZ = CoordConversion.chunkIndexGetZ(chunkIndex);
            for (int blockIndex : lChunk.trackedBlocks) {
                final int localX = CoordConversion.chunkBlockIndexGetX(blockIndex);
                final int localY = CoordConversion.chunkBlockIndexGetY(blockIndex);
                final int localZ = CoordConversion.chunkBlockIndexGetZ(blockIndex);
                final int x = localX + chunkX * 16;
                final int y = localY;
                final int z = localZ + chunkZ * 16;
                final Map<Integer, Integer> updatedStates = executeRules(x, y, z);
                if (updatedStates != null) changesToProcess.add(new BlockChange(x, y, z, updatedStates));
            }
            lChunk.trackedBlocks.clear();
        }

        // Apply changes and register affected blocks for the next tick
        while (!changesToProcess.isEmpty()) {
            final BlockChange currentChange = changesToProcess.poll();
            final int x = currentChange.x();
            final int y = currentChange.y();
            final int z = currentChange.z();
            final Map<Integer, Integer> blockData = currentChange.blockData();
            // Set states
            for (Map.Entry<Integer, Integer> changeEntry : blockData.entrySet()) {
                final int stateIndex = changeEntry.getKey();
                final int value = changeEntry.getValue();
                if (stateIndex == 0) {
                    try {
                        final Block block = Block.fromStateId(value);
                        assert block != null;
                        this.instance.setBlock(x, y, z, block, false);
                    } catch (IllegalStateException ignored) {
                    }
                } else {
                    final LChunk lChunk = loadedChunks.get(CoordConversion.chunkIndex(
                            CoordConversion.globalToChunk(x),
                            CoordConversion.globalToChunk(z)));
                    if (lChunk == null) continue;
                    lChunk.setState(x, y, z, stateIndex, value);
                }
            }
            // Register the point for the next tick
            register(x, y, z);
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
                    case Rule.Result.BlockCopy blockCopy -> {
                        final int blockX = x + blockCopy.x();
                        final int blockY = y + blockCopy.y();
                        final int blockZ = z + blockCopy.z();
                        // Copy block state
                        final Block targetBlock = instance.getBlock(blockX, blockY, blockZ);
                        final int stateId = targetBlock.stateId();
                        block.put(0, stateId);
                        // Copy other states
                        LChunk lChunk = loadedChunks.get(CoordConversion.chunkIndex(
                                CoordConversion.globalToChunk(blockX),
                                CoordConversion.globalToChunk(blockZ)));
                        for (int i = 1; i < stateCount; i++) {
                            final int value = lChunk.getState(blockX, blockY, blockZ, i);
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
                    final LChunk lChunk = loadedChunks.get(CoordConversion.chunkIndex(
                            CoordConversion.globalToChunk(x),
                            CoordConversion.globalToChunk(z)));
                    if (lChunk == null) yield 0;
                    yield lChunk.getState(x, y, z, stateIndex);
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
        LChunk lChunk = loadedChunks.get(CoordConversion.chunkIndex(
                CoordConversion.globalToChunk(x),
                CoordConversion.globalToChunk(z)));
        for (int i = 1; i < stateCount; i++) {
            final int value = properties.getOrDefault(i, 0);
            lChunk.setState(x, y, z, i, value);
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

            final int chunkX = CoordConversion.globalToChunk(nX);
            final int chunkZ = CoordConversion.globalToChunk(nZ);
            final long chunkIndex = CoordConversion.chunkIndex(chunkX, chunkZ);
            LChunk lChunk = this.loadedChunks.computeIfAbsent(chunkIndex,
                    _ -> new LChunk());

            final int localX = CoordConversion.globalToSectionRelative(nX);
            final int localZ = CoordConversion.globalToSectionRelative(nZ);
            final int blockIndex = CoordConversion.chunkBlockIndex(localX, nY, localZ);
            lChunk.trackedBlocks.add(blockIndex);
        }
    }

    @Override
    public void handleChunkUnload(int chunkX, int chunkZ) {
        final long chunkIndex = CoordConversion.chunkIndex(chunkX, chunkZ);
        this.loadedChunks.remove(chunkIndex);
    }

    @Override
    public Map<Integer, Integer> queryIndexes(int x, int y, int z) {
        final int chunkX = CoordConversion.globalToChunk(x);
        final int chunkZ = CoordConversion.globalToChunk(z);
        final LChunk lChunk = this.loadedChunks.get(CoordConversion.chunkIndex(chunkX, chunkZ));
        Map<Integer, Integer> indexes = new HashMap<>();
        indexes.put(0, instance.getBlock(x, y, z).stateId());
        for (int i = 1; i < stateCount; i++) {
            final int value = lChunk.getState(x, y, z, i);
            indexes.put(i, value);
        }
        return Map.copyOf(indexes);
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
