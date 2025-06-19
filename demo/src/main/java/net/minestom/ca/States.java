package net.minestom.ca;

public final class States {
    // Direction state - stores which direction to push (0=none, Direction ordinal + 1)
    public static final Automata.CellRule.State PUSH_DIRECTION = new Automata.CellRule.State("push_direction", 4);
    // Strength state - stores how far the push should propagate
    public static final Automata.CellRule.State PUSH_STRENGTH = new Automata.CellRule.State("push_strength", 8);
}
