package crml.lsp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.misc.IntervalSet;

import crml.language.grammar.crmlLexer;
import crml.language.grammar.crmlParser;

/** What the grammar says about its tokens: which are keywords, and which of those name built-in types or boolean values. */
final class Vocabulary {
    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** Word literals by token type, such as 'model', without quotes. */
    static final Map<Integer, String> WORDS;
    static final IntervalSet BUILTIN_TYPES = firstTokens(crmlParser.RULE_builtin_type);
    static final IntervalSet BOOLEAN_VALUES = firstTokens(crmlParser.RULE_boolean_value);

    static {
        Map<Integer, String> words = new HashMap<>();
        for (int type = 1; type <= crmlLexer.VOCABULARY.getMaxTokenType(); type++) {
            String literal = crmlLexer.VOCABULARY.getLiteralName(type);
            if (literal == null) continue;
            String text = literal.substring(1, literal.length() - 1); // literal names are quoted: 'model'
            if (WORD.matcher(text).matches()) words.put(type, text);
        }
        WORDS = Collections.unmodifiableMap(words);
    }

    private Vocabulary() {
    }

    /** The tokens a rule can start with; for builtin_type and boolean_value, all of their literals. */
    private static IntervalSet firstTokens(int rule) {
        ATN atn = crmlParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[rule]);
    }
}
