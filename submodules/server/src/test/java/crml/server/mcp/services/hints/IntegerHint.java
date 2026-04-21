package crml.server.mcp.services.hints;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import crml.language.util.Parser;
import crml.server.util.MarkdownExtractor;
import crml.test.BaseSpecificationTest;

public class IntegerHint extends BaseSpecificationTest {

    static List<String> fileNameSource() throws IOException {
        return MarkdownExtractor.extractCrmlBlocks(HintsRoot.ROOT.resolve("integer_type.md"));
    }

    @ParameterizedTest
    @MethodSource("fileNameSource")
    public void simulateTestFile(final String model) throws IOException {
        emit(model, "CRML model");

        var parsed = new Parser().parse(model);

        emit(parsed.syntax(), "Syntax Errors");
        emit(parsed.toPrettyTree(), "AST");
        assertFalse(parsed.syntax().hasErrors());
    }
}
