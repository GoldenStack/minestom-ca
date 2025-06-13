package net.goldenstack.minestom_ca.lang;

import java.util.ArrayList;
import java.util.List;

public final class Scanner {
    private final String input;
    private int index;
    private int line;

    public Scanner(String input) {
        this.input = input;
    }

    public sealed interface Token {
        record Identifier(String value) implements Token {
        }

        record Constant(String value) implements Token {
        }

        record Number(long value) implements Token {
        }

        record Offset(long x, long y) implements Token {
        }

        record Arrow() implements Token {
        }

        record At() implements Token {
        }

        record Dollar() implements Token {
        }

        record Tilde() implements Token {
        }

        record And() implements Token {
        }

        record Exclamation() implements Token {
        }

        record Comma() implements Token {
        }

        record Separator() implements Token {
        }

        record LeftBracket() implements Token {
        }

        record RightBracket() implements Token {
        }

        record Colon() implements Token {
        }

        record Equals() implements Token {
        }

        record Plus() implements Token {
        }

        record Minus() implements Token {
        }

        record GreaterThan() implements Token {
        }

        record LessThan() implements Token {
        }

        record EOF() implements Token {
        }
    }

    Token nextToken() {
        if (isAtEnd()) return null;
        if (peek() == '\n') {
            advance();
            line++;
            return nextToken();
        }
        skipWhitespace();
        if (isAtEnd()) return null;
        skipComment();
        if (peek() == '\n') {
            advance();
            line++;
            return nextToken();
        }

        if (isAtEnd()) return null;

        char c = advance();

        if (Character.isLetter(c)) {
            final String value = nextIdentifier();
            return new Token.Identifier(value);
        }
        if (c == '#') {
            advance();
            final String value = nextIdentifier();
            return new Token.Constant(value);
        }
        if (Character.isDigit(c)) {
            index--;
            final long value = nextNumber();
            return new Token.Number(value);
        }
        if (c == '{') {
            skipWhitespace();
            final long x = nextNumber();
            skipWhitespace();
            consume(',');
            skipWhitespace();
            final long y = nextNumber();
            skipWhitespace();
            consume('}');
            return new Token.Offset(x, y);
        }
        if (c == '-' && peek() == '>') {
            advance();
            return new Token.Arrow();
        }
        if (c == '@') return new Token.At();
        if (c == '$') return new Token.Dollar();
        if (c == '~') return new Token.Tilde();
        if (c == '&') return new Token.And();
        if (c == '!') return new Token.Exclamation();
        if (c == ',') return new Token.Comma();
        if (c == '|') return new Token.Separator();
        if (c == ':') return new Token.Colon();
        if (c == '[') return new Token.LeftBracket();
        if (c == ']') return new Token.RightBracket();
        if (c == '=') return new Token.Equals();
        if (c == '+') return new Token.Plus();
        if (c == '-') return new Token.Minus();
        if (c == '>') return new Token.GreaterThan();
        if (c == '<') return new Token.LessThan();

        throw new IllegalArgumentException("Unexpected character: " + c);
    }

    private long nextNumber() {
        final int startIndex = index;
        while (Character.isDigit(peek())) advance();
        return Long.parseLong(input.substring(startIndex, index));
    }

    public List<Token> scanTokens() {
        List<Token> tokens = new ArrayList<>();
        Token token;
        while ((token = nextToken()) != null) {
            tokens.add(token);
        }
        tokens.add(new Token.EOF());
        return List.copyOf(tokens);
    }

    void skipComment() {
        if (peek() == '/' && peekNext() == '/') {
            while (peek() != '\n' && !isAtEnd()) advance();
        }
    }

    private String nextIdentifier() {
        var start = index - 1;
        char peek;
        while (Character.isLetterOrDigit((peek = peek())) || peek == '_' || peek == '\'') advance();
        return input.substring(start, index);
    }

    private void consume(char c) {
        final char peek = peek();
        if (peek != c) throw new IllegalArgumentException("Expected '" + c + "' but got '" + peek + "'");
        advance();
    }

    void skipWhitespace() {
        while (Character.isWhitespace(peek())) advance();
    }

    char advance() {
        return input.charAt(index++);
    }

    char peekNext() {
        return input.charAt(index + 1);
    }

    char peek() {
        if (isAtEnd()) return '\0';
        return input.charAt(index);
    }

    private boolean isAtEnd() {
        return index >= input.length();
    }
}
