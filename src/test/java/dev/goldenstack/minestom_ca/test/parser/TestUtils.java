package dev.goldenstack.minestom_ca.test.parser;

import dev.goldenstack.minestom_ca.Rule;
import org.jetbrains.annotations.NotNull;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUtils {

    public static void assertRule(@NotNull String ruleString, @NotNull Rule expected) {
        assertEquals(expected, parseRule(ruleString));
    }

    public static @NotNull Rule parseRule(@NotNull String ruleString) {
        return null; // TODO: Implement rule parsing
    }

}
