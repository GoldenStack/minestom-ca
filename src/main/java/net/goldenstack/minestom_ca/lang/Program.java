package net.goldenstack.minestom_ca.lang;

import net.goldenstack.minestom_ca.AutomataQuery;
import net.goldenstack.minestom_ca.CellRule;
import net.minestom.server.coordinate.Point;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Program(List<Rule> rules, Map<String, Integer> variables) {
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

    public CellRule makeCellRule() {
        final int stateCount = RuleAnalysis.stateCount(rules);

        List<CellRule.State> states = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : variables.entrySet()) {
            states.add(new CellRule.State(entry.getKey()));
        }

        boolean[] trackedStates = new boolean[Short.MAX_VALUE];
        for (Rule rule : rules) {
            RuleAnalysis.queryExpression(rule.condition(), Rule.Expression.Literal.class, literal -> {
                if (literal.value() < 0 || literal.value() >= trackedStates.length) return;
                trackedStates[literal.value()] = true;
            });
        }
        return new CellRule() {
            @Override
            public Action process(int x, int y, int z, AutomataQuery query) {
                Map<Integer, Long> block = null;
                for (Rule rule : rules) {
                    if (!verifyCondition(x, y, z, query, rule.condition())) continue;
                    if (block == null) block = new HashMap<>(stateCount);
                    for (Rule.Result result : rule.results()) {
                        switch (result) {
                            case Rule.Result.SetIndex set -> {
                                final int index = set.stateIndex();
                                final long value = expression(x, y, z, query, set.expression());
                                block.put(index, value);
                            }
                            case Rule.Result.BlockCopy blockCopy -> {
                                final int blockX = x + blockCopy.x();
                                final int blockY = y + blockCopy.y();
                                final int blockZ = z + blockCopy.z();
                                // Copy block state
                                Map<Integer, Long> queryIndexes = query.queryIndexes(blockX, blockY, blockZ);
                                for (Map.Entry<Integer, Long> entry : queryIndexes.entrySet()) {
                                    final int index = entry.getKey();
                                    final long value = entry.getValue();
                                    block.put(index, value);
                                }
                            }
                            case Rule.Result.TriggerEvent triggerEvent -> {
                                final String eventName = triggerEvent.event();
                                final Rule.Expression eventExpression = triggerEvent.expression();
                                if (eventExpression != null) {
                                    final long value = expression(x, y, z, query, eventExpression);
                                    System.out.println("Event: " + eventName + "=" + value);
                                } else {
                                    System.out.println("Event: " + eventName);
                                }
                            }
                        }
                    }
                }
                if (block == null) return null;
                return new CellRule.Action.UpdateState(block);
            }

            @Override
            public boolean tracked(int state) {
                return state >= 0 && state < trackedStates.length && trackedStates[state];
            }

            @Override
            public List<State> states() {
                return states;
            }
        };
    }

    private boolean verifyCondition(int x, int y, int z, AutomataQuery query, Rule.Condition condition) {
        return switch (condition) {
            case Rule.Condition.And and -> {
                for (Rule.Condition c : and.conditions()) {
                    if (!verifyCondition(x, y, z, query, c)) yield false;
                }
                yield true;
            }
            case Rule.Condition.Equal equal -> {
                final long first = expression(x, y, z, query, equal.first());
                final long second = expression(x, y, z, query, equal.second());
                yield first == second;
            }
            case Rule.Condition.Not not -> !verifyCondition(x, y, z, query, not.condition());
        };
    }

    private long expression(int x, int y, int z, AutomataQuery query, Rule.Expression expression) {
        return switch (expression) {
            case Rule.Expression.Index index -> {
                final int stateIndex = index.stateIndex();
                yield query.stateAt(x, y, z, stateIndex);
            }
            case Rule.Expression.NeighborIndex index -> expression(
                    x + index.x(), y + index.y(), z + index.z(),
                    query,
                    new Rule.Expression.Index(index.stateIndex()));
            case Rule.Expression.Literal literal -> literal.value();
            case Rule.Expression.NeighborsCount neighborsCount -> {
                int count = 0;
                for (Point offset : neighborsCount.offsets()) {
                    final int nX = x + offset.blockX();
                    final int nY = y + offset.blockY();
                    final int nZ = z + offset.blockZ();
                    if (verifyCondition(nX, nY, nZ, query, neighborsCount.condition())) count++;
                }
                yield count;
            }
            case Rule.Expression.Compare compare -> {
                final long first = expression(x, y, z, query, compare.first());
                final long second = expression(x, y, z, query, compare.second());
                yield (long) Math.signum(first - second);
            }
            case Rule.Expression.Operation operation -> {
                final long first = expression(x, y, z, query, operation.first());
                final long second = expression(x, y, z, query, operation.second());
                yield switch (operation.type()) {
                    case ADD -> first + second;
                    case SUBTRACT -> first - second;
                    case MULTIPLY -> first * second;
                    case DIVIDE -> first / second;
                    case MODULO -> first % second;
                };
            }
        };
    }
}
