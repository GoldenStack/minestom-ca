package net.goldenstack.minestom_ca;

import java.util.Map;

public interface AutomataQuery {
    long stateAt(int x, int y, int z, int index);

    default long blockAt(int x, int y, int z) {
        return stateAt(x, y, z, 0);
    }

    Map<Integer, Long> queryIndexes(int x, int y, int z);

    Map<String, Long> queryNames(int x, int y, int z);
}
