package net.goldenstack.minestom_ca.test.parser;

import net.goldenstack.minestom_ca.Program;
import net.goldenstack.minestom_ca.Rule;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class TestUtils {
    public static void assertRule(@NotNull String rulesString, @NotNull Rule @NotNull ... expected) {
        assertEquals(List.of(expected), parseRules(rulesString));
    }

    public static @NotNull List<Rule> parseRules(@NotNull String ruleString) {
        final Program program = Program.fromString(ruleString);
        return program.rules();
    }
}
