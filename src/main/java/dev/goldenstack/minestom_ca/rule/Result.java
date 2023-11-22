package dev.goldenstack.minestom_ca.rule;

import dev.goldenstack.minestom_ca.state.LocalState;
import dev.goldenstack.minestom_ca.state.State;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Function;

/**
 * Determines a result of a rule application.
 */
public interface Result extends Function<@NotNull LocalState, @NotNull Map<Point, State>> {

    record SetRelative(@NotNull Vec vec, @NotNull State state) implements Result {
        @Override
        public @NotNull Map<Point, State> apply(@NotNull LocalState localState) {
            return Map.of(vec, state);
        }
    }

    record Set(@NotNull State state) implements Result {
        @Override
        public @NotNull Map<Point, State> apply(@NotNull LocalState localState) {
            return Map.of(Vec.ZERO, state);
        }
    }

    @Override
    @NotNull Map<Point, State> apply(@NotNull LocalState localState);

}
