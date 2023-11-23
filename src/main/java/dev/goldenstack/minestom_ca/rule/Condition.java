package dev.goldenstack.minestom_ca.rule;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
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
            for (Condition child : children) {
                if (!child.test(localState)) return false;
            }
            return true;
        }
    }

    record NeighborCondition(@NotNull IntPredicate validCount,
                             @NotNull List<Point> neighbors,
                             @NotNull Condition condition) implements Condition {
        @Override
        public boolean test(@NotNull LocalState localState) {
            int count = 0;
            for (Point point : neighbors) {
                final LocalState relative = localState.relative(point);
                if (condition.test(relative)) {
                    count++;
                }
            }
            return validCount.test(count);
        }
    }

    record SelfState(int index, int value) implements Condition {
        public SelfState(Block block) {
            this(0, block.stateId());
        }

        @Override
        public boolean test(@NotNull LocalState localState) {
            return localState.selfStateValue(index) == value;
        }
    }

    record RelativeState(int x, int y, int z, int index, int value) implements Condition {
        public RelativeState(int x, int y, int z, Block block) {
            this(x, y, z, 0, block.stateId());
        }

        @Override
        public boolean test(@NotNull LocalState localState) {
            return localState.relativeStateValue(index, x, y, z) == value;
        }
    }
}
