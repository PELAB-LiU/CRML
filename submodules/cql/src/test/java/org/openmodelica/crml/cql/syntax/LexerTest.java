package org.openmodelica.crml.cql.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openmodelica.crml.cql.api.Diagnostic;
import org.openmodelica.crml.cql.api.DiagnosticCodes;
import org.openmodelica.crml.cql.syntax.Scanner.Scan;
import org.openmodelica.crml.cql.syntax.grammar.CqlLexer;

/** The lexer (frontend plan, M2): tokens of spec section 4.1, spans, lexical diagnostics. */
class LexerTest {

    /** The symbolic names of the default-channel tokens of {@code text}, EOF left out. */
    private static String types(String text) {
        List<String> names = new ArrayList<>();
        for (Token token : tokens(text)) {
            names.add(CqlLexer.VOCABULARY.getSymbolicName(token.getType()));
        }
        return String.join(" ", names);
    }

    /** The default-channel tokens of {@code text}, EOF left out. */
    private static List<Token> tokens(String text) {
        List<Token> tokens = new ArrayList<>();
        for (Token token : Scanner.scan(text).tokens().getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static List<Diagnostic> diagnostics(String text) {
        return Scanner.scan(text).diagnostics();
    }

    // ── Tokens ───────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "resource | RESOURCE", "as | AS", "let | LET", "default | DEFAULT", "all | ALL",
        "some | SOME", "no | NO", "in | IN", "contains | CONTAINS", "is | IS", "it | IT",
        "count | COUNT", "union | UNION", "intersect | INTERSECT", "except | EXCEPT",
        "true | TRUE", "false | FALSE",
    })
    void keywords(String text, String type) {
        assertEquals(type, types(text));
    }

