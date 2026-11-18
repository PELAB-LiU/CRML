package org.openmodelica.crml.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

/** Completion: texts with the cursor written as {@code |}. */
class CompletionTest {
    private static final Analyzer ANALYZER = new Analyzer();
    private static final String URI = "file:///test/Completion.crml";

    private static final String HEAD = "model M is FORML union {\n  Boolean b is external;\n  Periods P is external;\n";

    // ── Where a value starts ─────────────────────────────────

    @Test
    void listsOperatorsWithALeftOperandFirstThenTheOthersThenValues() {
        List<CompletionItem> items = complete(HEAD + "  Boolean r is |\n};");
        List<String> labels = labels(items);

        int ensure = labels.indexOf("P 'ensure' b");
        int during = labels.indexOf("'during' b");
        int value = labels.indexOf("b");
        int keyword = labels.indexOf("not");
        assertTrue(ensure >= 0 && during >= 0 && value >= 0 && keyword >= 0, labels::toString);
        assertTrue(ensure < during && during < value && value < keyword, labels::toString);
        assertEquals("${1:P} 'ensure' ${2:b}", edit(item(items, "P 'ensure' b")).getNewText());
        assertEquals("'during' ${1:b}", edit(item(items, "'during' b")).getNewText());
        assertEquals(InsertTextFormat.Snippet, item(items, "'during' b").getInsertTextFormat());
    }

    @Test
    void proposesEachOverloadOnce() {
        List<String> labels = labels(complete(HEAD + "  Boolean r is |\n};"));
        long checkCount = labels.stream().filter(l -> l.startsWith("P 'check count' E")).count();
        assertEquals(6, checkCount, labels::toString);
    }

    @Test
    void leavesOutTheVariableBeingDefined() {
        List<String> labels = labels(complete(HEAD + "  Boolean r is |\n};"));
        assertTrue(labels.contains("b") && labels.contains("P"), labels::toString);
        assertFalse(labels.contains("r"), labels::toString);
    }

    @Test
    void insertsTheBuiltInFormsWhole() {
        List<CompletionItem> items = complete(HEAD + "  Boolean r is |\n};");
        assertEquals("if ${1:condition} then ${2:value} else ${3:otherwise}",
            edit(item(items, "if … then … else …")).getNewText());
        assertFalse(labels(items).contains("if"), "the keyword is replaced by the form");
    }

    @Test
    void ranksOperatorsOfTheDeclaredTypeFirst() {
        List<String> labels = labels(complete(HEAD + "  Clock c is |\n};"));
        // Both take an operand on their left; 'becomes true' gives a Clock, 'ensure' a Boolean.
        assertTrue(labels.indexOf("b 'becomes true'") < labels.indexOf("P 'ensure' b"), labels::toString);
        labels = labels(complete(HEAD + "  Boolean r is |\n};"));
        assertTrue(labels.indexOf("P 'ensure' b") < labels.indexOf("b 'becomes true'"), labels::toString);
    }

    @Test
    void ranksTheDocumentsOwnOperatorsFirst() {
        List<String> labels = labels(complete(HEAD
            + "  Operator [ Boolean ] 'mine' Boolean x = x;\n  Boolean r is |\n};"));
        assertTrue(labels.indexOf("'mine' x") < labels.indexOf("'during' b"), labels::toString);
    }

    @Test
    void proposesTheBooleanValuesWithTheValuesInScope() {
        List<CompletionItem> items = complete(HEAD + "  Boolean r is |\n};");
        List<String> labels = labels(items);
        assertEquals(CompletionItemKind.Constant, item(items, "true").getKind());
        assertEquals(1, labels.stream().filter("true"::equals).count(), "not again as a keyword");
        assertTrue(labels.indexOf("true") < labels.indexOf("P"), "a Boolean before a Periods where a Boolean is defined: " + labels);
        assertTrue(labels.indexOf("undefined") < labels.indexOf("not"), labels::toString);
    }

    // ── After a value ────────────────────────────────────────

