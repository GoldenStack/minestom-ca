package net.goldenstack.minestom_ca;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

public interface AutomataQuery {
    int stateIndex(String state);

    long state(int index);

    default long state(String state) {
        final int index = stateIndex(state);
        return state(index);
    }

    long stateAt(int x, int y, int z, int index);

    Int2LongMap queryIndexes();

    default long stateAt(int x, int y, int z, String state) {
        final int index = stateIndex(state);
        return stateAt(x, y, z, index);
    }

    default Block blockAt(int x, int y, int z) {
        final long blockState = stateAt(x, y, z, 0);
        return Block.fromStateId((int) blockState);
    }

    Int2LongMap queryIndexes(int x, int y, int z);

    Map<String, Long> queryNames(int x, int y, int z);

    default int countNeighborsState(int index, List<Point> points,
                                    LongPredicate predicate) {
        int count = 0;
        for (Point point : points) {
            final long state = stateAt(point.blockX(), point.blockY(), point.blockZ(), index);
            if (predicate.test(state)) count++;
        }
        return count;
    }

    default int countNeighborsStateLimit(int index, int limit, List<Point> points,
                                         LongPredicate predicate) {
        int count = 0;
        for (Point point : points) {
            final long state = stateAt(point.blockX(), point.blockY(), point.blockZ(), index);
            if (predicate.test(state) && ++count >= limit) break;
        }
        return count;
    }
}
