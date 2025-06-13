package net.goldenstack.minestom_ca;

import java.util.List;
import java.util.Map;

public interface CellRule {
    Map<Integer, Integer> process(int x, int y, int z, AutomataQuery query);

    boolean tracked(int state);

    List<State> states();

    record State(String name) {
    }
}