    @Test
    void continuesAValueWithTheRestOfAnOperator() {
        List<CompletionItem> items = complete(HEAD + "  Boolean r is P |\n};");
        List<String> labels = labels(items);
        assertEquals("'ensure' ${1:b}", edit(item(items, "P 'ensure' b")).getNewText());
        assertFalse(labels.contains("'during' b"), "an operator without a left operand cannot follow a value");
        assertTrue(labels.contains("and"), labels::toString);
        assertTrue(labels.indexOf("P 'ensure' b") < labels.indexOf("and"), labels::toString);
    }

    @Test
    void replacesAQuotedKeywordBeingTyped() {
        String text = HEAD + "  Boolean r is P 'ens|\n};";
        List<CompletionItem> items = complete(text);
        CompletionItem ensure = item(items, "P 'ensure' b");
        assertEquals("'ensure'", ensure.getFilterText());
        assertEquals(new Range(new Position(3, 17), new Position(3, 21)), edit(ensure).getRange());
        assertTrue(items.stream().noneMatch(i -> i.getKind() == CompletionItemKind.Keyword), "no keywords inside quotes");
    }

    @Test
    void replacesAQuotedKeywordTheEditorClosedByItself() {
        CompletionItem ensure = item(complete(HEAD + "  Boolean r is P 'ens|'\n};"), "P 'ensure' b");
        assertEquals("'ensure'", ensure.getFilterText());
        assertEquals("'ensure' ${1:b}", edit(ensure).getNewText());
        assertEquals(new Range(new Position(3, 17), new Position(3, 22)), edit(ensure).getRange(),
            "the closing quote is replaced too");
    }

    @Test
    void completesRightAfterAnOpeningQuote() {
        CompletionItem ensure = item(complete(HEAD + "  Boolean r is P '|'\n};"), "P 'ensure' b");
        assertEquals(new Range(new Position(3, 17), new Position(3, 19)), edit(ensure).getRange());
    }

    @Test
    void replacesAWordBeingTyped() {
        CompletionItem during = item(complete(HEAD + "  Boolean r is dur|\n};"), "'during' b");
        assertEquals("during", during.getFilterText());
        assertEquals(new Range(new Position(3, 15), new Position(3, 18)), edit(during).getRange());
    }

    // ── Scopes ───────────────────────────────────────────────

    @Test
    void seesTheParametersInAnOperatorBodyButNotTheOperator() {
        List<String> labels = labels(complete(HEAD
            + "  Operator [ Boolean ] 'twice' Boolean x = |;\n};"));
        assertTrue(labels.contains("x"), labels::toString);
        assertFalse(labels.contains("'twice' x"), labels::toString);
    }

    @Test
    void proposesClassesWhereATypeIsWritten() {
        List<CompletionItem> items = complete("model M is {\n  class Pump is { Real v is external; };\n  |\n};");
        CompletionItem pump = item(items, "Pump");
        assertEquals(CompletionItemKind.Class, pump.getKind());
        assertTrue(labels(items).contains("Boolean"), "and the built-in types");
    }

    @Test
    void worksInAnIncompleteDocument() {
        List<String> labels = labels(complete(HEAD + "  Boolean broken is ;\n  Boolean r is |"));
        assertTrue(labels.contains("broken") && labels.contains("'during' b"), labels::toString);
    }

    @Test
    void fallsBackToTheLastDocumentThatHadADom() {
        String uri = "file:///test/Fallback.crml";
        try {
            ANALYZER.complete(uri, ParsedDocument.parse(HEAD + "  Boolean r is |\n};"), new Position(3, 15),
                CompletionEngine.ClientSupport.FULL, new HashMap<>());
            // No DOM at all: the definition kind is missing.
            String broken = "M is FORML union {\n  Boolean b is external;\n  Boolean r is \n};";
            List<CompletionItem> items = ANALYZER.complete(uri, ParsedDocument.parse(broken), new Position(2, 15),
                CompletionEngine.ClientSupport.FULL, new HashMap<>());
            assertTrue(labels(items).contains("'during' b"), () -> labels(items).toString());
        } finally {
            ANALYZER.close(uri);
        }
    }

    @Test
    void proposesNothingInComments() {
        assertEquals(List.of(), complete(HEAD + "  // Boolean r is |\n};"));
    }

    // ── Dependencies ─────────────────────────────────────────

