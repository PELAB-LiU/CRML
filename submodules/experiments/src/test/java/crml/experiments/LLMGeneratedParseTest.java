package crml.experiments;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import crml.compiler.omc.OMCUtil;
import crml.compiler.omc.OMGenerator;
import crml.language.util.Parser;
import crml.language.util.Parser.ParserResult;
import crml.test.ReportedTest;

public class LLMGeneratedParseTest extends ReportedTest {

    static final Path GENERATED_DIR = Paths.get(
            System.getProperty("llm.generated.dir", "../../LLM/generated"));

    static List<Arguments> fileNameSource() throws IOException {
        try (Stream<Path> walk = Files.walk(GENERATED_DIR)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".crml"))
                    .sorted()
                    .map(p -> Arguments.of(p, p.getParent().getFileName().toString(), p.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    @ParameterizedTest(name = "[{1}] {2}")
    @MethodSource("fileNameSource")
    void parseFile(Path crmlFile, String llmName, String fileName) throws IOException {
        emit(crmlFile, "CRML file");

        ParserResult result = new Parser().parse(crmlFile);
        emit(result.toPrettyTree(), "AST");
        if (result.syntax().hasErrors()) {
            emit(CRMLSyntaxResultsWrapper.of(result.syntax()), "Syntax Errors");
        }

        assertFalse(result.syntax().hasErrors());

        // Compilation step: CRML -> Modelica
        Path outputDir = Paths.get("build", "experiments", "llm-generated", llmName);
        Files.createDirectories(outputDir);

        String modelicaCode = null;
        try {
            String preprocessed = CRMLLibraryLoader.preprocessModel(crmlFile);
            ParserResult prepResult = new Parser().parse(preprocessed);
            OMGenerator codegen = new OMGenerator(prepResult, false);
            modelicaCode = codegen.getModelicaCode(null);
            emit(modelicaCode, "Modelica Code");
        } catch (Exception e) {
            emit(e.toString(), "Compilation Error");
            fail("Compilation failed: " + e.getMessage());
        }

        // Modelica loading step: load generated .mo with omc
        if (modelicaCode != null) {
            String baseName = fileName.replaceFirst("\\.crml$", "");
            Path moFile = outputDir.resolve(baseName + ".mo");
            Files.write(moFile, modelicaCode.getBytes(StandardCharsets.UTF_8));

            Path mosFile = outputDir.resolve(baseName + ".mos");
            String unixMoPath = moFile.toAbsolutePath().toString().replace("\\", "/");
            String mosScript = "loadFile(\"" + unixMoPath + "\");\ngetErrorString();\n";
            Files.write(mosFile, mosScript.getBytes(StandardCharsets.UTF_8));

            try {
                String omc = OMCUtil.locateOMC();
                ProcessBuilder pb = new ProcessBuilder(omc, mosFile.toAbsolutePath().toString());
                pb.directory(outputDir.toFile());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String omcOutput = OMCUtil.checkInputStream(process.getInputStream());
                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                emit(omcOutput, "OMC Output");
                if (!finished) {
                    fail("omc timed out after 30 seconds");
                } else if (omcOutput.contains("false")) {
                    fail("omc failed to load Modelica file");
                }
            } catch (InterruptedException | IOException e) {
                emit(e.toString(), "OMC Error");
                fail("omc invocation failed: " + e.getMessage());
            }
        }
    }
}
