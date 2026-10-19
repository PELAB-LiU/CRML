package crml.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Position;

import crml.language.grammar.crmlLexer;
import crml.language.grammar.crmlParser;

/**
 * Suggests the keywords the grammar allows at the cursor. The text before
 * the cursor, without the word being typed, is parsed; where the parser runs
 * out of input, it reports the tokens it expected next.
 */
public final class KeywordCompletion {
    private KeywordCompletion() {
    }

    public static List<CompletionItem> at(ParsedDocument document, Position position) {
        int cursor = document.positions.offset(position);
        if (insideCommentOrString(document, cursor)) return Collections.emptyList();

        int wordStart = cursor;
        while (wordStart > 0 && isWordChar(document.text.charAt(wordStart - 1))) wordStart--;

        List<CompletionItem> items = new ArrayList<>();
        IntervalSet expected = expectedAtEnd(document.text.substring(0, wordStart));
        for (int type : expected.toArray()) {
            String word = Vocabulary.WORDS.get(type);
            if (word == null) continue;
            CompletionItem item = new CompletionItem(word);
            item.setKind(Vocabulary.BUILTIN_TYPES.contains(type) ? CompletionItemKind.Class
                : Vocabulary.BOOLEAN_VALUES.contains(type) ? CompletionItemKind.Value
                : CompletionItemKind.Keyword);
            items.add(item);
        }
        return items;
    }

    /** The tokens the parser expects after the given text, or none if it fails before reaching the end. */
    private static IntervalSet expectedAtEnd(String prefix) {
        crmlLexer lexer = new crmlLexer(CharStreams.fromString(prefix));
        lexer.removeErrorListeners();
        crmlParser parser = new crmlParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        IntervalSet expected = new IntervalSet();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String message, RecognitionException e) {
                if (offendingSymbol instanceof Token && ((Token) offendingSymbol).getType() == Token.EOF) {
                    expected.addAll(e != null ? e.getExpectedTokens() : ((Parser) recognizer).getExpectedTokens());
                }
            }
        });
        parser.definition();
        return expected;
    }

    private static boolean insideCommentOrString(ParsedDocument document, int cursor) {
        for (Token token : document.tokens) {
            int type = token.getType();
            if (type != crmlLexer.LINE_COMMENT && type != crmlLexer.ML_COMMENT
                && type != crmlLexer.STRING && type != crmlLexer.USER_KEYWORD) continue;
            int start = document.positions.utf16Offset(token.getStartIndex());
            int end = document.positions.utf16Offset(token.getStopIndex() + 1);
            if (start < cursor && cursor < end) return true;
            // A line comment ending the document has no line break, so its end is still inside it.
            if (type == crmlLexer.LINE_COMMENT && cursor == end) {
                char last = document.text.charAt(end - 1);
                if (last != '\n' && last != '\r') return true;
            }
        }
        return false;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
