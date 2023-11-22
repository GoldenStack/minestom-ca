package dev.goldenstack.minestom_ca.rule;

import org.jetbrains.annotations.NotNull;

/**
 * A generic rule for a cellular automata situated in a Minecraft world.
 * @param condition the condition for rule application
 * @param result the calculated result of rule application
 */
public record Rule(@NotNull Condition condition, @NotNull Result result) {}
