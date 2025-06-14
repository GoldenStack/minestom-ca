package net.goldenstack.minestom_ca.lang;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.goldenstack.minestom_ca.Automata;
import net.minestom.server.coordinate.Point;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record Program(List<Rule> rules, Set<String> variables) {
    public Program {
        rules = List.copyOf(rules);
        variables = Set.copyOf(variables);
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

    public Automata.CellRule makeCellRule() {
        List<Automata.CellRule.State> states = new ArrayList<>();
        for (String state : variables) {
            states.add(new Automata.CellRule.State(state));
        }

        boolean[] trackedStates = new boolean[Short.MAX_VALUE];
        for (Rule rule : rules) {
            RuleAnalysis.queryExpression(rule.condition(), Rule.Expression.Literal.class, literal -> {
                if (literal.value() < 0 || literal.value() >= trackedStates.length) return;
                trackedStates[literal.value()] = true;
            });
        }
        return new Automata.CellRule() {
            @Override
            public void init(Map<State, Integer> mapping) {
            }

            @Override
            public List<Action> process(Automata.Query query) {
                Int2LongMap block = null;
                for (Rule rule : rules) {
                    if (!verifyCondition(0, 0, 0, query, rule.condition())) continue;
                    if (block == null) block = new Int2LongOpenHashMap();
                    for (Rule.Result result : rule.results()) {
                        switch (result) {
                            case Rule.Result.SetState set -> {
                                final String state = set.state();
                                final long value = expression(0, 0, 0, query, set.expression());
                                block.put(query.stateIndex(state), value);
                            }
                            case Rule.Result.BlockCopy blockCopy -> {
                                final int blockX = blockCopy.x();
                                final int blockY = blockCopy.y();
                                final int blockZ = blockCopy.z();
                                final long[] queryIndexes = query.queryIndexes(blockX, blockY, blockZ);
                                for (int i = 0; i < queryIndexes.length; i++) {
                                    final long value = queryIndexes[i];
                                    block.put(i, value);
                                }
                            }
                            case Rule.Result.TriggerEvent triggerEvent -> {
                                final String eventName = triggerEvent.event();
                                final Rule.Expression eventExpression = triggerEvent.expression();
                                if (eventExpression != null) {
                                    final long value = expression(0, 0, 0, query, eventExpression);
                                    System.out.println("Event: " + eventName + "=" + value);
                                } else {
                                    System.out.println("Event: " + eventName);
                                }
                            }
                        }
                    }
                }
                if (block == null) return null;
                return List.of(Automata.CellRule.Action.UpdateState(block));
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

    private boolean verifyCondition(int x, int y, int z, Automata.Query query, Rule.Condition condition) {
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

    private long expression(int x, int y, int z, Automata.Query query, Rule.Expression expression) {
        return switch (expression) {
            case Rule.Expression.State state -> {
                final String stateName = state.state();
                yield query.stateAt(x, y, z, stateName);
            }
            case Rule.Expression.NeighborState neighbor -> expression(
                    x + neighbor.x(), y + neighbor.y(), z + neighbor.z(),
                    query,
                    new Rule.Expression.State(neighbor.state()));
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
