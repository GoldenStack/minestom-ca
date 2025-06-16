package net.minestom.ca.rules;

import net.minestom.ca.Automata.CellRule;
import net.minestom.ca.Automata.Query;
import net.minestom.ca.Neighbors;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuleSamples {

    public static final class GameOfLife implements CellRule {
        private static final long VOID_STATE = Block.AIR.stateId();
        private static final long ALIVE_STATE = Block.WHITE_WOOL.stateId();

        private static final List<Action> KILL_ACTION = List.of(Action.action(action -> {
            action.state(0, VOID_STATE);
            action.wakeup(Neighbors.MOORE_2D_SELF);
        }));
        private static final List<Action> REPRODUCE_ACTION = List.of(Action.action(action -> {
            action.state(0, ALIVE_STATE);
            action.wakeup(Neighbors.MOORE_2D_SELF);
        }));

        @Override
        public void init(Map<State, Integer> mapping) {
        }

        @Override
        public List<Action> process(Query query) {
            final int neighbors = query.countLimit(0, 4, Neighbors.MOORE_2D,
                    state -> state == ALIVE_STATE);
            final long currentState = query.state(0);
            if (currentState == ALIVE_STATE && (neighbors < 2 || neighbors > 3)) {
                // Underpopulation or overpopulation
                return KILL_ACTION;
            } else if (currentState == VOID_STATE && neighbors == 3) {
                // Reproduction
                return REPRODUCE_ACTION;
            }
            return null;
        }

        @Override
        public boolean tracked(Block block) {
            return block.stateId() == ALIVE_STATE;
        }

        @Override
        public Set<State> states() {
            return Set.of();
        }
    }

    public static final class GrassGrow implements CellRule {
        private static final long DIRT_STATE = Block.DIRT.stateId();
        private static final long GRASS_STATE = Block.GRASS_BLOCK.stateId();

        private static final List<Action> GROW_ACTION = List.of(Action.action(action -> {
            action.state(0, GRASS_STATE);
            action.condition(0, DIRT_STATE);
            action.schedule(25);
        }));

        @Override
        public void init(Map<State, Integer> mapping) {
        }

        @Override
        public List<Action> process(Query query) {
            final long currentState = query.state(0);
            return currentState == DIRT_STATE ? GROW_ACTION : null;
        }

        @Override
        public boolean tracked(Block block) {
            return block.stateId() == DIRT_STATE;
        }

        @Override
        public Set<State> states() {
            return Set.of();
        }
    }
}
