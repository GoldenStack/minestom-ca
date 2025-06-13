package net.goldenstack.minestom_ca.rules;

import net.goldenstack.minestom_ca.AutomataQuery;
import net.goldenstack.minestom_ca.CellRule;
import net.goldenstack.minestom_ca.Neighbors;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.Map;

public final class RuleSamples {

    public static final class GameOfLife implements CellRule {
        private static final long VOID_STATE = Block.AIR.stateId();
        private static final long ALIVE_STATE = Block.WHITE_WOOL.stateId();

        @Override
        public Action process(int x, int y, int z, AutomataQuery query) {
            final int neighbors = query.predicateNeighborsState(x, y, z, 0, Neighbors.MOORE_2D,
                    state -> state == ALIVE_STATE);
            final long currentState = query.stateAt(x, y, z, 0);
            if (currentState == ALIVE_STATE && (neighbors < 2 || neighbors > 3)) {
                // Underpopulation or overpopulation
                return new Action.UpdateState(Map.of(0, VOID_STATE));
            } else if (currentState == VOID_STATE && neighbors == 3) {
                // Reproduction
                return new Action.UpdateState(Map.of(0, ALIVE_STATE));
            }
            return null;
        }

        @Override
        public boolean tracked(int state) {
            return state == ALIVE_STATE;
        }

        @Override
        public List<State> states() {
            return List.of();
        }
    }

    public static final class GrassGrow implements CellRule {
        private static final long DIRT_STATE = Block.DIRT.stateId();
        private static final long GRASS_STATE = Block.GRASS_BLOCK.stateId();

        @Override
        public Action process(int x, int y, int z, AutomataQuery query) {
            final long currentState = query.stateAt(x, y, z, 0);
            if (currentState == DIRT_STATE) {
                return new Action.ConditionalSchedule(25, Map.of(0, DIRT_STATE), Map.of(0, GRASS_STATE));
            }
            return null;
        }

        @Override
        public boolean tracked(int state) {
            return state == DIRT_STATE;
        }

        @Override
        public List<State> states() {
            return List.of();
        }
    }
}
