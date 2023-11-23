package dev.goldenstack.minestom_ca.rule;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public sealed interface Condition {

    record And(@NotNull List<Condition> conditions) implements Condition {
        public And(@NotNull Condition... conditions) {
            this(List.of(conditions));
        }
    }

    record Or(@NotNull List<Condition> conditions) implements Condition {
    }

    record Not(@NotNull Condition condition) implements Condition {
    }

    record Equal(@NotNull Condition condition, int expected) implements Condition {
        public Equal(Block block) {
            this(new Index(0), block.stateId());
        }
    }

    record Index(int stateIndex) implements Condition {
    }

    record Neighbors(@NotNull List<Point> offsets, @NotNull Condition condition) implements Condition {
        public Neighbors(int x, int y, int z, @NotNull Condition condition) {
            this(List.of(new Vec(x, y, z)), condition);
        }
    }
}
