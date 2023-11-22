package dev.goldenstack.minestom_ca.rule;

import dev.goldenstack.minestom_ca.state.LocalState;
import net.minestom.server.coordinate.Point;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * A condition for a rule to be applied.
 */
public interface Condition extends Predicate<@NotNull LocalState> {

    record Joined(@NotNull List<Condition> children) implements Condition {
        public Joined(@NotNull Condition @NotNull ... children) {
            this(List.of(children));
        }

        @Override
        public boolean test(@NotNull LocalState localState) {
            for (var child : children) {
                if (!child.test(localState)) return false;
            }
            return true;
        }
    }

    record NeighborCondition(@NotNull IntPredicate validCount, @NotNull List<Point> neighbors, @NotNull Condition condition) implements Condition {
        @Override
        public boolean test(@NotNull LocalState localState) {
            int count = 0;
            for (var point : neighbors) {
                if (condition.test(LocalState.relative(localState, point))) {
                    count++;
                }
            }
            return validCount.test(count);
        }
    }

    record SelfState(@NotNull String state) implements Condition {
        @Override
        public boolean test(@NotNull LocalState localState) {
            return localState.self().variant().equals(state);
        }
    }

    record RelativeState(int x, int y, int z, @NotNull String state) implements Condition {
        @Override
        public boolean test(@NotNull LocalState localState) {
            return localState.relative(x, y, z).variant().equals(state);
        }
    }

    @Override
    boolean test(@NotNull LocalState localState);

}
