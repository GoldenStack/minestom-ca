package net.goldenstack.minestom_ca;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;

import java.util.List;

public interface CellRule {
    Action process(AutomataQuery query);

    boolean tracked(int state);

    List<State> states();

    record State(String name) {
    }

    sealed interface Action {
        record UpdateState(Int2LongMap states) implements Action {
        }

        // Update state after X ticks no matter what
        record Schedule(int tick, Int2LongMap updatedStates) implements Action {
        }

        // Update state after X ticks if specified states are equal
        record ConditionalSchedule(int tick,
                                   Int2LongMap conditionStates,
                                   Int2LongMap updatedStates) implements Action {
        }
    }

    static Int2LongMap stateMap(int key, long value) {
        return Int2LongMaps.singleton(key, value);
    }
}
