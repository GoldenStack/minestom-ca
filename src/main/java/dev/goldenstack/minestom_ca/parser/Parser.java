package dev.goldenstack.minestom_ca.parser;


import dev.goldenstack.minestom_ca.Rule;
import dev.goldenstack.minestom_ca.parser.Scanner.Token;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.goldenstack.minestom_ca.Neighbors.*;
import static java.util.Map.entry;

public final class Parser {
    private static final Map<String, List<Point>> TARGETS = Map.ofEntries(
            entry("up", List.of(UP)),
            entry("down", List.of(DOWN)),
            entry("north", List.of(NORTH)),
            entry("south", List.of(SOUTH)),
            entry("east", List.of(EAST)),
            entry("west", List.of(WEST)),
            entry("northeast", List.of(NORTHEAST)),
            entry("southeast", List.of(SOUTHEAST)),
            entry("northwest", List.of(NORTHWEST)),
            entry("southwest", List.of(SOUTHWEST)),
            entry("moore2dself", MOORE_2D_SELF),
            entry("moore2d", MOORE_2D)
    );

    private List<Token> tokens;
    private int index;

    private final AtomicInteger stateCounter = new AtomicInteger(0);

    private final Map<String, Integer> properties = new HashMap<>();
    private final List<Rule> rules = new ArrayList<>();

    public Parser() {
    }

    public void feedTokens(List<Token> tokens) {
        System.out.println("feedTokens " + tokens);
        this.tokens = tokens;
        this.index = 0;
        while (!isAtEnd()) {
            final Rule.Condition condition = nextCondition();
            consume(Token.Arrow.class, "Expected '->'");
            final Rule.Result result = nextResult();
            this.rules.add(new Rule(condition, result));
        }
    }

    private Rule.Condition nextCondition() {
        while (!(peek() instanceof Token.Arrow)) {
            CountPredicate countPredicate = new CountPredicate(1, false, false, 0);
            if (peek() instanceof Token.LeftBracket) {
                countPredicate = nextCountPredicate();
            }

            if (peek() instanceof Token.Constant constant) {
                // Self block check
                advance();
                final Block block = Block.fromNamespaceId(constant.value());
                if (block == null) throw error("Unknown block " + constant.value());
                if (peek() instanceof Token.Arrow) {
                    return new Rule.Condition.Equal(block);
                } else if (peek() instanceof Token.And) {
                    advance();
                    return new Rule.Condition.And(new Rule.Condition.Equal(block), nextCondition());
                }
            } else if (peek() instanceof Token.Exclamation) {
                // Self block check not
                advance();
                final Token.Constant constant = consume(Token.Constant.class, "Expected constant");
                final Block block = Block.fromNamespaceId(constant.value());
                if (block == null) throw error("Unknown block " + constant.value());
                final Rule.Condition.Not condition = new Rule.Condition.Not(new Rule.Condition.Equal(block));
                if (peek() instanceof Token.Arrow) {
                    return condition;
                } else if (peek() instanceof Token.And) {
                    advance();
                    return new Rule.Condition.And(condition, nextCondition());
                }
            } else if (peek() instanceof Token.Identifier identifier) {
                advance();
                if (!(peek() instanceof Token.At)) {
                    // Self identifier
                    final int index = this.properties.computeIfAbsent(identifier.value(),
                            s -> stateCounter.incrementAndGet());
                    return switch (advance()) {
                        case Token.Equals ignored -> new Rule.Condition.Equal(
                                new Rule.Expression.Index(index),
                                nextExpression()
                        );
                        case Token.Exclamation ignored -> new Rule.Condition.Not(
                                new Rule.Condition.Equal(
                                        new Rule.Expression.Index(index),
                                        nextExpression()
                                ));
                        default -> throw error("Expected operator");
                    };
                } else {
                    // Neighbor block check
                    consume(Token.At.class, "Expected '@'");
                    final List<Point> targets = TARGETS.get(identifier.value());
                    Rule.Expression neighborsCount = new Rule.Expression.NeighborsCount(targets, nextCondition());
                    return new Rule.Condition.Equal(countPredicate.compare ?
                            new Rule.Expression.Compare(neighborsCount, new Rule.Expression.Literal(countPredicate.compareWith())) :
                            neighborsCount,
                            new Rule.Expression.Literal(countPredicate.value())
                    );
                }
            }
        }
        throw error("Expected condition");
    }

    private CountPredicate nextCountPredicate() {
        consume(Token.LeftBracket.class, "Expected '['");
        if (peek() instanceof Token.Number number && peekNext() instanceof Token.RightBracket) {
            // Constant
            advance();
            consume(Token.RightBracket.class, "Expected ']'");
            return new CountPredicate((int) number.value(), false, false, 0);
        } else if (peek() instanceof Token.Number number && peekNext() instanceof Token.Exclamation) {
            // Not
            advance();
            advance();
            consume(Token.RightBracket.class, "Expected ']'");
            return new CountPredicate((int) number.value(), false, true, 0);
        } else if (peek() instanceof Token.GreaterThan && peekNext() instanceof Token.Number number) {
            // Greater than
            advance();
            advance();
            consume(Token.RightBracket.class, "Expected ']'");
            return new CountPredicate(-1, true, false, (int) number.value());
        } else if (peek() instanceof Token.LessThan lessThan && peekNext() instanceof Token.Number number) {
            // Lesser than
            advance();
            advance();
            consume(Token.RightBracket.class, "Expected ']'");
            return new CountPredicate(1, true, false, (int) number.value());
        }
        throw error("Expected count predicate");
    }

    record CountPredicate(int value, boolean compare, boolean not, int compareWith) {
    }

    private Rule.Result nextResult() {
        while (!(peek() instanceof Token.EOF)) {
            if (peek() instanceof Token.Constant constant) {
                advance();
                final Block block = Block.fromNamespaceId(constant.value());
                if (block == null) throw error("Unknown block " + constant.value());
                if (peek() instanceof Token.EOF) {
                    return new Rule.Result.Set(block);
                } else if (peek() instanceof Token.And) {
                    advance();
                    throw new UnsupportedOperationException();
                }
            }
        }
        throw error("Expected result");
    }

    private Rule.Expression nextExpression() {
        if (peek() instanceof Token.Number number) {
            advance();
            return new Rule.Expression.Literal((int) number.value());
        }
        if (peek() instanceof Token.Constant constant) {
            advance();
            final Block block = Block.fromNamespaceId(constant.value());
            if (block == null) throw error("Unknown block " + constant.value());
            return new Rule.Expression.Literal(block);
        }
        throw error("Expected number");
    }

    public List<Rule> rules() {
        return List.copyOf(rules);
    }

    <T extends Token> T consume(Class<T> type, String message) {
        if (check(type)) return (T) advance();
        throw error(message);
    }

    Token advance() {
        if (!isAtEnd()) index++;
        return previous();
    }

    Token previous() {
        return tokens.get(index - 1);
    }

    boolean check(Class<? extends Token> type) {
        if (isAtEnd()) return false;
        // peek() instanceof type
        return peek().getClass().isAssignableFrom(type);
    }

    boolean isAtEnd() {
        return peek() instanceof Token.EOF;
    }

    Token peek() {
        return tokens.get(index);
    }

    Token peekNext() {
        return tokens.get(index + 1);
    }

    private RuntimeException error(String message) {
        return new RuntimeException("Error at line " + -1 + ": " + message + " got " + peek().getClass().getSimpleName());
    }
}
