package crml.language.hints;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import crml.language.util.BaseSpecificationTest;
import crml.language.util.MarkdownExtractor;

public class RealHint extends BaseSpecificationTest {
        
    static List<String> fileNameSource() throws IOException {
        return MarkdownExtractor.extractCrmlBlocks(RESOURCES.resolve("../../../../../../").normalize().resolve("documentation/llm_hints/real_type.md"));
    }
    
    @ParameterizedTest
    @MethodSource("fileNameSource")
    public void simulateTestFile(final String model) throws IOException {
        emit(model, "CRML model");

        var parsed = parse(model);
        
        emit(parsed.syntax(), "Syntax Errors");
        emit(parsed.toPrettyTree(), "AST");
        assertFalse(parsed.syntax().hasErrors());
    }
}
