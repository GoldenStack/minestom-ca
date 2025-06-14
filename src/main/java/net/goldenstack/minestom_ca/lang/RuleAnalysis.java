package net.goldenstack.minestom_ca.lang;

import java.util.function.Consumer;

/**
 * Utility functions for tracking information about rules.
 */
public final class RuleAnalysis {
    public static <T extends Rule.Expression> void queryExpression(Rule.Condition condition, Class<T> type, Consumer<T> consumer) {
        switch (condition) {
            case Rule.Condition.And and -> {
                for (Rule.Condition c : and.conditions()) {
                    queryExpression(c, type, consumer);
                }
            }
            case Rule.Condition.Equal equal -> {
                queryExpression(equal.first(), type, consumer);
                queryExpression(equal.second(), type, consumer);
            }
            case Rule.Condition.Not not -> queryExpression(not.condition(), type, consumer);
        }
    }

    public static <T extends Rule.Expression> void queryExpression(Rule.Expression expression, Class<T> type, Consumer<T> consumer) {
        if (type.isInstance(expression)) {
            consumer.accept(type.cast(expression));
            return;
        }
        switch (expression) {
            case Rule.Expression.Compare compare -> {
                queryExpression(compare.first(), type, consumer);
                queryExpression(compare.second(), type, consumer);
            }
            case Rule.Expression.State state -> {
                // Empty
            }
            case Rule.Expression.NeighborState neighborState -> {
                // Empty
            }
            case Rule.Expression.Literal literal -> {
                // Empty
            }
            case Rule.Expression.NeighborsCount neighborsCount -> {
                queryExpression(neighborsCount.condition(), type, consumer);
            }
            case Rule.Expression.Operation operation -> {
                queryExpression(operation.first(), type, consumer);
                queryExpression(operation.second(), type, consumer);
            }
        }
    }
}
