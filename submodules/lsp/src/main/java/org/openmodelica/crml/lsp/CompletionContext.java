package org.openmodelica.crml.lsp;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import crml.language.grammar.crmlLexer;
import crml.language.grammar.crmlParser.NaturalOperatorDefContext;
import crml.language.grammar.crmlParser.OperatorContext;
import crml.language.grammar.crmlParser.ProgramOperatorDefContext;
import crml.language.grammar.crmlParser.TemplateContext;
import crml.language.grammar.crmlParser.Var_defContext;

/**
 * Where a completion is asked for, read from the text alone: what kind of
 * thing can be written there, which text the chosen item replaces, and the
 * type declared for the value being written, if any. Works on any text,
 * however incomplete.
 */
final class CompletionContext {

    /** What the grammar allows at the cursor. */
    enum Place {
        /** A value can start here, e.g. after {@code is}. */
        VALUE,
        /** A value ends here; an infix or postfix operator can follow. */
        AFTER_VALUE,
        /** A type can be written here, but not a value. */
        TYPE,
        /** A dependency can be written here: {@code model M is |} or after {@code X union}. */
        DEPENDENCY,
        /** A name in {@code flatten { … }}. */
        FLATTENED,
        /** Something else: only keywords are proposed. */
        OTHER,
        /** Nothing: inside a comment or a string. */
        NONE
    }

    private static final int NOT = tokenType("'not'");
    private static final int LESS = tokenType("'<'");
    private static final int BOOLEAN = tokenType("'Boolean'");
    private static final int FLATTEN = tokenType("'flatten'");

    final Place place;
    /** The tokens the grammar allows where the replaced text starts. */
    final IntervalSet expected;
    /**
     * The text the chosen item replaces, as UTF-16 offsets: from
     * {@link #start} to {@link #rangeEnd}. It ends at the cursor
     * ({@link #end}), or after it, at the closing quote of a quoted keyword
     * the cursor is in, e.g. one the editor closed by itself: {@code 'ens|'}.
     */
    final int start;
    final int end;
    final int rangeEnd;
    /** The text typed so far that the chosen item replaces. */
    final String prefix;
    /** The type declared for the value being written, e.g. Boolean in {@code Boolean b is |}; null if unknown. */
    final String expectedType;
    /** The code point index of the cursor, as ANTLR and the source map count. */
    final int cursor;

    private CompletionContext(Place place, IntervalSet expected, int start, int end, int rangeEnd, String prefix,
                              String expectedType, int cursor) {
        this.place = place;
        this.expected = expected;
        this.start = start;
        this.end = end;
        this.rangeEnd = rangeEnd;
        this.prefix = prefix;
        this.expectedType = expectedType;
        this.cursor = cursor;
    }

    static CompletionContext at(ParsedDocument document, Position position) {
        int end = document.positions.offset(position);
        int cursor = document.positions.codePointIndex(end);
        if (KeywordCompletion.insideCommentOrString(document, end, false)) {
            return new CompletionContext(Place.NONE, new IntervalSet(), end, end, end, "", null, cursor);
        }
        int start = replacedFrom(document.text, end);
        int rangeEnd = document.text.startsWith("'", start) ? closingQuote(document.text, end) : end;
        IntervalSet expected = KeywordCompletion.expectedAtEnd(document.text.substring(0, start));
        Place place = expected.contains(NOT) ? Place.VALUE
            : expected.contains(LESS) ? Place.AFTER_VALUE
            : expected.contains(BOOLEAN) ? Place.TYPE
            : expected.contains(FLATTEN) ? Place.DEPENDENCY
            : inFlatten(document.text.substring(0, start)) ? Place.FLATTENED
            : Place.OTHER;
        return new CompletionContext(place, expected, start, end, rangeEnd, document.text.substring(start, end),
            declaredType(document, cursor), cursor);
    }

    /** True if the typed text starts a quoted keyword, e.g. {@code 'becomes tr}. */
    boolean quoted() {
        return prefix.startsWith("'");
    }

    Range range(ParsedDocument document) {
        return new Range(document.positions.positionOfOffset(start), document.positions.positionOfOffset(rangeEnd));
    }

    /** True if the text ends inside {@code flatten { A, B,}, where a name is written next. */
    private static boolean inFlatten(String before) {
        return before.matches("(?s).*\\bflatten\\s*\\{(\\s*[A-Za-z_][A-Za-z0-9_]*\\s*,)*\\s*");
    }

    /**
     * After the closing quote of the quoted keyword the cursor is in, if the
     * rest of the keyword up to it is words and spaces; else the cursor.
     */
    private static int closingQuote(String text, int cursor) {
        int i = cursor;
        while (i < text.length() && (KeywordCompletion.isWordChar(text.charAt(i)) || text.charAt(i) == ' ')) i++;
        return i < text.length() && text.charAt(i) == '\'' ? i + 1 : cursor;
    }

