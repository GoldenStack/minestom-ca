package dev.goldenstack.minestom_ca.rule;

import dev.goldenstack.minestom_ca.state.LocalState;
import dev.goldenstack.minestom_ca.state.State;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Determines a result of a rule application.
 */
public interface Result extends Function<@NotNull LocalState, LocalState.@NotNull Changes> {

    record SetRelative(@NotNull Vec vec, @NotNull State state) implements Result {
        @Override
        public LocalState.@NotNull Changes apply(@NotNull LocalState localState) {
            return new LocalState.Changes(Object2ObjectMaps.singleton(vec, state));
        }
    }

    record Set(@NotNull State state) implements Result {
        @Override
        public LocalState.@NotNull Changes apply(@NotNull LocalState localState) {
            return new LocalState.Changes(Object2ObjectMaps.singleton(Vec.ZERO, state));
        }
    }

    @Override
    LocalState.@NotNull Changes apply(@NotNull LocalState localState);

}