    @Test
    void proposesTheLibrariesToDependOn() {
        List<CompletionItem> items = complete("model M is |");
        CompletionItem etl = item(items, "ETL");
        assertEquals("ETL union ", edit(etl).getNewText());
        assertEquals(CompletionItemKind.Module, etl.getKind());
        assertEquals("library", etl.getLabelDetails().getDescription());
        assertTrue(labels(items).contains("FORML"), () -> labels(items).toString());
    }

    @Test
    void leavesOutTheDependenciesAlreadyListed() {
        List<String> labels = labels(complete("model M is ETL union |\n{\n};"));
        assertTrue(labels.contains("FORML"), labels::toString);
        assertFalse(labels.contains("ETL"), labels::toString);
    }

    @Test
    void proposesNamesInAFlattenedSet() {
        CompletionItem forml = item(complete("model M is flatten { ETL, |"), "FORML");
        assertEquals("FORML", edit(forml).getNewText(), "the union comes after the set");
    }

    @Test
    void proposesOpenModelsToModelsOnly() {
        String other = "file:///test/Other.crml";
        try {
            ANALYZER.diagnose(other, ParsedDocument.parse("model Other is {\n  Boolean o is true;\n};"));
            assertTrue(labels(complete("model M is |")).contains("Other"));
            assertFalse(labels(complete("library L is |")).contains("Other"), "a library cannot depend on a model");
        } finally {
            ANALYZER.close(other);
        }
    }

    // ── Client support and documentation ────────────────────

    @Test
    void insertsPlainTextForClientsWithoutSnippets() {
        List<CompletionItem> items = complete(HEAD + "  Boolean r is |\n};", new CompletionEngine.ClientSupport(false, false, false));
        CompletionItem ensure = item(items, "P 'ensure' b");
        assertEquals("P 'ensure' b", edit(ensure).getNewText());
        assertEquals(InsertTextFormat.PlainText, ensure.getInsertTextFormat());
        assertEquals("Boolean · FORML", ensure.getDetail());
    }

    @Test
    void documentsAnOperatorWithItsSignatureCommentAndLibrary() {
        Map<String, String> documentation = new HashMap<>();
        List<CompletionItem> items = complete(HEAD + "  Boolean r is |\n};", CompletionEngine.ClientSupport.FULL, documentation);
        CompletionItem becomesTrue = item(items, "b 'becomes true'");
        assertEquals(": Clock", becomesTrue.getLabelDetails().getDetail());
        assertEquals("ETL", becomesTrue.getLabelDetails().getDescription());
        String markdown = documentation.get((String) becomesTrue.getData());
        assertNotNull(markdown);
        assertEquals("```crml\nOperator [ Clock ] Boolean b 'becomes true'\n```\n\n"
            + "Operators on events\nEvents generated when a Boolean becomes true\n\nFrom ETL", markdown);
    }

    // ─────────────────────────────────────────────────────────

    static List<CompletionItem> complete(String textWithCursor) {
        return complete(textWithCursor, CompletionEngine.ClientSupport.FULL);
    }

    static List<CompletionItem> complete(String textWithCursor, CompletionEngine.ClientSupport support) {
        return complete(textWithCursor, support, new HashMap<>());
    }

    static List<CompletionItem> complete(String textWithCursor, CompletionEngine.ClientSupport support,
                                         Map<String, String> documentation) {
        int cursor = textWithCursor.indexOf('|');
        String text = textWithCursor.substring(0, cursor) + textWithCursor.substring(cursor + 1);
        ParsedDocument document = ParsedDocument.parse(text);
        try {
            return ANALYZER.complete(URI, document, document.positions.positionOfOffset(cursor), support, documentation);
        } finally {
            ANALYZER.close(URI);
        }
    }

    /** The labels in the order the client lists them. */
    static List<String> labels(List<CompletionItem> items) {
        List<CompletionItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(i -> i.getSortText() == null ? i.getLabel() : i.getSortText()));
        return sorted.stream().map(CompletionItem::getLabel).collect(Collectors.toList());
    }

    static CompletionItem item(List<CompletionItem> items, String label) {
        return items.stream().filter(i -> i.getLabel().equals(label)).findFirst()
            .orElseThrow(() -> new AssertionError("no item " + label + " in " + labels(items)));
    }

    static TextEdit edit(CompletionItem item) {
        return item.getTextEdit().getLeft();
    }
}
