package org.openmodelica.crml.cql.syntax;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.openmodelica.crml.cql.api.Diagnostic;
import org.openmodelica.crml.cql.api.DiagnosticCodes;
import org.openmodelica.crml.cql.syntax.grammar.CqlLexer;

/**
 * Runs the generated {@link CqlLexer} over a text and reports its lexical problems
 * (frontend plan, M2).
 *
 * <p>The lexer never fails: an unterminated string or name, or a character that starts
 * no token, is an error token, which becomes a diagnostic here. Error tokens stay in
 * the token stream, so that the parser does not accept the statement they are in; the
 * parser must not report them a second time. Escapes other than {@code \"} and
 * {@code \\} in a string are reported here as well.
 */
final class Scanner {

    /** The result of scanning a text: its tokens and its lexical diagnostics. */
    static final class Scan {
        private final SourceText source;
        private final CommonTokenStream tokens;
        private final List<Diagnostic> diagnostics;

        private Scan(SourceText source, CommonTokenStream tokens, List<Diagnostic> diagnostics) {
            this.source = source;
            this.tokens = tokens;
            this.diagnostics = Collections.unmodifiableList(diagnostics);
        }

        SourceText source() {
            return source;
        }

        /** All tokens, filled, hidden ones (comments) included, ending with EOF. */
        CommonTokenStream tokens() {
            return tokens;
        }

        List<Diagnostic> diagnostics() {
            return diagnostics;
        }
    }

    private Scanner() {
    }

    static Scan scan(String text) {
        CqlLexer lexer = new CqlLexer(CharStreams.fromString(text));
        // The grammar has a catch-all rule, so the lexer reports nothing itself;
        // without this, ANTLR's default listener would print to stderr.
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        SourceText source = new SourceText(text);
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Token token : tokens.getTokens()) {
            check(token, source, diagnostics);
        }
        return new Scan(source, tokens, diagnostics);
    }

    private static void check(Token token, SourceText source, List<Diagnostic> diagnostics) {
        switch (token.getType()) {
            case CqlLexer.UNKNOWN_CHAR:
                diagnostics.add(Diagnostic.error(DiagnosticCodes.UNKNOWN_CHARACTER,
                    "unknown character " + describe(token.getText()), source.span(token)));
                break;
            case CqlLexer.UNTERMINATED_STRING:
                diagnostics.add(Diagnostic.error(DiagnosticCodes.UNTERMINATED_STRING,
                    "string not closed with \" on its line", source.span(token)));
                break;
            case CqlLexer.UNTERMINATED_NAME:
                diagnostics.add(Diagnostic.error(DiagnosticCodes.UNTERMINATED_NAME,
                    "name not closed with ` on its line", source.span(token)));
                break;
            case CqlLexer.QUOTED_NAME:
                if (token.getText().length() == 2) {
                    diagnostics.add(Diagnostic.error(DiagnosticCodes.EMPTY_NAME,
                        "empty name in backquotes", source.span(token)));
                }
                break;
            case CqlLexer.STRING:
                checkEscapes(token, source, diagnostics);
                break;
            default:
                break;
        }
    }

    private static void checkEscapes(Token token, SourceText source, List<Diagnostic> diagnostics) {
        String text = token.getText();
        int start = source.offsetOfCodePoint(token.getStartIndex());
        // The lexer guarantees that a backslash is followed by a character before the
        // closing quote.
        int i = 1;
        while (i < text.length() - 1) {
            if (text.charAt(i) != '\\') {
                i++;
                continue;
            }
            char escaped = text.charAt(i + 1);
            if (escaped != '"' && escaped != '\\') {
                int length = 1 + Character.charCount(text.codePointAt(i + 1));
                diagnostics.add(Diagnostic.error(DiagnosticCodes.INVALID_ESCAPE,
                    "invalid escape \\" + new String(Character.toChars(text.codePointAt(i + 1)))
                        + " (only \\\" and \\\\ are escapes)",
                    source.span(start + i, length)));
            }
            i += 2;
        }
    }

    /** {@code 'c'}, or {@code U+XXXX} for a character that does not print. */
    private static String describe(String character) {
        int codePoint = character.codePointAt(0);
        // isSpaceChar as well: isWhitespace leaves out the no-break spaces.
        if (Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint) || Character.getType(codePoint) == Character.FORMAT) {
            return String.format("U+%04X", codePoint);
        }
        return "'" + character + "'";
    }

    /**
     * The value of a {@link CqlLexer#STRING} token: without the quotes and unescaped.
     * An invalid escape (already reported) stands for the character after the
     * backslash.
     */
    static String stringValue(Token token) {
        String text = token.getText();
        StringBuilder value = new StringBuilder(text.length());
        int i = 1;
        while (i < text.length() - 1) {
            int at = text.charAt(i) == '\\' ? i + 1 : i;
            value.append(text.charAt(at));
            i = at + 1;
        }
        return value.toString();
    }

    /** The name a {@link CqlLexer#NAME} or {@link CqlLexer#QUOTED_NAME} token stands for. */
    static String nameValue(Token token) {
        String text = token.getText();
        return token.getType() == CqlLexer.QUOTED_NAME ? text.substring(1, text.length() - 1) : text;
    }
}
