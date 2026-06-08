package crml.language.specification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.Test;

import crml.language.grammar.crmlBaseListener;
import crml.language.grammar.crmlParser;
import crml.language.util.Parser;

public class VariableDeclarationTest {
    private final Parser parser = new Parser();

    private Parser.ParserResult parse(String content) {
        return parser.parse("model Foo is {\n" + content + "\n};");
    }

    private crmlParser.Var_defContext findVarDef(Parser.ParserResult result) {
        crmlParser.Var_defContext[] found = {null};
        ParseTreeWalker.DEFAULT.walk(new crmlBaseListener() {
            @Override
            public void enterVar_def(crmlParser.Var_defContext ctx) {
                found[0] = ctx;
            }
        }, result.ast());
        return found[0];
    }

    @Test
    public void testCorrect_multiVariableDeclaration() {
        assertFalse(parse("Integer x1, x2;").syntax().hasErrors());
    }

    // --- external variable declarations ---

    @Test
    public void testCorrect_realExternalDeclaration() {
        Parser.ParserResult result = parse("Real T is external;");
        assertFalse(result.syntax().hasErrors());
        crmlParser.Var_defContext varDef = findVarDef(result);
        assertNotNull(varDef, "var_def not found in parse tree");
        assertNotNull(varDef.is_external, "is_external label should be set for 'Real T is external;'");
    }

    @Test
    public void testCorrect_integerExternalDeclaration() {
        Parser.ParserResult result = parse("Integer n is external;");
        assertFalse(result.syntax().hasErrors());
        assertNotNull(findVarDef(result).is_external, "is_external label should be set");
    }

    @Test
    public void testCorrect_booleanExternalDeclaration() {
        Parser.ParserResult result = parse("Boolean b is external;");
        assertFalse(result.syntax().hasErrors());
        assertNotNull(findVarDef(result).is_external, "is_external label should be set");
    }

    @Test
    public void testCorrect_fixedRealExternalDeclaration() {
        Parser.ParserResult result = parse("fixed Real T is external;");
        assertFalse(result.syntax().hasErrors());
        assertNotNull(findVarDef(result).is_external, "is_external label should be set");
    }

    @Test
    public void testFaulty_externalWithoutIs() {
        assertTrue(parse("Real T external;").syntax().hasErrors());
    }
}
