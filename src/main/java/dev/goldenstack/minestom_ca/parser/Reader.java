package dev.goldenstack.minestom_ca.parser;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.NotNull;
import org.typemeta.funcj.control.Either;
import org.typemeta.funcj.data.Chr;
import org.typemeta.funcj.data.Unit;
import org.typemeta.funcj.functions.Functions;
import org.typemeta.funcj.parser.Combinators;
import org.typemeta.funcj.parser.Parser;
import org.typemeta.funcj.parser.Ref;
import org.typemeta.funcj.parser.Text;

import java.util.Optional;
import java.util.function.BiFunction;

public class Reader {

    /**
     * Parses any number of whitespace or newline characters.
     */
    private static final Parser<Chr, Unit> WS = Text.ws.skipMany();

    /**
     * Parses a single EOF or newline character.
     */
    private static final Parser<Chr, Unit> END = Parser.choice(
            Combinators.eof(),
            Text.ws.map(i -> Unit.UNIT)
    );

    /**
     * Parses an integer.
     */
    private static final Parser<Chr, Integer> INTEGER = Text.chr('-').optional().and(Text.digit.many1()).map(minus -> chrs -> {
        StringBuilder number = new StringBuilder(chrs.size());
        for (var c : chrs) {
            number.append(c.charValue());
        }

        int sign = minus.isPresent() ? -1 : 1;
        try {
            return sign * java.lang.Integer.parseInt(number.toString());
        } catch (NumberFormatException e) {
            throw new RuntimeException(e);
        }
    });

    /**
     * Parses a word (i.e. alphabetical characters).
     */
    private static final Parser<Chr, String> WORD = Text.alpha.many1().map(chrs -> {
        StringBuilder word = new StringBuilder(chrs.size());
        for (var c : chrs) {
            word.append(c.charValue());
        }
        return word.toString();
    });

    /**
     * Parses a point, i.e. three integers enclosed within curly brackets and separated with commas.
     */
    private static final Parser<Chr, Point> OFFSET =
            Text.chr('{')
                    .andL(WS).andR(INTEGER)
                    .andL(WS).andL(Text.string(","))
                    .andL(WS).and(INTEGER)
                    .andL(WS).andL(Text.string(","))
                    .andL(WS).and(INTEGER)
                    .andL(WS).andL(Text.chr('}')).map((x, y, z) -> new Vec(x, y, z));

    /**
     * Parses {@link dev.goldenstack.minestom_ca.parser.AST.Expr expressions}.
     */
    public static final Parser<Chr, AST.Expr> EXPR;

    /**
     * Parses {@link dev.goldenstack.minestom_ca.parser.AST.Result results}.
     */
    public static final Parser<Chr, AST.Result> RESULT;

    /**
     * Parses {@link dev.goldenstack.minestom_ca.parser.AST.Rule rules}.
     */
    public static final Parser<Chr, AST.Rule> RULE;

    /**
     * Parses {@link dev.goldenstack.minestom_ca.parser.AST.OffsetExpr offset expressions}.
     */
    public static final Parser<Chr, AST.OffsetExpr> OFFSET_EXPR;

    /**
     * Parses {@link dev.goldenstack.minestom_ca.parser.AST.OffsetAlias offset aliases}.
     */
    public static final Parser<Chr, AST.OffsetAlias> OFFSET_ALIAS;

    private static <L extends T, R, T> Functions.F2<L, Optional<R>, T> maybeJoin(@NotNull BiFunction<L, R, ? extends T> mapper) {
        return (l, maybe) -> maybe.map(r -> (T) mapper.apply(l, r)).orElse(l);
    }

    private static <L extends T, R, T> Parser<Chr, T> binOp(@NotNull Parser<Chr, L> left, @NotNull String name, @NotNull Parser<Chr, R> right, @NotNull BiFunction<L, R, ? extends T> mapper) {
        return left.andL(WS).and(Text.string(name).andL(WS).andR(right).optional()).map(maybeJoin(mapper));
    }

