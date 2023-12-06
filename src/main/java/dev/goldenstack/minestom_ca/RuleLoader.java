package dev.goldenstack.minestom_ca;

import dev.goldenstack.minestom_ca.parser.Parser;
import dev.goldenstack.minestom_ca.parser.Scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class RuleLoader {
    public static List<Rule> fromFile(Path path){
        final String program;
        try {
            program = Files.readAllLines(path).stream().reduce("", (a, b) -> a + "\n" + b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fromString(program);
    }

    public static List<Rule> fromString(String program){
        List<String> lines = List.of(program.split("\n"));
        Parser parser = new Parser();
        for (String string : lines) {
            List<Scanner.Token> tokens = new Scanner(string).scanTokens();
            parser.feedTokens(tokens);
        }
        return parser.rules();
    }
}
