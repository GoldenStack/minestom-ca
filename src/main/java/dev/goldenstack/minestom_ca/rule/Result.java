package dev.goldenstack.minestom_ca.rule;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Determines the result of a rule application.
 */
public sealed interface Result {

    record And(@NotNull List<Result> others) implements Result {
        public And {
            others = List.copyOf(others);
        }
    }

    record Set(@NotNull Point offset, int index, int value) implements Result {
        public Set(Point offset, Block block) {
            this(offset, 0, block.stateId());
        }

        public Set(Block block) {
            this(new Vec(0, 0, 0), block);
        }
    }
}
