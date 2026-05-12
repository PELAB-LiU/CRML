package ctests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import crml.compiler.translation.LibraryDefinition;
import crml.compiler.translation.LibraryRegistry;
import crml.compiler.util.OperatorStatusReporter;
import crml.language.util.CRMLSyntaxResultsWrapper;
import crml.language.util.Parser;
import crml.test.ReportedTest;
import crml.util.SafeResource;

@DisplayName("ETL library tests")
class ETLLibraryTest extends ReportedTest {

    @Test
    @DisplayName("ETL.crml parses without syntax errors")
    void etlSyntax() throws IOException {
        Path path = SafeResource.get("ETL.crml");
        emit(path, "CRML model");

        Parser.ParserResult parsed = new Parser().parse(path);

        emit(CRMLSyntaxResultsWrapper.of(parsed.syntax()), "Syntax Errors");
        emit(parsed.toPrettyTree(), "AST");

        assertFalse(parsed.syntax().hasErrors(),
                "ETL.crml has syntax errors: " + parsed.syntax().errors());
    }

    @Test
    @DisplayName("ETL library exposes all expected operators")
    void etlOperators() {
        LibraryDefinition etl = LibraryRegistry.getLibrary("ETL");

        List<String> expected = Arrays.asList(
                // Boolean templates
                "'or'", "'xor'", "'implies'",
                // Clock operators
                "'inside'",
                "'countinside'",
                // Event / edge operators
                "'becomes true'",
                "'becomes false'",
                "'becomes true inside'",
                "'becomes false inside'",
                // Requirement evaluation
                "'decideover'",
                "'evaluateover'",
                "'checkover'",
                // Category helper operators
                "'id'", "'cte_false'", "'cte_true'"
        );

        emit(OperatorStatusReporter.ofOperators(etl, expected), "Operator Status");

        for (String op : expected)
            assertTrue(etl.operators().containsKey(op), "ETL missing operator: " + op);
    }

    @Test
    @DisplayName("ETL library exposes all expected categories")
    void etlCategories() {
        LibraryDefinition etl = LibraryRegistry.getLibrary("ETL");

        List<String> expected = Arrays.asList("increasing1", "increasing2", "varying1", "varying2");

        emit(OperatorStatusReporter.ofCategories(etl, expected), "Category Status");

        for (String cat : expected)
            assertNotNull(etl.categories().getCategory(cat),
                    "ETL missing category: " + cat);
    }
}
