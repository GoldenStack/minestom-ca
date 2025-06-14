package net.goldenstack.minestom_ca;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import net.minestom.server.coordinate.Point;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface CellRule {
    State BLOCK_STATE = new State("block_state");

    void init(Map<State, Integer> mapping);

    List<Action> process(AutomataQuery query);

    boolean tracked(int state);

    List<State> states();

    record State(String name) {
    }

    record Action(
            Int2LongMap updatedStates,
            // Relative points to wake up after the action is applied
            List<Point> wakePoints,
            // States to check before applying the action
            @Nullable Int2LongMap conditionStates,
            // Number of ticks to wait before applying the action (including interpreting the conditions)
            int scheduleTick
    ) {
        public static Action UpdateState(Int2LongMap states) {
            return new Action(states, Neighbors.MOORE_3D_SELF, null, 0);
        }

        // Update state after X ticks no matter what
        public static Action Schedule(int tick, Int2LongMap updatedStates) {
            return new Action(updatedStates, Neighbors.MOORE_3D_SELF, null, tick);
        }

        // Update state after X ticks if specified states are equal
        public static Action ConditionalSchedule(int tick, Int2LongMap conditionStates, Int2LongMap updatedStates) {
            return new Action(updatedStates, Neighbors.MOORE_3D_SELF, conditionStates, tick);
        }

        public Action immediate() {
            return new Action(updatedStates, wakePoints, conditionStates, 0);
        }
    }

    static CellRule rules(CellRule... rules) {
        boolean[] trackedStates = new boolean[Short.MAX_VALUE];
        for (int i = 0; i < trackedStates.length; i++) {
            for (CellRule rule : rules) {
                if (rule.tracked(i)) {
                    trackedStates[i] = true;
                    break;
                }
            }
        }

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
            public List<Action> process(AutomataQuery query) {
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
            public boolean tracked(int state) {
                return state >= 0 && state < trackedStates.length && trackedStates[state];
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
