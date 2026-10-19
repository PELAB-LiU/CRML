package crml.lsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;

import crml.language.grammar.crmlLexer;

/**
 * Lexical highlighting from the Java lexer, for editors without a client-side
 * highlighter. It matches editors/vscode/src/lexical.ts: keywords, built-in
 * types, user operators, strings and comments; identifiers, numbers, symbols
 * and boolean values stay uncoloured.
 */
public final class SemanticTokensProvider {
    public static final List<String> TOKEN_TYPES =
        Collections.unmodifiableList(Arrays.asList("keyword", "type", "userOperator", "string", "comment"));
    public static final SemanticTokensLegend LEGEND = new SemanticTokensLegend(TOKEN_TYPES, Collections.emptyList());

    private static final int KEYWORD = 0;
    private static final int TYPE = 1;
    private static final int USER_OPERATOR = 2;
    private static final int STRING = 3;
    private static final int COMMENT = 4;

    private SemanticTokensProvider() {
    }

    /** The legend index for a token type, or -1 to leave it uncoloured. */
    static int kindOf(int tokenType) {
        switch (tokenType) {
            case crmlLexer.USER_KEYWORD: return USER_OPERATOR;
            case crmlLexer.STRING: return STRING;
            case crmlLexer.LINE_COMMENT:
            case crmlLexer.ML_COMMENT: return COMMENT;
            default:
                if (!Vocabulary.WORDS.containsKey(tokenType) || Vocabulary.BOOLEAN_VALUES.contains(tokenType)) return -1;
                return Vocabulary.BUILTIN_TYPES.contains(tokenType) ? TYPE : KEYWORD;
        }
    }

    public static SemanticTokens of(ParsedDocument document) {
        Positions positions = document.positions;
        List<Integer> data = new ArrayList<>();
        int previousLine = 0;
        int previousCharacter = 0;
        for (Token token : document.tokens) {
            int kind = kindOf(token.getType());
            if (kind < 0) continue;
            int start = positions.utf16Offset(token.getStartIndex());
            int end = positions.utf16Offset(token.getStopIndex() + 1);
            // Semantic tokens cannot span lines: split block comments, leaving out the line breaks.
            for (int line = positions.lineAt(start); line < positions.lineCount() && positions.lineStart(line) < end; line++) {
                int from = Math.max(start, positions.lineStart(line));
                int to = Math.min(end, positions.lineContentEnd(line));
                if (to <= from) continue;
                int character = from - positions.lineStart(line);
                data.add(line - previousLine);
                data.add(line == previousLine ? character - previousCharacter : character);
                data.add(to - from);
                data.add(kind);
                data.add(0);
                previousLine = line;
                previousCharacter = character;
            }
        }
        return new SemanticTokens(data);
    }
}
