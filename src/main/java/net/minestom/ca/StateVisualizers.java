package net.minestom.ca;

import java.util.function.LongFunction;

public final class StateVisualizers {
    public static final LongFunction<String> BINARY = value -> "0b" + Long.toBinaryString(value);
}
