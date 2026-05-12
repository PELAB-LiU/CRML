package ctests;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

@DisplayName("FORM-L library tests")
class FORMLLibraryTest extends ReportedTest {

    @Test
    @DisplayName("FORM-L.crml parses without syntax errors")
    void formlSyntax() throws IOException {
        Path path = SafeResource.get("FORM-L.crml");
        emit(path, "CRML model");

        Parser.ParserResult parsed = new Parser().parse(path);

        emit(CRMLSyntaxResultsWrapper.of(parsed.syntax()), "Syntax Errors");
        emit(parsed.toPrettyTree(), "AST");

        assertFalse(parsed.syntax().hasErrors(),
                "FORM-L.crml has syntax errors: " + parsed.syntax().errors());
    }

    @Test
    @DisplayName("FORM-L library exposes all expected period-construction operators")
    void formlPeriodOperators() {
        LibraryDefinition forml = LibraryRegistry.getLibrary("FORM_L");

        List<String> expected = Arrays.asList(
                // Single-boundary
                "'from'", "'after'", "'before'", "'until'", "'during'", "'when'",
                // Compound: after …
                "'afterbefore'",
                "'afteruntil'",
                "'afterfor'",
                "'afterwithin'",
                // Compound: from …
                "'frombefore'",
                "'fromuntil'",
                "'fromfor'",
                "'fromwithin'"
        );

        emit(OperatorStatusReporter.ofOperators(forml, expected), "Operator Status");

        for (String op : expected)
            assertTrue(forml.operators().containsKey(op), "FORM-L missing operator: " + op);
    }

    @Test
    @DisplayName("FORM-L library exposes all expected requirement-checking operators")
    void formlRequirementOperators() {
        LibraryDefinition forml = LibraryRegistry.getLibrary("FORM_L");

        List<String> expected = Arrays.asList(
                // Boolean outcome operators
                "'check at end'",
                "'check anytime'",
                "'ensure'",
                // Count-based operators
                "'check count<'",
                "'check count<='",
                "'check count>'",
                "'check count>='",
                "'check count=='",
                "'check count<>'",
                // Duration-based operators
                "'check duration<'",
                "'check duration<='",
                "'check duration>'",
                "'check duration>='"
        );

        emit(OperatorStatusReporter.ofOperators(forml, expected), "Operator Status");

        for (String op : expected)
            assertTrue(forml.operators().containsKey(op), "FORM-L missing operator: " + op);
    }
}
