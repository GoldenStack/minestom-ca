package dev.goldenstack.minestom_ca.rule;

import dev.goldenstack.minestom_ca.state.LocalState;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Determines a result of a rule application.
 */
public interface Result extends Function<@NotNull LocalState, LocalState.@NotNull Changes> {

    @Override
    LocalState.@NotNull Changes apply(@NotNull LocalState localState);

}
