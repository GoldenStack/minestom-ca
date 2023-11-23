package dev.goldenstack.minestom_ca.rule;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A generic rule for a cellular automata situated in a Minecraft world.
 *
 * @param condition the condition for rule application
 * @param result    the calculated result of rule application
 */
public record Rule(@NotNull Condition condition, @NotNull Result result) {

    public sealed interface Condition {
        record And(@NotNull List<Condition> conditions) implements Condition {
            public And {
                conditions = List.copyOf(conditions);
            }

            public And(@NotNull Condition... conditions) {
                this(List.of(conditions));
            }
        }

        record Or(@NotNull List<Condition> conditions) implements Condition {
            public Or {
                conditions = List.copyOf(conditions);
            }
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
            public Neighbors {
                offsets = List.copyOf(offsets);
            }


            public Neighbors(int x, int y, int z, @NotNull Condition condition) {
                this(List.of(new Vec(x, y, z)), condition);
            }
        }
    }

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

}
