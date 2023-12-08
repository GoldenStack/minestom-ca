package dev.goldenstack.minestom_ca;

import dev.goldenstack.minestom_ca.parser.Parser;
import dev.goldenstack.minestom_ca.parser.Scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record Program(List<Rule> rules, Map<Integer, String> variables) {
    public Program {
        rules = List.copyOf(rules);
        variables = Map.copyOf(variables);
    }

    public static Program fromFile(Path path) {
        final String program;
        try {
            program = Files.readAllLines(path).stream().reduce("", (a, b) -> a + "\n" + b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fromString(program);
    }

    public static Program fromString(String program) {
        List<String> lines = List.of(program.split("\n"));
        Parser parser = new Parser();
        for (String string : lines) {
            List<Scanner.Token> tokens = new Scanner(string).scanTokens();
            parser.feedTokens(tokens);
        }
        return parser.program();
    }
}
