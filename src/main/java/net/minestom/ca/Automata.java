package net.minestom.ca;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

public final class Automata {
    public interface Rule {
        // The default state for the block, used to track the block's state
        // Its index is always 0
        State BLOCK_STATE = new State("block_state", 32, false, value -> Objects.requireNonNull(Block.fromStateId((int) value)).state());

        void init(Map<State, Integer> mapping);

        /**
         * Process a single block in the world.
         *
         * @param context processing context
         * @param query   query the current block and its neighbors
         * @return actions to apply to the world
         */
        Action process(Context context, Query query);

        Info info();

        record State(String name,
                     int bitSize,
                     // Transient states aren't affected by copy actions
                     boolean transient_,
                     LongFunction<String> visualizer) {
            public State {
                if (bitSize < 1 || bitSize > 64) {
                    throw new IllegalArgumentException("State bit size must be between 1 and 64");
                }
            }

            public State(String name, int bitSize) {
                this(name, bitSize, false, String::valueOf);
            }
        }

        record Interest(State state, LongPredicate predicate) {
            public static Interest block(Block block) {
                return new Interest(BLOCK_STATE, value -> Block.fromStateId((int) value).id() == block.id());
            }

            public static Interest blockState(Block block) {
                return new Interest(BLOCK_STATE, value -> value == block.stateId());
            }

            public static Interest state(State state) {
                return new Interest(state, value -> value != 0);
            }
        }

        record Info(
                String name,
                String description,
                List<Interest> interests,
                Set<State> states
        ) {
        }

        record Action(
                // Whether states should be cleared before applying the action
                boolean clear,
                // States that emerge from a copy (ignoring transient states)
                long @Nullable [] copy,
                // Block properties to update
                Map<String, String> updatedProperties,
                // States to update
                Int2LongMap updatedStates,
                // Trigger world actions
                List<Trigger> triggers,
                // Relative points to wake up after the action is applied
                List<Vec> wakePoints,
                // Properties to check before applying the action
                @Nullable Map<String, String> conditionProperties,
                // States to check before applying the action
                @Nullable Int2LongMap conditionStates,
                // Number of ticks to wait before applying the action (including interpreting the conditions)
                int scheduleTick
        ) {
            public static Action action(Consumer<Builder> builder) {
                Builder b = new Builder();
                builder.accept(b);
                return b.build();
            }

            public Action immediate() {
                return new Action(clear, copy, updatedProperties, updatedStates, triggers,
                        wakePoints, conditionProperties, conditionStates, 0);
            }

            public static final class Builder {
                boolean clear;
                long @Nullable [] copy;
                Map<String, String> updatedProperties = new HashMap<>();
                Int2LongMap updatedStates = new Int2LongOpenHashMap();
                List<Trigger> triggers = new ArrayList<>();
                List<Vec> wakePoints = Neighbors.MOORE_3D_SELF;
                @Nullable Map<String, String> conditionProperties;
                @Nullable Int2LongMap conditionStates;
                int scheduleTick;

                public void clear() {
                    this.clear = true;
                }

                public void copy(long[] copy) {
                    this.copy = copy;
                }

                public void property(String name, String value) {
                    updatedProperties.put(name, value);
                }

                public void properties(Map<String, String> properties) {
                    updatedProperties.putAll(properties);
                }

                public void state(int state, long value) {
                    updatedStates.put(state, value);
                }

                public void states(long[] states) {
                    for (int i = 0; i < states.length; i++) {
                        updatedStates.put(i, states[i]);
                    }
                }

                public void trigger(Trigger trigger) {
                    triggers.add(trigger);
                }

                public void wakeup(List<Vec> wakePoints) {
                    this.wakePoints = wakePoints;
                }

                public void wakeup(Vec wakePoint) {
                    this.wakePoints = List.of(wakePoint);
                }

                public void condition(int state, long value) {
                    if (conditionStates == null) conditionStates = new Int2LongOpenHashMap();
                    conditionStates.put(state, value);
                }

                public void schedule(int scheduleTick) {
                    this.scheduleTick = scheduleTick;
                }

                Action build() {
                    return new Action(clear, copy, updatedProperties, updatedStates, triggers,
                            wakePoints, conditionProperties, conditionStates, scheduleTick
                    );
                }
            }
        }
    }

    public interface Context {
        long tick();
    }

    public interface Query {
        Query offset(int x, int y, int z);

        default Query offset(Vec offset) {
            return offset(offset.blockX(), offset.blockY(), offset.blockZ());
        }

