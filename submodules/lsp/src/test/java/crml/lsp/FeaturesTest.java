package crml.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

class FeaturesTest {
    static final String SRI = String.join("\n",
        "model SRI is FORM_L union {",
        "  Boolean normal_operation is external;",
        "  Real T is external;",
        "  // Req. 1: During normal operation, the system must maintain a temperature between 16°C to 30°C.",
        "  Boolean T_in_range is ((T >= 16) and (T <= 30));",
        "  Boolean req1 is ('during' normal_operation) 'ensure' T_in_range;",
        "",
        "  class Req_speed is {",
        "    Real v is external;",
        "    //Req. 3: To limit erosion and corrosion, the fluid velocity in the heat exchangers shall not exceed 6 m/s.",
        "    Boolean req3 is ('during' true) 'ensure' (v > 6.0);",
        "  };", // the publication listing has no ';' here, which the grammar requires
        "};");

    // ── Outline ──────────────────────────────────────────────

    @Test
    void outlinesModelsClassesAndVariables() {
        List<DocumentSymbol> symbols = DocumentSymbols.of(ParsedDocument.parse(SRI));
        assertEquals(List.of(
            "Module SRI (model)",
            "  Variable normal_operation (Boolean)",
            "  Variable T (Real)",
            "  Variable T_in_range (Boolean)",
            "  Variable req1 (Boolean)",
            "  Class Req_speed (class)",
            "    Field v (Real)",
            "    Field req3 (Boolean)"), render(symbols, ""));
        assertEquals(new Range(new Position(7, 8), new Position(7, 17)), symbols.get(0).getChildren().get(4).getSelectionRange());
    }

    @Test
    void namesOperatorsByTheirCallShape() {
        String text = "library L is {\n  Operator [Boolean] 'during' Boolean b = b;\n  Operator [Boolean] Boolean a 'ensure' Boolean b = a and b;\n};";
        assertEquals(List.of(
            "Package L (library)",
            "  Operator 'during' b (Operator [Boolean])",
            "  Operator a 'ensure' b (Operator [Boolean])"), render(DocumentSymbols.of(ParsedDocument.parse(text)), ""));
    }

    @Test
    void outlinesWhatItCanWhileTheDocumentHasErrors() {
        String text = "model M is {\n  Boolean a is true;\n  Boolean b is ;\n  Real c is 1.0;\n};";
        List<String> outline = render(DocumentSymbols.of(ParsedDocument.parse(text)), "");
        assertEquals("Module M (model)", outline.get(0));
        assertTrue(outline.contains("  Variable a (Boolean)"), outline.toString());
        assertTrue(outline.contains("  Variable c (Real)"), outline.toString());
    }

    private static List<String> render(List<DocumentSymbol> symbols, String indent) {
        List<String> lines = new ArrayList<>();
        for (DocumentSymbol symbol : symbols) {
            lines.add(indent + symbol.getKind() + " " + symbol.getName() + " (" + symbol.getDetail() + ")");
            lines.addAll(render(symbol.getChildren(), indent + "  "));
        }
        return lines;
    }

    // ── Diagnostics ──────────────────────────────────────────

    @Test
    void reportsSyntaxErrorsAtTheOffendingToken() {
        List<Diagnostic> diagnostics = Diagnostics.of(ParsedDocument.parse("model M is {\n  Boolean b is ;\n};"));
        assertEquals(1, diagnostics.size(), diagnostics.toString());
        assertEquals(DiagnosticSeverity.Error, diagnostics.get(0).getSeverity());
        assertEquals(new Range(new Position(1, 15), new Position(1, 16)), diagnostics.get(0).getRange());
    }

    @Test
    void reportsCharactersTheLexerCannotMatch() {
        List<Diagnostic> diagnostics = Diagnostics.of(ParsedDocument.parse("model M is {\n  Boolean b is true; ¤\n};"));
        assertTrue(diagnostics.stream().anyMatch(d -> d.getRange().equals(new Range(new Position(1, 21), new Position(1, 22)))),
            diagnostics.toString());
    }

    @Test
    void reportsNamesTheModelBuilderCannotResolve() {
        List<Diagnostic> diagnostics = Diagnostics.of(ParsedDocument.parse("model M is {\n  Boolean b is c;\n};"));
        assertEquals(1, diagnostics.size(), diagnostics.toString());
        assertEquals("The model could not be built: cannot resolve c", diagnostics.get(0).getMessage().getLeft());
        assertEquals(new Range(new Position(0, 6), new Position(0, 7)), diagnostics.get(0).getRange());
    }