    /**
     * Where the replaced text starts: the word being typed, or a quoted
     * keyword being typed (which may contain spaces): its opening quote if it
     * is on the line, has only words and spaces after it and is not the
     * closing quote of a keyword before it.
     */
    private static int replacedFrom(String text, int end) {
        int word = end;
        while (word > 0 && KeywordCompletion.isWordChar(text.charAt(word - 1))) word--;
        int i = end;
        while (i > 0 && (KeywordCompletion.isWordChar(text.charAt(i - 1)) || text.charAt(i - 1) == ' ')) i--;
        if (i > 0 && text.charAt(i - 1) == '\'') {
            int quote = i - 1;
            int lineStart = quote;
            while (lineStart > 0 && text.charAt(lineStart - 1) != '\n' && text.charAt(lineStart - 1) != '\r') lineStart--;
            int quotesBefore = 0;
            for (int k = lineStart; k < quote; k++) {
                if (text.charAt(k) == '\'') quotesBefore++;
            }
            if (quotesBefore % 2 == 0) return quote;
        }
        return word;
    }

    /**
     * The type declared for the value at the cursor: of the variable whose
     * definition it is in, or the result type of the operator whose body it
     * is in. Null if the cursor is in neither, or the type is not written.
     */
    private static String declaredType(ParsedDocument document, int cursor) {
        for (ParserRuleContext context = innermost(document, document.tree, cursor); context != null; context = context.getParent()) {
            if (context instanceof Var_defContext) {
                Var_defContext variable = (Var_defContext) context;
                return after(variable, "is", cursor) && variable.type() != null ? variable.type().getText() : null;
            }
            if (context instanceof NaturalOperatorDefContext || context instanceof ProgramOperatorDefContext) {
                OperatorContext operator = (OperatorContext) context.getParent();
                return after(context, "=", cursor) && operator.domain != null ? operator.domain.getText() : null;
            }
            if (context instanceof TemplateContext) {
                return after(context, "=", cursor) ? "Boolean" : null;
            }
        }
        return null;
    }

    /** True if {@code context} has the token {@code text} as a child, before the cursor. */
    private static boolean after(ParserRuleContext context, String text, int cursor) {
        for (int i = 0; i < context.getChildCount(); i++) {
            ParseTree child = context.getChild(i);
            if (child instanceof TerminalNode && text.equals(child.getText())) {
                Token token = ((TerminalNode) child).getSymbol();
                return token.getStartIndex() >= 0 && token.getStopIndex() < cursor;
            }
        }
        return false;
    }

    /** The innermost rule whose text contains the cursor, or touches it from the left. */
    private static ParserRuleContext innermost(ParsedDocument document, ParserRuleContext context, int cursor) {
        if (context == null || !contains(document, context, cursor)) return null;
        for (int i = 0; i < context.getChildCount(); i++) {
            ParseTree child = context.getChild(i);
            if (child instanceof ParserRuleContext) {
                ParserRuleContext inner = innermost(document, (ParserRuleContext) child, cursor);
                if (inner != null) return inner;
            }
        }
        return context;
    }

    private static boolean contains(ParsedDocument document, ParserRuleContext context, int cursor) {
        Token start = context.getStart();
        Token stop = context.getStop();
        if (start == null || start.getStartIndex() < 0 || start.getStartIndex() > cursor) return false;
        // A rule that is not finished (no stop, or it ends before it starts)
        // goes on to the cursor.
        if (stop == null || stop.getTokenIndex() < start.getTokenIndex() || stop.getType() == Token.EOF) return true;
        if (cursor <= stop.getStopIndex() + 1) return true;
        // So does a rule the parser gave up in at its end, e.g. Boolean b is |,
        // over the blanks after it.
        return endsInError(context) && blank(document, stop.getStopIndex() + 1, cursor);
    }

    /** True if the parser gave up in {@code context}, or in its last child, and so on. */
    private static boolean endsInError(ParserRuleContext context) {
        for (ParserRuleContext current = context; current != null; ) {
            if (current.exception != null) return true;
            ParseTree last = current.getChildCount() == 0 ? null : current.getChild(current.getChildCount() - 1);
            current = last instanceof ParserRuleContext ? (ParserRuleContext) last : null;
        }
        return false;
    }

    /** True if the code points from start (inclusive) to end (exclusive) are white space. */
    private static boolean blank(ParsedDocument document, int start, int end) {
        String between = document.text.substring(document.positions.utf16Offset(start), document.positions.utf16Offset(end));
        return between.trim().isEmpty();
    }

    private static int tokenType(String literal) {
        for (int type = 1; type <= crmlLexer.VOCABULARY.getMaxTokenType(); type++) {
            if (literal.equals(crmlLexer.VOCABULARY.getLiteralName(type))) return type;
        }
        throw new IllegalStateException("The grammar has no token " + literal);
    }
}