    static {
        // Expression parsing
        Ref<Chr, AST.Expr> expression = Parser.ref();

        Parser<Chr, AST.Expr> PARENS_EXPR = Text.chr('(').andL(WS).andR(expression).andL(WS).andL(Text.chr(')'));
        Parser<Chr, AST.Expr> INT_EXPR = INTEGER.map(AST.Expr.Integer::new);
        Parser<Chr, AST.Expr> INDEX_EXPR = WORD.map(AST.Expr.Index::new);
        Parser<Chr, AST.Expr> WORD_EXPR = Text.chr('#').andL(WS).andR(WORD).map(AST.Expr.ValueLiteral::new);

        Parser<Chr, AST.Expr> BASE_EXPR = Parser.choice(PARENS_EXPR, INT_EXPR, INDEX_EXPR, WORD_EXPR);

        Ref<Chr, AST.Expr> UNEQUAL_EXPR = Parser.ref();
        UNEQUAL_EXPR.set(binOp(BASE_EXPR, "!=", UNEQUAL_EXPR, AST.Expr.NotEqual::new));

        Ref<Chr, AST.Expr> EQUAL_EXPR = Parser.ref();
        EQUAL_EXPR.set(binOp(UNEQUAL_EXPR, "=", EQUAL_EXPR, AST.Expr.Equal::new));

        Ref<Chr, AST.Expr> LESSER_EXPR = Parser.ref();
        LESSER_EXPR.set(binOp(EQUAL_EXPR, "<", LESSER_EXPR, AST.Expr.Lesser::new));

        Ref<Chr, AST.Expr> GREATER_EXPR = Parser.ref();
        GREATER_EXPR.set(binOp(LESSER_EXPR, ">", GREATER_EXPR, AST.Expr.Greater::new));

        Ref<Chr, AST.Expr> NEIGHBOR_EXPR = Parser.ref();
        NEIGHBOR_EXPR.set(Reader.binOp(GREATER_EXPR, "@", WORD.<Either<String, Point>>map(Either::left).or(OFFSET.map(Either::right)), AST.Expr.Neighbors::new));

        Ref<Chr, AST.Expr> NOT_EXPR = Parser.ref();
        NOT_EXPR.set(Parser.choice(
                NEIGHBOR_EXPR,
                Text.chr('!').andL(WS).andR(NEIGHBOR_EXPR).map(AST.Expr.Not::new)
        ));

        Ref<Chr, AST.Expr> OR_EXPR = Parser.ref();
        OR_EXPR.set(binOp(NOT_EXPR, "|", OR_EXPR, AST.Expr.Or::new));

        Ref<Chr, AST.Expr> AND_EXPR = Parser.ref();
        AND_EXPR.set(binOp(OR_EXPR, "&", AND_EXPR, AST.Expr.And::new));

        expression.set(AND_EXPR);
        EXPR = expression;

        // Result parsing
        Parser<Chr, AST.Result> SET_RESULT = Parser.choice(INT_EXPR, INDEX_EXPR).andL(WS).andL(Text.chr('=')).andL(WS).and(Parser.choice(INT_EXPR, WORD_EXPR)).map(AST.Result.Set::new);

        Parser<Chr, AST.Result> NEIGHBORS_RESULT = binOp(SET_RESULT, "@", WORD.<Either<String, Point>>map(Either::left).or(OFFSET.map(Either::right)), AST.Result.Neighbors::new);

        Ref<Chr, AST.Result> AND_RESULT = Parser.ref();
        AND_RESULT.set(NEIGHBORS_RESULT.andL(WS).and(Text.chr('&').andL(WS).andR(AND_RESULT).optional()).map(maybeJoin(AST.Result.And::new)));

        RESULT = AND_RESULT;

        // Rule parsing
        RULE = EXPR.andL(WS).andL(Text.string("->")).andL(WS).and(RESULT).map(AST.Rule::new);

        // Offset expression parsing
        Parser<Chr, AST.OffsetExpr> LITERAL_OFFSET = OFFSET.map(AST.OffsetExpr.Literal::new);
        Ref<Chr, AST.OffsetExpr> AND_OFFSET = Parser.ref();

        AND_OFFSET.set(binOp(LITERAL_OFFSET, "&", AND_OFFSET, AST.OffsetExpr.And::new));

        OFFSET_EXPR = AND_OFFSET;

        OFFSET_ALIAS = Text.string("let").andL(WS).andR(WORD).andL(WS).andL(Text.chr('=')).andL(WS).and(AND_OFFSET).andL(END).map(AST.OffsetAlias::new);
    }

}
