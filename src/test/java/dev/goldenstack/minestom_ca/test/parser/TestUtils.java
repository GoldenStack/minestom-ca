package dev.goldenstack.minestom_ca.test.parser;

import dev.goldenstack.minestom_ca.Rule;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUtils {

    public static void assertRule(@NotNull String rulesString, @NotNull Rule @NotNull ... expected) {
        assertEquals(List.of(expected), parseRules(rulesString));
    }

    public static @NotNull List<Rule> parseRules(@NotNull String ruleString) {
        return null; // TODO: Implement rule parsing
    }

}
