package dev.goldenstack.minestom_ca.parser;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import org.typemeta.funcj.data.Chr;
import org.typemeta.funcj.data.Unit;
import org.typemeta.funcj.parser.Combinators;
import org.typemeta.funcj.parser.Parser;
import org.typemeta.funcj.parser.Text;

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

}
