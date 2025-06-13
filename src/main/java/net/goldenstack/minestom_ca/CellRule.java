package net.goldenstack.minestom_ca;

import java.util.List;
import java.util.Map;

public interface CellRule {
    Action process(int x, int y, int z,
                   AutomataQuery query);

    boolean tracked(int state);

    List<State> states();

    record State(String name) {
    }

    sealed interface Action {
        record UpdateState(Map<Integer, Long> states) implements Action {
        }

        // Update state after X ticks no matter what
        record Schedule(int tick, Map<Integer, Long> updatedStates) implements Action {
        }

        // Update state after X ticks if specified states are equal
        record ConditionalSchedule(int tick, Map<Integer, Long> conditionStates,
                                   Map<Integer, Long> updatedStates) implements Action {
        }
    }
}
