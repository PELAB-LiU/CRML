package crml.experiments;

import static j2html.TagCreator.iframe;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import crml.compiler.omc.OMGenerator;
import crml.test.ReportedTest;
import crml.util.IOUtil;
import crml.util.SafeResource;

import crml.language.util.Parser;
import crml.language.util.Parser.ParserResult;

public class SRIRef extends ReportedTest {

    static final Path OUTPUT_DIR = Paths.get("build", "experiments", "sri");
    static final Path PKG_DIR = OUTPUT_DIR.resolve("sri");

    @BeforeAll
    static void setup() throws IOException {
        if (Files.exists(OUTPUT_DIR)) {
            Files.walk(OUTPUT_DIR)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
        }
        Files.createDirectories(OUTPUT_DIR);
        Files.createDirectories(PKG_DIR);
    }

    @Test
    void run() throws Exception {
        Path library = SafeResource.get("modelica_libraries/CRMLtoModelica.mo");
        Files.copy(library, OUTPUT_DIR.resolve("CRMLtoModelica.mo"), StandardCopyOption.REPLACE_EXISTING);
        assertTrue(Files.exists(OUTPUT_DIR.resolve("CRMLtoModelica.mo")));

        Path crmlFile = SafeResource.get("models/sri/sri_ref.crml");
        String fileName = IOUtil.strip(crmlFile, ".crml");

        emit(crmlFile, "CRML file");

        ParserResult result = new Parser().parse(crmlFile);
        emit(result.toPrettyTree(), "AST");
       if(result.syntax().hasErrors()){
            emit(CRMLSyntaxResultsWrapper.of(result.syntax()), "Syntax Errors");
       }

        OMGenerator code = new OMGenerator(result, false);
        IOUtil.write(PKG_DIR.resolve(code.filename()), code.getModelicaCode(null));

        assertTrue(Files.exists(PKG_DIR.resolve(code.filename())));
    }
}
