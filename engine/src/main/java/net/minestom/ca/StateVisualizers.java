package net.minestom.ca;

import java.util.function.LongFunction;

public final class StateVisualizers {
    public static final LongFunction<String> DECIMAL = Long::toString;
    public static final LongFunction<String> HEXADECIMAL = value -> "0x" + Long.toHexString(value).toUpperCase();
    public static final LongFunction<String> BINARY = value -> "0b" + Long.toBinaryString(value);
}
