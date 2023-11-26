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
        final int conditionCount = conditionCount(rule.condition(), 0);
        final int resultCount = resultCount(rule.result(), 0);
        return Math.max(conditionCount, resultCount) + 1;
    }

    /**
     * Finds the largest state index used anywhere within the result.
     */
    private static int resultCount(Rule.Result result, int current) {
        return switch (result) {
            case Rule.Result.And and -> {
                int max = current;
                for (Rule.Result r : and.others()) {
                    max = Math.max(max, resultCount(r, current));
                }
                yield max;
            }
            case Rule.Result.Set set -> Math.max(current, set.index());
        };
    }

    /**
     * Finds the largest state index used anywhere within the condition.
     */
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
                    conditionCount(equal.first(), current),
                    conditionCount(equal.second(), current)
            ));
            case Rule.Condition.Index index -> Math.max(current, index.stateIndex());
            case Rule.Condition.Neighbors neighbors -> {
                int max = current;
                for (Point ignored : neighbors.offsets()) {
                    max = Math.max(max, conditionCount(neighbors.condition(), current));
                }
                yield max;
            }
            case Rule.Condition.Not not -> conditionCount(not.condition(), current);
            case Rule.Condition.Or or -> {
                int max = current;
                for (Rule.Condition c : or.conditions()) {
                    max = Math.max(max, conditionCount(c, current));
                }
                yield max;
            }
            case Rule.Condition.Compare cmp -> Math.max(current, Math.max(
                    conditionCount(cmp.first(), current),
                    conditionCount(cmp.second(), current)
            ));
            case Rule.Condition.Literal ignored -> current;
        };
    }
}
