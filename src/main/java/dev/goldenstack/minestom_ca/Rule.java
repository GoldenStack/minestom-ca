package dev.goldenstack.minestom_ca;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

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

        record Equal(@NotNull Expression first, @NotNull Expression second) implements Condition {
            public Equal(Block block) {
                this(new Expression.Index(0), new Expression.Literal(block.stateId()));
            }
        }
    }

    public sealed interface Result {
        record Set(Map<Integer, Expression> expressions) implements Result {
            public Set(Block block) {
                this(Map.of(0, new Expression.Literal(block.stateId())));
            }
        }
    }

    public sealed interface Expression {
        record Literal(int value) implements Expression {
            public Literal(@NotNull Block block) {
                this(block.stateId());
            }
        }

        record Index(int x, int y, int z, int stateIndex) implements Expression {
            public Index(int stateIndex) {
                this(0, 0, 0, stateIndex);
            }

            public Index(int x, int y, int z) {
                this(x, y, z, 0);
            }

            public Index(@NotNull Point offset, int stateIndex) {
                this(offset.blockX(), offset.blockY(), offset.blockZ(), stateIndex);
            }

            public Index(@NotNull Point offset) {
                this(offset, 0);
            }
        }

        record NeighborsCount(@NotNull List<Point> offsets, @NotNull Condition condition) implements Expression {
            public NeighborsCount {
                offsets = List.copyOf(offsets);
            }

            public NeighborsCount(@NotNull Point offset, @NotNull Condition condition) {
                this(List.of(offset), condition);
            }

            public NeighborsCount(int x, int y, int z, @NotNull Condition condition) {
                this(new Vec(x, y, z), condition);
            }
        }

        record Compare(@NotNull Expression first, @NotNull Expression second) implements Expression {
        }

        record Operation(@NotNull Expression first, @NotNull Expression second,
                         @NotNull Type type) implements Expression {
            public enum Type {
                ADD,
                SUBTRACT,
                MULTIPLY,
                DIVIDE,
                MODULO
            }
        }
    }
}
