package net.goldenstack.minestom_ca;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A generic rule for a cellular automata situated in a Minecraft world.
 */
public record Rule(@NotNull Condition condition, List<Result> results) {
    public Rule {
        results = List.copyOf(results);
    }

    public Rule(Condition condition, Result... results) {
        this(condition, List.of(results));
    }

    public sealed interface Condition {
        record And(@NotNull List<Condition> conditions) implements Condition {
            public And {
                conditions = List.copyOf(conditions);
            }

            public And(@NotNull Condition... conditions) {
                this(List.of(conditions));
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
        record SetIndex(int stateIndex, Expression expression) implements Result {
            public SetIndex(Block block) {
                this(0, new Expression.Literal(block.stateId()));
            }
        }

        record BlockCopy(int x, int y, int z) implements Result {
        }

        record TriggerEvent(String event, @Nullable Expression expression) implements Result {
        }
    }

    public sealed interface Expression {
        record Literal(int value) implements Expression {
            public Literal(@NotNull Block block) {
                this(block.stateId());
            }
        }

        record Index(int stateIndex) implements Expression {
        }

        record NeighborIndex(int x, int y, int z, int stateIndex) implements Expression {
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
