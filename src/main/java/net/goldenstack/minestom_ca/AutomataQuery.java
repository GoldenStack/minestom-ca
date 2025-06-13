package net.goldenstack.minestom_ca;

import java.util.Map;

public interface AutomataQuery {
    int stateAt(int x, int y, int z, int index);

    default int blockAt(int x, int y, int z) {
        return stateAt(x, y, z, 0);
    }

    Map<Integer, Integer> queryIndexes(int x, int y, int z);

    Map<String, Integer> queryNames(int x, int y, int z);
}