        int stateIndex(Rule.State state);

        long state(int index);

        default Block block() {
            final long state = state(0);
            return Block.fromStateId((int) state);
        }

        default long state(Rule.State state) {
            final int index = stateIndex(state);
            return state(index);
        }

        long stateAt(int x, int y, int z, int index);

        default Block blockAt(int x, int y, int z) {
            final long state = stateAt(x, y, z, 0);
            return Block.fromStateId((int) state);
        }

        default Block blockAt(Point point) {
            return blockAt(point.blockX(), point.blockY(), point.blockZ());
        }

        long[] queryIndexes();

        default long stateAt(int x, int y, int z, Rule.State state) {
            final int index = stateIndex(state);
            return stateAt(x, y, z, index);
        }

        default long stateAt(Point point, int index) {
            return stateAt(point.blockX(), point.blockY(), point.blockZ(), index);
        }

        long[] queryIndexes(int x, int y, int z);

        default long[] queryIndexes(Point point) {
            return queryIndexes(point.blockX(), point.blockY(), point.blockZ());
        }

        Map<Rule.State, Long> queryNames(int x, int y, int z);

        default int count(int index, List<Vec> points, LongPredicate predicate) {
            int count = 0;
            for (Vec point : points) {
                final long state = stateAt(point, index);
                if (predicate.test(state)) count++;
            }
            return count;
        }

        default int countLimit(int index, int limit, List<Vec> points, LongPredicate predicate) {
            int count = 0;
            for (Vec point : points) {
                final long state = stateAt(point, index);
                if (predicate.test(state) && ++count >= limit) break;
            }
            return count;
        }

        default boolean any(int index, List<Vec> points, LongPredicate predicate) {
            for (Vec point : points) {
                final long state = stateAt(point, index);
                if (predicate.test(state)) return true;
            }
            return false;
        }

        default boolean anyPresent(int index, List<Vec> points) {
            for (Vec point : points) {
                final long state = stateAt(point, index);
                if (state != 0) return true;
            }
            return false;
        }

        default <T> T findFirst(int index, List<Vec> points, Finder<T> finder) {
            for (Vec point : points) {
                final int blockX = point.blockX();
                final int blockY = point.blockY();
                final int blockZ = point.blockZ();
                final long value = stateAt(blockX, blockY, blockZ, index);
                final T result = finder.find(blockX, blockY, blockZ, value);
                if (result != null) return result;
            }
            return null;
        }

        default <T> T findFirstPresent(int index, List<Vec> points, Finder<T> finder) {
            for (Vec point : points) {
                final int blockX = point.blockX();
                final int blockY = point.blockY();
                final int blockZ = point.blockZ();
                final long value = stateAt(blockX, blockY, blockZ, index);
                if (value == 0) continue;
                final T result = finder.find(blockX, blockY, blockZ, value);
                if (result != null) return result;
            }
            return null;
        }

        @FunctionalInterface
        interface Finder<T> {
            T find(int x, int y, int z, long value);
        }
    }

    public record RuleConfig(
            Rule rule,
            // How often the rule executes (1 = every tick, 2 = every other tick)
            int tickInterval,
            // How many times the rule executes within a single tick
            int executionsPerTick
    ) {
        public RuleConfig(Rule rule, int tickInterval) {
            this(rule, tickInterval, 1);
        }

        public RuleConfig(Rule rule) {
            this(rule, 1, 1);
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
        void handlePlacement(int x, int y, int z, Map<Rule.State, Long> properties);

        default void handlePlacement(Point point, Block block) {
            handlePlacement(point.blockX(), point.blockY(), point.blockZ(),
                    Map.of(Rule.BLOCK_STATE, (long) block.stateId()));
        }

        void handleChunkLoad(int chunkX, int chunkZ);

        void handleChunkUnload(int chunkX, int chunkZ);

        void clearAt(int x, int y, int z);

        void updateAt(int x, int y, int z, Map<Rule.State, Long> properties);

        /**
         * Gets the instance that is being ticked
         *
         * @return the ticked instance
         */
        Instance instance();

        List<Rule> rules();

        List<Rule.State> states();

        default Rule.State state(String stateName) {
            for (Rule.State state : states()) {
                if (state.name().equals(stateName)) return state;
            }
            return null;
        }

        Query query();

        void updateRules(List<RuleConfig> configs);

        int lightSpeed();

        void updateLightSpeed(int lightSpeed);

        int tickInterval();

        void updateTickInterval(int tickInterval);
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
