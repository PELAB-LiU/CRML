package crml.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import crml.language.grammar.crmlLexer;
import crml.language.grammar.crmlParser;
import crml.language.grammar.crmlParser.DefinitionContext;

/** A CRML document lexed and parsed, with the syntax errors found on the way. */
public final class ParsedDocument {
    /** A syntax error over code points start (inclusive) to end (exclusive). */
    public static final class SyntaxError {
        public final int start;
        public final int end;
        public final String message;

        SyntaxError(int start, int end, String message) {
            this.start = start;
            this.end = end;
            this.message = message;
        }
    }

    public final String text;
    public final Positions positions;
    /** All tokens, including comments and whitespace, without EOF. */
    public final List<Token> tokens;
    /** The parse tree, partial where the parser recovered from errors. */
    public final DefinitionContext tree;
    public final List<SyntaxError> errors;

    private ParsedDocument(String text, List<Token> tokens, DefinitionContext tree, List<SyntaxError> errors) {
        this.text = text;
        this.positions = new Positions(text);
        this.tokens = tokens;
        this.tree = tree;
        this.errors = errors;
    }

    public static ParsedDocument parse(String text) {
        List<SyntaxError> errors = new ArrayList<>();
        BaseErrorListener listener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String message, RecognitionException e) {
                if (offendingSymbol instanceof Token) {
                    Token token = (Token) offendingSymbol;
                    int end = token.getType() == Token.EOF ? token.getStartIndex() : token.getStopIndex() + 1;
                    errors.add(new SyntaxError(token.getStartIndex(), end, message));
                } else if (recognizer instanceof Lexer) {
                    // The lexer reports the characters from the token start up to the one it could not match.
                    Lexer lexer = (Lexer) recognizer;
                    errors.add(new SyntaxError(lexer._tokenStartCharIndex, lexer.getInputStream().index() + 1, message));
                }
            }
        };

        crmlLexer lexer = new crmlLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        CommonTokenStream stream = new CommonTokenStream(lexer);
        crmlParser parser = new crmlParser(stream);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        DefinitionContext tree = parser.definition();
        stream.fill();
        List<Token> tokens = new ArrayList<>(stream.getTokens());
        tokens.removeIf(token -> token.getType() == Token.EOF);
        return new ParsedDocument(text, Collections.unmodifiableList(tokens), tree, errors);
    }
}
