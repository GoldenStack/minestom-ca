package dev.goldenstack.minestom_ca.test.parser;

import dev.goldenstack.minestom_ca.Rule;
import dev.goldenstack.minestom_ca.parser.Parser;
import dev.goldenstack.minestom_ca.parser.Scanner;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class TestUtils {
    public static void assertRule(@NotNull String rulesString, @NotNull Rule @NotNull ... expected) {
        assertEquals(List.of(expected), parseRules(rulesString));
    }

    public static @NotNull List<Rule> parseRules(@NotNull String ruleString) {
        List<String> lines = List.of(ruleString.split("\n"));
        Parser parser = new Parser();
        for (String string : lines) {
            List<Scanner.Token> tokens = new Scanner(string).scanTokens();
            parser.feedTokens(tokens);
        }
        return parser.rules();
    }
}
