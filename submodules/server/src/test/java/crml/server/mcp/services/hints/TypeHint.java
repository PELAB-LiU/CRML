package crml.server.mcp.services.hints;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import crml.language.util.Parser;
import crml.server.util.MarkdownExtractor;
import crml.test.ReportedTest;

public class TypeHint extends ReportedTest {

    @Test
    public void simulateTestFile() throws IOException {
        Path file = HintsRoot.ROOT.resolve("type_type.md");
        assumeTrue(Files.exists(file), "type_type.md is disabled — skipping");

        List<String> models = MarkdownExtractor.extractCrmlBlocks(file);
        for (String model : models) {
            Parser.ParserResult parsed = new Parser().parse(model);
            emit(parsed.syntax(), "Syntax Errors");
            emit(parsed.toPrettyTree(), "AST");
            assertFalse(parsed.syntax().hasErrors(), "Syntax errors in model: " + model);
        }
    }
}
