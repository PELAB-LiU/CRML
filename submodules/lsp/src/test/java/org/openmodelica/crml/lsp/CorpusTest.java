package org.openmodelica.crml.lsp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

/** Runs every feature on every test model, as a crash test and to see what users would be told. */
class CorpusTest {
    static final Path TEST_MODELS = Paths.get("../test-resources/src/main/resources/testModels");

    static List<Path> models() throws IOException {
        try (Stream<Path> files = Files.walk(TEST_MODELS)) {
            return files.filter(f -> f.toString().endsWith(".crml")).sorted().collect(Collectors.toList());
        }
    }

    @Test
    void analysesEveryTestModel() throws IOException {
        List<Path> models = models();
        assertTrue(models.size() > 100, "expected the test corpus, found " + models.size());
        Map<String, Integer> outcomes = new TreeMap<>();
        Analyzer analyzer = new Analyzer();
        int completions = 0;
        for (Path model : models) {
            ParsedDocument document = ParsedDocument.parse(new String(Files.readAllBytes(model), StandardCharsets.UTF_8));
            String uri = model.toUri().toString();
            List<Diagnostic> diagnostics = analyzer.diagnose(uri, document);
            // Completion where values start, and at the end of the text.
            for (Token token : document.tokens) {
                if (token.getText().equals("is") || token.getText().equals("=")) {
                    analyzer.complete(uri, document, document.positions.position(token.getStopIndex() + 1),
                        CompletionEngine.ClientSupport.FULL, new HashMap<>());
                    completions++;
                }
            }
            analyzer.complete(uri, document, document.positions.position(document.text.codePointCount(0, document.text.length())),
                CompletionEngine.ClientSupport.FULL, new HashMap<>());
            analyzer.close(uri);
            DocumentSymbols.of(document);
            SemanticTokensProvider.of(document);
            KeywordCompletion.at(document, new Position(document.positions.lineCount() - 1, 0));

            String outcome = diagnostics.isEmpty() ? "clean"
                : !document.errors.isEmpty() ? "syntax errors"
                : firstLine(diagnostics.get(0).getMessage().getLeft());
            outcomes.merge(outcome, 1, Integer::sum);
        }
        outcomes.forEach((outcome, count) -> System.out.printf("%4d  %s%n", count, outcome));
        System.out.printf("%4d  completions%n", completions);
        assertFalse(outcomes.isEmpty());
    }

    private static String firstLine(String message) {
        String line = message.split("\\R", 2)[0];
        return line.length() > 110 ? line.substring(0, 110) + "..." : line;
    }
}