    /** error, warning and info are names to the lexer; the parser reads them after "!". */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "error | NAME", "warning | NAME", "info | NAME",
        "inside | NAME", "counter | NAME", "it2 | NAME", "_x | NAME", "Pump_1 | NAME",
    })
    void wordsThatAreNames(String text, String type) {
        assertEquals(type, types(text));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
        "== | EQ", "!= | NE", "<= | LE", ">= | GE", "< | LT", "> | GT", "! | NOT",
        "!in | NOT_IN", "!contains | NOT_CONTAINS", ":= | ASSIGN", ":: | DCOLON",
        ": | COLON", ".. | DOTDOT", ". | DOT", "/ | SLASH", "',' | COMMA", "'|' | BAR",
        "; | SEMI", "( | LPAREN", ") | RPAREN", "[ | LBRACK", "] | RBRACK", "{ | LBRACE",
        "} | RBRACE", "-> | ARROW", "- | MINUS", "~ | TILDE", "+ | PLUS", "* | STAR",
        "= | EQUALS",
    })
    void operators(String text, String type) {
        assertEquals(type, types(text));
    }

    /** The cases where the longest match and the order of the rules decide. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
        "!inside        | NOT NAME",
        "!in(           | NOT_IN LPAREN",
        "!in x          | NOT_IN NAME",
        "!in_x          | NOT NAME",
        "!in2           | NOT NAME",
        "!containsX     | NOT NAME",
        "!contains MO   | NOT_CONTAINS NAME",
        "! in           | NOT IN",
        "!=             | NE",
        "0..1           | INT DOTDOT INT",
        "1..*           | INT DOTDOT STAR",
        "1.5            | REAL",
        "1.             | INT DOT",
        "x.1            | NAME DOT INT",
        "-3             | MINUS INT",
        "]-[            | RBRACK MINUS LBRACK",
        "]->            | RBRACK ARROW",
        "]-             | RBRACK MINUS",
        "'S/[T] -[R 1]-> U' | NAME SLASH LBRACK NAME RBRACK MINUS LBRACK NAME INT RBRACK ARROW NAME",
        "'/<it, .f>'    | SLASH LT IT COMMA DOT NAME GT",
        ".~f+           | DOT TILDE NAME PLUS",
        ".@container    | DOT BUILTIN",
        "@container+    | BUILTIN PLUS",
        "pkg::T         | NAME DCOLON NAME",
        "x :=           | NAME ASSIGN",
        "a:b            | NAME COLON NAME",
        ".count         | DOT COUNT",
    })
    void longestMatch(String text, String types) {
        assertEquals(types, types(text));
    }

    @Test
    void strings() {
        assertEquals("STRING", types("\"Pump\""));
        assertEquals("STRING", types("\"a # b\""));
        assertEquals("STRING", types("\"\""));
        List<Token> tokens = tokens("\"say \\\"hi\\\" \\\\ there\"");
        assertEquals(1, tokens.size());
        assertEquals("say \"hi\" \\ there", Scanner.stringValue(tokens.get(0)));
        assertEquals("'or'", Scanner.stringValue(tokens("\"'or'\"").get(0)));
    }

    @Test
    void quotedNames() {
        List<Token> tokens = tokens("`in` `count` `two words`");
        assertEquals("QUOTED_NAME QUOTED_NAME QUOTED_NAME", types("`in` `count` `two words`"));
        assertEquals("in", Scanner.nameValue(tokens.get(0)));
        assertEquals("two words", Scanner.nameValue(tokens.get(2)));
        assertEquals("Pump", Scanner.nameValue(tokens("Pump").get(0)));
    }

    @Test
    void builtins() {
        assertEquals("BUILTIN", types("@container"));
        assertEquals("@referrers", tokens("@referrers").get(0).getText());
    }

    /** Line breaks are whitespace (plan F2); comments go to the hidden channel. */
    @Test
    void whitespaceAndComments() {
        assertEquals("COUNT LPAREN NAME RPAREN EQ INT",
            types("count(\n  vars\r\n)\t==   # three of them\n 3 # done"));
        List<String> comments = new ArrayList<>();
        for (Token token : Scanner.scan("a # one\nb #two").tokens().getTokens()) {
            if (token.getChannel() == Token.HIDDEN_CHANNEL) {
                comments.add(token.getText());
            }
        }
        assertEquals(Arrays.asList("# one", "#two"), comments);
        assertEquals("", types("﻿  \n\t "));
    }

    /** Every token of the examples of spec section 3 is recognised. */
    @Test
    void specExamples() {
        String text = String.join("\n",
            "resource(\"UseLogic.crml\")   as CRML",
            "default *                        -[traced]->      MO := TR/[TraceLink]/<.crml, .modelica>",
            "default CRML/[Model]             -[toClass]->     MO/[ClassDefinition] :=",
            "            TR/[TraceLink::.kind == MODEL_TO_CLASS]/<.crml, .modelica>",
            "*/[CustomOperator] -[keywords]-> */[Keyword] := */[CustomOperator]/<it, .header/[Keyword]>",
            "let vars  = CRML/[Variable::@container in model]",
            "let ops   = LOGIC/[CustomOperator::@container is Library]",
            "model -[toClass 1]- cls",
            "no MO/[Placeholder | Reference::no .target, .reason == UNRESOLVED | Reference::.target !in MO]",
            "all v in vars : v.toComponent.name == v.name",
            "BROKEN -[0]-> MO",
            "let orOp = ops/[.keywords.keyword contains \"'or'\"]",
            "xOrY.toComponent.binding/[FunctionCall].function.target == orOp.toFunction ! warning \"$a$\"");
        assertEquals(Arrays.asList(), diagnostics(text));
        assertTrue(tokens(text).size() > 150);
    }

    // ── Spans ────────────────────────────────────────────────────────────────

    private static Span spanOf(String text, String tokenText) {
        Scan scan = Scanner.scan(text);
        for (Token token : scan.tokens().getTokens()) {
            if (token.getText().equals(tokenText)) {
                return scan.source().span(token);
            }
        }
        throw new AssertionError("no token " + tokenText);
    }

    @Test
    void spansOnOneLine() {
        assertEquals(new Span(0, 5, 1, 1), spanOf("count(x) == 1", "count"));
        assertEquals(new Span(9, 2, 1, 10), spanOf("count(x) == 1", "=="));
    }

    @Test
    void spansOverLines() {
        String text = "let a = x\r\nlet b = y\n\nno b\rsome a";
        assertEquals(new Span(15, 1, 2, 5), spanOf(text, "b"));
        assertEquals(new Span(22, 2, 4, 1), spanOf(text, "no"));
        assertEquals(new Span(27, 4, 5, 1), spanOf(text, "some"));
    }

    /** An emoji is one code point for ANTLR and two UTF-16 units for the span. */
    @Test
    void spansAfterCharactersOutsideTheBasicPlane() {
        String text = "\"😀\" == x";
        assertEquals(new Span(0, 4, 1, 1), spanOf(text, "\"😀\""));
        assertEquals(new Span(5, 2, 1, 6), spanOf(text, "=="));
        assertEquals(new Span(8, 1, 1, 9), spanOf(text, "x"));
    }

    @Test
    void spanOfEndOfFile() {
        Scan scan = Scanner.scan("a\nb");
        List<Token> all = scan.tokens().getTokens();
        assertEquals(new Span(3, 0, 2, 2), scan.source().span(all.get(all.size() - 1)));
    }

    // ── Lexical errors ───────────────────────────────────────────────────────

    private static void assertDiagnostic(String text, String code, Span span) {
        List<Diagnostic> diagnostics = diagnostics(text);
        assertEquals(1, diagnostics.size(), diagnostics::toString);
        assertEquals(code, diagnostics.get(0).code());
        assertEquals(Diagnostic.Severity.ERROR, diagnostics.get(0).severity());
        assertEquals(span, diagnostics.get(0).span());
    }

    @Test
    void unknownCharacters() {
        assertDiagnostic("a ? b", DiagnosticCodes.UNKNOWN_CHARACTER, new Span(2, 1, 1, 3));
        assertDiagnostic("x.@ y", DiagnosticCodes.UNKNOWN_CHARACTER, new Span(2, 1, 1, 3));
        assertDiagnostic("a\n$", DiagnosticCodes.UNKNOWN_CHARACTER, new Span(2, 1, 2, 1));
        assertEquals("unknown character '?'", diagnostics("?").get(0).message());
        assertEquals("unknown character U+00A0", diagnostics(" ").get(0).message());
        // The error token stays in the stream, so that the parser rejects the statement.
        assertEquals("NAME UNKNOWN_CHAR NAME", types("a ? b"));
    }

    @Test
    void unterminatedString() {
        String text = "x == \"Pump\ny == 1";
        assertDiagnostic(text, DiagnosticCodes.UNTERMINATED_STRING, new Span(5, 5, 1, 6));
        // The string ends at the line break; the next line is lexed as usual.
        assertEquals("NAME EQ UNTERMINATED_STRING NAME EQ INT", types(text));
        assertDiagnostic("\"abc\\", DiagnosticCodes.UNTERMINATED_STRING, new Span(0, 5, 1, 1));
    }

    @Test
    void unterminatedAndEmptyNames() {
        assertDiagnostic("`in\nx", DiagnosticCodes.UNTERMINATED_NAME, new Span(0, 3, 1, 1));
        assertDiagnostic("a ``", DiagnosticCodes.EMPTY_NAME, new Span(2, 2, 1, 3));
    }

    @Test
    void invalidEscapes() {
        assertDiagnostic("\"a\\nb\"", DiagnosticCodes.INVALID_ESCAPE, new Span(2, 2, 1, 3));
        assertEquals(2, diagnostics("\"\\t\\x\"").size());
        assertEquals(Arrays.asList(), diagnostics("\"\\\\n\""));
        assertEquals("anb", Scanner.stringValue(tokens("\"a\\nb\"").get(0)));
    }

    @Test
    void diagnosticToString() {
        assertEquals("1:3 error unknown-character: unknown character '?'", diagnostics("a ?").get(0).toString());
    }
}
