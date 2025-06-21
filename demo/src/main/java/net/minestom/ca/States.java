package net.minestom.ca;

public final class States {
    // Direction state - stores which direction to push (0=none, Direction ordinal + 1)
    public static final Automata.Rule.State PUSH_DIRECTION = new Automata.Rule.State("push_direction", 4);
    // Strength state - stores how far the push should propagate
    public static final Automata.Rule.State PUSH_STRENGTH = new Automata.Rule.State("push_strength", 8);

    // Data state - data stored inside a wire or source block
    public static final Automata.Rule.State DATA = new Automata.Rule.State("data", 64);
}