    @Test
    void acceptsTheSriExample() {
        // 'during' and 'ensure' come from FORM_L, which is not resolved; the
        // calls stay unresolved, which is not reported.
        assertEquals(List.of(), Diagnostics.of(ParsedDocument.parse(SRI)));
    }

    // ── Completion ───────────────────────────────────────────

    @Test
    void suggestsDefinitionKindsAtTheStart() {
        assertEquals(List.of("library", "model", "package"), labels(""));
    }

    @Test
    void suggestsElementKeywordsInsideAModel() {
        List<String> labels = labels("model M is {\n  ");
        assertTrue(labels.containsAll(List.of("Boolean", "Real", "class", "Operator", "Template", "constant")), labels.toString());
        assertFalse(labels.contains("and"), labels.toString());
    }

    @Test
    void suggestsOperatorsAfterAnExpression() {
        List<String> labels = labels("model M is {\n  Boolean b is x ");
        assertTrue(labels.containsAll(List.of("and", "or")), labels.toString());
        assertFalse(labels.contains("class"), labels.toString());
    }

    @Test
    void completesTheWordBeingTyped() {
        assertTrue(labels("model M is {\n  Boo").contains("Boolean"));
    }

    @Test
    void suggestsNothingInCommentsAndStrings() {
        assertEquals(List.of(), labels("model M is {\n  // Boo"));
        assertEquals(List.of(), labels("model M is {\n  /* Boo|  */"));
        assertEquals(List.of(), labels("model M is {\n  String s is \"Boo|\";"));
    }

    /** Completion labels, sorted, at the | in the text, or at its end. */
    private static List<String> labels(String textWithCursor) {
        int cursor = textWithCursor.indexOf('|') < 0 ? textWithCursor.length() : textWithCursor.indexOf('|');
        String text = textWithCursor.replace("|", "");
        ParsedDocument document = ParsedDocument.parse(text);
        Position position = document.positions.positionOfOffset(cursor);
        return KeywordCompletion.at(document, position).stream().map(CompletionItem::getLabel).sorted().collect(Collectors.toList());
    }

    // ── Semantic tokens ──────────────────────────────────────

    @Test
    void highlightsLikeTheVsCodeExtension() {
        String[] lines = SRI.split("\n", -1);
        List<Integer> data = SemanticTokensProvider.of(ParsedDocument.parse(SRI)).getData();
        List<String> tokens = new ArrayList<>();
        int line = 0;
        int character = 0;
        for (int i = 0; i < data.size(); i += 5) {
            character = data.get(i) == 0 ? character + data.get(i + 1) : data.get(i + 1);
            line += data.get(i);
            tokens.add(line + " " + SemanticTokensProvider.TOKEN_TYPES.get(data.get(i + 3)) + " "
                + lines[line].substring(character, character + data.get(i + 2)));
        }
        // The same expectations as editors/vscode/test/lexical.test.ts.
        assertEquals(List.of(
            "0 keyword model", "0 keyword is", "0 keyword union",
            "1 type Boolean", "1 keyword is", "1 keyword external",
            "2 type Real", "2 keyword is", "2 keyword external",
            "3 comment " + lines[3].substring(2),
            "4 type Boolean", "4 keyword is", "4 keyword and",
            "5 type Boolean", "5 keyword is", "5 userOperator 'during'", "5 userOperator 'ensure'",
            "7 keyword class", "7 keyword is",
            "8 type Real", "8 keyword is", "8 keyword external",
            "9 comment " + lines[9].substring(4),
            "10 type Boolean", "10 keyword is", "10 userOperator 'during'", "10 userOperator 'ensure'"), tokens);
    }

    // ── Positions ────────────────────────────────────────────

    @Test
    void convertsCodePointsToUtf16AcrossLineBreaks() {
        Positions positions = new Positions("/* 😀 */\r\nx\ry");
        assertEquals(new Position(0, 8), positions.position(7)); // after the comment: the emoji counts twice
        assertEquals(new Position(1, 0), positions.position(9)); // x, after \r\n
        assertEquals(new Position(2, 0), positions.position(11)); // y, after \r
        assertEquals(10, positions.offset(new Position(1, 0)));
        assertEquals(11, positions.offset(new Position(1, 99))); // clamped to the end of line 1
    }
}
