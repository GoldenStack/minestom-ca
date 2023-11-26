package dev.goldenstack.minestom_ca;

import net.minestom.server.coordinate.Point;

/**
 * Utility functions for tracking information about rules.
 */
public final class RuleAnalysis {

    /**
     * Finds the number of state indices used within the given rule.
     *
     * @param rule the rule to check
     * @return the largest state index used, plus one (as the starting index is 0)
     */
    public static int stateCount(Rule rule) {
        return conditionCount(rule.condition(), 0) + 1;
    }

    private static int expressionCount(Rule.Expression expression, int current) {
        return switch (expression) {
            case Rule.Expression.Index index -> Math.max(current, index.stateIndex());
            case Rule.Expression.Literal ignored -> current;
            case Rule.Expression.NeighborsCount neighborsCount -> {
                int max = current;
                for (Point ignored : neighborsCount.offsets()) {
                    max = Math.max(max, conditionCount(neighborsCount.condition(), current));
                }
                yield max;
            }
            case Rule.Expression.Compare cmp -> Math.max(current, Math.max(
                    expressionCount(cmp.first(), current),
                    expressionCount(cmp.second(), current)
            ));
            case Rule.Expression.Operation op -> Math.max(current, Math.max(
                    expressionCount(op.first(), current),
                    expressionCount(op.second(), current)
            ));
        };
    }

    private static int conditionCount(Rule.Condition condition, int current) {
        return switch (condition) {
            case Rule.Condition.And and -> {
                int max = current;
                for (Rule.Condition c : and.conditions()) {
                    max = Math.max(max, conditionCount(c, current));
                }
                yield max;
            }
            case Rule.Condition.Equal equal -> Math.max(current, Math.max(
                    expressionCount(equal.first(), current),
                    expressionCount(equal.second(), current)
            ));
            case Rule.Condition.Not not -> conditionCount(not.condition(), current);
            case Rule.Condition.Or or -> {
                int max = current;
                for (Rule.Condition c : or.conditions()) {
                    max = Math.max(max, conditionCount(c, current));
                }
                yield max;
            }
        };
    }
}
