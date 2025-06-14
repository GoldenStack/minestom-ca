package net.goldenstack.minestom_ca;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface CellRule {
    Action process(AutomataQuery query);

    boolean tracked(int state);

    List<State> states();

    record State(String name) {
    }

    record Action(
            Int2LongMap updatedStates,
            // States to check before applying the action
            @Nullable Int2LongMap conditionStates,
            // Number of ticks to wait before applying the action (including interpreting the conditions)
            int scheduleTick) {
        public static Action UpdateState(Int2LongMap states) {
            return new Action(states, null, 0);
        }

        // Update state after X ticks no matter what
        public static Action Schedule(int tick, Int2LongMap updatedStates) {
            return new Action(updatedStates, null, tick);
        }

        // Update state after X ticks if specified states are equal
        public static Action ConditionalSchedule(int tick, Int2LongMap conditionStates, Int2LongMap updatedStates) {
            return new Action(updatedStates, conditionStates, tick);
        }

        public Action immediate() {
            return new Action(updatedStates, conditionStates, 0);
        }
    }

    static Int2LongMap stateMap(int key, long value) {
        return Int2LongMaps.singleton(key, value);
    }
}
