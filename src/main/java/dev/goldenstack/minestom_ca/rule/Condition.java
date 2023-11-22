package dev.goldenstack.minestom_ca.rule;

import dev.goldenstack.minestom_ca.state.LocalState;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * A condition for a rule to be applied.
 */
public interface Condition extends Predicate<@NotNull LocalState> {

    @Override
    boolean test(@NotNull LocalState localState);

}
