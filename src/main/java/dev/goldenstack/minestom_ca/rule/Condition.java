package dev.goldenstack.minestom_ca.rule;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.ToIntFunction;

public sealed interface Condition extends ToIntFunction<@NotNull LocalState> {

    record And(@NotNull List<Condition> conditions) implements Condition {
        public And(@NotNull Condition ... conditions) {
            this(List.of(conditions));
        }

        @Override
        public int applyAsInt(@NotNull LocalState value) {
            for (var condition : conditions) {
                if (condition.applyAsInt(value) == 0) return 0;
            }
            return 1;
        }
    }

    record Or(@NotNull List<Condition> conditions) implements Condition {
        @Override
        public int applyAsInt(@NotNull LocalState value) {
            for (var condition : conditions) {
                if (condition.applyAsInt(value) != 0) return 1;
            }
            return 0;
        }
    }

    record Not(@NotNull Condition condition) implements Condition {
        @Override
        public int applyAsInt(@NotNull LocalState value) {
            return condition.applyAsInt(value) == 0 ? 1 : 0;
        }
    }

    record Equal(@NotNull Condition condition, int expected) implements Condition {
        public Equal(Block block) {
            this(new Index(0), block.stateId());
        }

        @Override
        public int applyAsInt(@NotNull LocalState value) {
            if (condition.applyAsInt(value) == expected) return 1;
            return 0;
        }
    }

    record Index(int stateIndex) implements Condition {
        @Override
        public int applyAsInt(@NotNull LocalState value) {
            return value.selfStateValue(stateIndex);
        }
    }

    record Neighbors(@NotNull List<Point> offsets, @NotNull Condition condition) implements Condition {
        public Neighbors(int x, int y, int z, @NotNull Condition condition) {
            this(List.of(new Vec(x, y, z)), condition);
        }

        @Override
        public int applyAsInt(@NotNull LocalState value) {
            int count = 0;
            for (var offset : offsets) {
                if (value.relativeTest(offset.blockX(), offset.blockY(), offset.blockZ(), condition) != 0) {
                    count++;
                }
            }
            return count;
        }
    }


}
