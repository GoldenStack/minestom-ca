package dev.goldenstack.minestom_ca.rule;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Function;

/**
 * Determines a result of a rule application.
 */
public sealed interface Result extends Function<@NotNull LocalState, @NotNull Map<Point, Map<Integer, Integer>>> {

    record SetRelative(@NotNull Point point, int index, int value) implements Result {
        @Override
        public @NotNull Map<Point, Map<Integer, Integer>> apply(@NotNull LocalState localState) {
            return Map.of(point, Map.of(index, value));
        }
    }

    record Set(int index, int value) implements Result {
        public Set(Block block) {
            this(0, block.stateId());
        }

        @Override
        public @NotNull Map<Point, Map<Integer, Integer>> apply(@NotNull LocalState localState) {
            return Map.of(Vec.ZERO, Map.of(index, value));
        }
    }
}
