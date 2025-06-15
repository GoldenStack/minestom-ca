package net.goldenstack.minestom_ca;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.LongPredicate;

public final class Automata {
    public interface CellRule {
        State BLOCK_STATE = new State("block_state");

        void init(Map<State, Integer> mapping);

        List<Action> process(Query query);

        boolean tracked(Block block);

        List<State> states();

        record State(String name) {
        }

        record Action(
                Int2LongMap updatedStates,
                // Whether states should be cleared before applying the action
                boolean clear,
                // Relative points to wake up after the action is applied
                List<Point> wakePoints,
                // States to check before applying the action
                @Nullable Int2LongMap conditionStates,
                // Number of ticks to wait before applying the action (including interpreting the conditions)
                int scheduleTick
        ) {
            public static Action UpdateState(Int2LongMap states) {
                return new Action(states, false, Neighbors.MOORE_3D_SELF, null, 0);
            }

            // Update state after X ticks no matter what
            public static Action Schedule(int tick, Int2LongMap updatedStates) {
                return new Action(updatedStates, false, Neighbors.MOORE_3D_SELF, null, tick);
            }

            // Update state after X ticks if specified states are equal
            public static Action ConditionalSchedule(int tick, Int2LongMap conditionStates, Int2LongMap updatedStates) {
                return new Action(updatedStates, false, Neighbors.MOORE_3D_SELF, conditionStates, tick);
            }

            public Action immediate() {
                return new Action(updatedStates, clear, wakePoints, conditionStates, 0);
            }
        }

        static CellRule rules(CellRule... rules) {
            List<State> states = new ArrayList<>();
            for (CellRule rule : rules) {
                states.addAll(rule.states());
            }
            return new CellRule() {
                @Override
                public void init(Map<State, Integer> mapping) {
                    for (CellRule rule : rules) rule.init(mapping);
                }

                @Override
                public List<Action> process(Query query) {
                    List<Action> first = null;
                    List<Action> result = null;
                    for (CellRule rule : rules) {
                        List<Action> actions = rule.process(query);
                        if (actions == null || actions.isEmpty()) continue;
                        if (first == null) {
                            first = actions;
                        } else {
                            if (result == null) result = new ArrayList<>(first);
                            result.addAll(actions);
                        }
                    }
                    return result != null ? result : first;
                }

                @Override
                public boolean tracked(Block block) {
                    for (CellRule rule : rules) {
                        if (rule.tracked(block)) return true;
                    }
                    return false;
                }

                @Override
                public List<State> states() {
                    return states;
                }
            };
        }

        static Int2LongMap stateMap(int key, long value) {
            return Int2LongMaps.singleton(key, value);
        }
    }

    public interface Query {
        int stateIndex(String state);

        long state(int index);

        default long state(String state) {
            final int index = stateIndex(state);
            return state(index);
        }

        long stateAt(int x, int y, int z, int index);

        long[] queryIndexes();

        default long stateAt(int x, int y, int z, String state) {
            final int index = stateIndex(state);
            return stateAt(x, y, z, index);
        }

        default Block blockAt(int x, int y, int z) {
            final long blockState = stateAt(x, y, z, 0);
            return Block.fromStateId((int) blockState);
        }

        long[] queryIndexes(int x, int y, int z);

        Map<String, Long> queryNames(int x, int y, int z);

        default int countNeighborsState(int index, List<Point> points,
                                        LongPredicate predicate) {
            int count = 0;
            for (Point point : points) {
                final long state = stateAt(point.blockX(), point.blockY(), point.blockZ(), index);
                if (predicate.test(state)) count++;
            }
            return count;
        }

        default int countNeighborsStateLimit(int index, int limit, List<Point> points,
                                             LongPredicate predicate) {
            int count = 0;
            for (Point point : points) {
                final long state = stateAt(point.blockX(), point.blockY(), point.blockZ(), index);
                if (predicate.test(state) && ++count >= limit) break;
            }
            return count;
        }
    }

    public interface World {
        Map<UUID, World> WORLDS = new HashMap<>();

        static void register(World world) {
            final Instance instance = world.instance();
            final World prev = WORLDS.put(instance.getUuid(), world);
            if (prev != null) {
                throw new IllegalStateException("An AutomataWorld is already registered for the instance " + instance);
            }
            instance.eventNode().addChild(AutomataImpl.AUTOMATA_EVENT_NODE);
            // Register loaded chunks
            System.out.println("Registering loaded chunks...");
            for (Chunk c : instance.getChunks()) world.handleChunkLoad(c.getChunkX(), c.getChunkZ());
            System.out.println("Loaded chunks registered");
        }

        static World get(Instance instance) {
            return WORLDS.get(instance.getUuid());
        }

        /**
         * Simulates one tick of progress for the world.
         */
        Metrics tick();

        /**
         * Handles an external block change (e.g. block place or break)
         */
        void handlePlacement(int x, int y, int z, Int2LongMap properties);

        default void handlePlacement(Point point, Block block) {
            handlePlacement(point.blockX(), point.blockY(), point.blockZ(),
                    CellRule.stateMap(0, block.stateId()));
        }

        void handleChunkLoad(int chunkX, int chunkZ);

        void handleChunkUnload(int chunkX, int chunkZ);

        /**
         * Gets the instance that is being ticked
         *
         * @return the ticked instance
         */
        Instance instance();

        CellRule rules();

        Query query();
    }

    public record Metrics(
            int processedSections,
            int processedBlocks,
            int modifiedBlocks
    ) {
        public static final Metrics EMPTY = new Metrics(0, 0, 0);

        public Metrics {
            if (processedSections < 0 || processedBlocks < 0 || modifiedBlocks < 0) {
                throw new IllegalArgumentException("Metrics values cannot be negative");
            }
        }

        public Metrics add(Metrics other) {
            return new Metrics(
                    this.processedSections + other.processedSections,
                    this.processedBlocks + other.processedBlocks,
                    this.modifiedBlocks + other.modifiedBlocks
            );
        }
    }
}
