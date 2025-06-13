package net.goldenstack.minestom_ca.lang;

import net.goldenstack.minestom_ca.lang.Scanner.Token;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static net.goldenstack.minestom_ca.Neighbors.NAMED;

public final class Parser {
    private int line;
    private List<Token> tokens;
    private int index;

    private final AtomicInteger stateCounter = new AtomicInteger(0);

    private final Map<String, Integer> properties = new HashMap<>();
    private final List<Rule> rules = new ArrayList<>();

    public Parser() {
    }

    public void feedTokens(List<Token> tokens) {
        this.line++;
        this.tokens = tokens;
        this.index = 0;
        while (!isAtEnd()) {
            List<Rule.Condition> conditions = new ArrayList<>();
            List<Rule.Result> results = new ArrayList<>();
            while (!(peek() instanceof Token.Arrow)) {
                conditions.add(nextCondition());
                if (peek() instanceof Token.And) advance();
            }
            consume(Token.Arrow.class, "Expected '->'");
            while (!(peek() instanceof Token.EOF)) results.add(nextResult());
            this.rules.add(new Rule(conditions.size() == 1 ? conditions.getFirst() :
                    new Rule.Condition.And(conditions), results));
        }
    }

    private Rule.Condition nextCondition() {
        CountPredicate countPredicate = new CountPredicate(1, false, false, 0);
        if (peek() instanceof Token.LeftBracket) {
            countPredicate = nextCountPredicate();
        }

        if (peek() instanceof Token.Constant) {
            // Self block check
            final Block block = nextBlock();
            return new Rule.Condition.Equal(block);
        } else if (peek() instanceof Token.Exclamation) {
            // Self block check not
            advance();
            final Token.Constant constant = consume(Token.Constant.class, "Expected constant");
            final Block block = Block.fromKey(constant.value());
            if (block == null) throw error("Unknown block " + constant.value());
            return new Rule.Condition.Not(new Rule.Condition.Equal(block));
        } else if (peek() instanceof Token.Identifier(String value)) {
            advance();
            if (!(peek() instanceof Token.At)) {
                // Self identifier
                final int index = getIndex(value);
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
                final List<Point> targets = NAMED.get(value);
                Rule.Expression neighborsCount = new Rule.Expression.NeighborsCount(targets, nextCondition());
                if (countPredicate.compare) {
                    return new Rule.Condition.Equal(
                            new Rule.Expression.Compare(neighborsCount, new Rule.Expression.Literal(countPredicate.compareWith())),
                            new Rule.Expression.Literal(countPredicate.value())
                    );
                } else if (countPredicate.not) {
                    return new Rule.Condition.Not(
                            new Rule.Condition.Equal(
                                    neighborsCount,
                                    new Rule.Expression.Literal(countPredicate.value())
                            ));
                } else {
                    return new Rule.Condition.Equal(
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
        if (peek() instanceof Token.Number(long value) && peekNext() instanceof Token.RightBracket) {
            // Constant
            advance();
            consume(Token.RightBracket.class, "Expected ']'");
            return new CountPredicate((int) value, false, false, 0);
        } else if (peek() instanceof Token.Exclamation && peekNext() instanceof Token.Number(long value)) {
            // Not
            advance();
            advance();
            consume(Token.RightBracket.class, "Expected ']'");
            return new CountPredicate((int) value, false, true, 0);
        } else if (peek() instanceof Token.GreaterThan && peekNext() instanceof Token.Number(long value)) {
            // Greater than
            advance();
            advance();
            consume(Token.RightBracket.class, "Expected ']'");
            return new CountPredicate(1, true, false, (int) value);
        } else if (peek() instanceof Token.LessThan && peekNext() instanceof Token.Number(long value)) {
            // Lesser than
            advance();
            advance();
            consume(Token.RightBracket.class, "Expected ']'");
            return new CountPredicate(-1, true, false, (int) value);
        }
        throw error("Expected count predicate");
    }

    record CountPredicate(int value, boolean compare, boolean not, int compareWith) {
    }

    private Rule.Result nextResult() {
        switch (peek()) {
            case Token.Constant _ -> {
                // Change block
                final Block block = nextBlock();
                return new Rule.Result.SetIndex(0, new Rule.Expression.Literal(block));
            }
            case Token.Identifier identifier -> {
                // Change state
                advance();
                consume(Token.Equals.class, "Expected '='");
                final int index = getIndex(identifier.value());
                return new Rule.Result.SetIndex(index, nextExpression());
            }
            case Token.Tilde _ -> {
                // Block copy
                advance();
                Token.Identifier identifier = consume(Token.Identifier.class, "Expected identifier");
                final List<Point> targets = NAMED.get(identifier.value());
                if (targets.size() > 1) throw error("Block copy can only be used with a single target");
                final Point first = targets.getFirst();
                return new Rule.Result.BlockCopy(first.blockX(), first.blockY(), first.blockZ());
            }
            case Token.Dollar _ -> {
                // Event triggering
                advance();
                final Token.Identifier identifier = consume(Token.Identifier.class, "Expected identifier");
                Rule.Expression expression = null;
                if (peek() instanceof Token.Equals) {
                    consume(Token.Equals.class, "Expected '='");
                    expression = nextExpression();
                }
                return new Rule.Result.TriggerEvent(identifier.value(), expression);
            }
            default -> {
            }
        }
        throw error("Expected result");
    }

    private Rule.Expression nextExpression() {
        Rule.Expression expression = null;
        switch (peek()) {
            case Token.Number number -> {
                advance();
                expression = new Rule.Expression.Literal((int) number.value());
            }
            case Token.Constant _ -> {
                final Block block = nextBlock();
                expression = new Rule.Expression.Literal(block);
            }
            case Token.Identifier identifier -> {
                advance();
                if (!(peek() instanceof Token.At)) {
                    // Self state
                    final int index = getIndex(identifier.value());
                    expression = new Rule.Expression.Index(index);
                } else {
                    // Neighbor state
                    advance();
                    final List<Point> targets = NAMED.get(identifier.value());
                    final Point first = targets.getFirst();
                    final Token.Identifier identifier2 = consume(Token.Identifier.class, "Expected identifier");
                    final int index = getIndex(identifier2.value());
                    expression = new Rule.Expression.NeighborIndex(
                            first.blockX(), first.blockY(), first.blockZ(),
                            index);
                }
            }
            default -> {
            }
        }
        if (expression == null) throw error("Expected number");
        final Rule.Expression.Operation.Type type = switch (peek()) {
            case Token.Plus ignored -> Rule.Expression.Operation.Type.ADD;
            case Token.Minus ignored -> Rule.Expression.Operation.Type.SUBTRACT;
            default -> null;
        };
        if (type != null) {
            advance();
            return new Rule.Expression.Operation(expression, nextExpression(), type);
        }
        return expression;
    }

    private Block nextBlock() {
        final Token.Constant constant = consume(Token.Constant.class, "Expected constant");
        Block block = Block.fromKey(constant.value());
        if (block == null) throw error("Unknown block: " + constant.value());
        if (peek() instanceof Token.LeftBracket && peekNext() instanceof Token.Identifier) {
            // Block has properties
            advance();
            while (!(peek() instanceof Token.RightBracket)) {
                final Token.Identifier propertyName = consume(Token.Identifier.class, "Expected property name");
                consume(Token.Equals.class, "Expected '='");
                String propertyValue;
                if (peek() instanceof Token.Identifier(String value)) {
                    advance();
                    propertyValue = value;
                } else if (peek() instanceof Token.Number(long value)) {
                    advance();
                    propertyValue = String.valueOf(value);
                } else {
                    throw error("Expected property value");
                }
                block = block.withProperty(propertyName.value(), propertyValue);
                if (peek() instanceof Token.Comma) advance();
            }
            consume(Token.RightBracket.class, "Expected ']'");
        }
        return block;
    }

    public Program program() {
        return new Program(rules, properties);
    }

    int getIndex(String identifier) {
        return this.properties.computeIfAbsent(identifier,
                _ -> stateCounter.incrementAndGet());
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
        return new RuntimeException("Error at line " + (line - 1) + ": " + message + " got " + peek().getClass().getSimpleName());
    }
}
