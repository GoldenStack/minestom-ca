package dev.goldenstack.minestom_ca.parser;

import net.minestom.server.coordinate.Point;
import org.jetbrains.annotations.NotNull;
import org.typemeta.funcj.control.Either;

import java.util.List;

public interface AST {

    record File(@NotNull List<Rule> rules, @NotNull List<OffsetAlias> offsets) {}

    record OffsetAlias(@NotNull String name, @NotNull OffsetExpr values) {}

    sealed interface OffsetExpr {
        record Literal(@NotNull Point value) implements OffsetExpr {}

        record And(@NotNull OffsetExpr first, @NotNull OffsetExpr second) implements OffsetExpr {}
    }

    record Rule(@NotNull Expr expression, @NotNull Result result) {}

    sealed interface Expr {
        record Integer(int value) implements Expr {}
        record Index(@NotNull String name) implements Expr {}

        record ValueLiteral(@NotNull String name) implements Expr {}

        record Not(@NotNull Expr expression) implements Expr {}
        record Neighbors(@NotNull Expr expression, @NotNull Either<String, Point> offsetOrName) implements Expr {}

        record And(@NotNull Expr first, @NotNull Expr second) implements Expr {}
        record Or(@NotNull Expr first, @NotNull Expr second) implements Expr {}

        record Equal(@NotNull Expr first, @NotNull Expr second) implements Expr {}
        record NotEqual(@NotNull Expr first, @NotNull Expr second) implements Expr {}

        record Greater(@NotNull Expr first, @NotNull Expr second) implements Expr {}
        record Lesser(@NotNull Expr first, @NotNull Expr second) implements Expr {}

    }

    sealed interface Result {
        record And(@NotNull Result first, @NotNull Result second) implements Result {}

        record Set(@NotNull Expr index, @NotNull Expr value) implements Result {}

        record Neighbors(@NotNull Result expression, @NotNull Either<String, Point> offsetOrName) implements Result {}
    }
}

