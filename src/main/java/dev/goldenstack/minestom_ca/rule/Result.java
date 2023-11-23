package dev.goldenstack.minestom_ca.rule;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Determines the result of a rule application.
 */
public sealed interface Result extends Function<@NotNull LocalState, @NotNull Map<Point, Map<Integer, Integer>>> {

    record And(@NotNull List<Result> others) implements Result {
        @Override
        public @NotNull Map<Point, Map<Integer, Integer>> apply(@NotNull LocalState localState) {
            Map<Point, Map<Integer, Integer>> map = new HashMap<>();
            for (var result : others) {
                map.putAll(result.apply(localState));
            }
            return map;
        }
    }

    record Set(@NotNull Point offset, int index, int value) implements Result {
        public Set(Point offset, Block block) {
            this(offset, 0, block.stateId());
        }

        public Set(Block block) {
            this(new Vec(0, 0, 0), block);
        }

        @Override
        public @NotNull Map<Point, Map<Integer, Integer>> apply(@NotNull LocalState localState) {
            return Map.of(offset, Map.of(index, value));
        }
    }
}
