package crml.experiments;

import crml.compiler.omc.OMCUtil;
import crml.compiler.omc.OMGenerator;
import crml.language.util.Parser;
import crml.language.util.Parser.ParserResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.*;

/**
 * Classifies CRML files by the stage at which they first fail:
 *  SYNTAX      – the CRML parser reports errors
 *  COMPILATION – the CRML→Modelica compiler throws
 *  LOAD        – omc cannot load the generated Modelica file
 *  RESERVED    – reserved for future use
 *  CORRECT     – all stages pass
 */
public class LLMFileClassifier {

    public enum Classification {
        SYNTAX, COMPILATION, LOAD, RESERVED, CORRECT
    }

    public static final class Result {
        public final Path file;
        public final String llmName;
        public final Classification classification;
        public final String detail;

        public Result(Path file, String llmName, Classification classification, String detail) {
            this.file = file;
            this.llmName = llmName;
            this.classification = classification;
            this.detail = detail;
        }
    }

    public static Result classify(Path crmlFile, String llmName, Path outputDir) {
        // Stage 1: syntax — parse the raw file
        ParserResult result;
        try {
            result = new Parser().parse(crmlFile);
        } catch (Exception e) {
            return new Result(crmlFile, llmName, Classification.SYNTAX, e.getMessage());
        }
        if (result.syntax().hasErrors()) {
            String errors = result.syntax().errors().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("; "));
            return new Result(crmlFile, llmName, Classification.SYNTAX, errors);
        }

        // Stage 2: compilation — CRML → Modelica
        String modelicaCode;
        try {
            String preprocessed = CRMLLibraryLoader.preprocessModel(crmlFile);
            ParserResult prepResult = new Parser().parse(preprocessed);
            OMGenerator codegen = new OMGenerator(prepResult, false);
            modelicaCode = codegen.getModelicaCode(null);
        } catch (Exception e) {
            return new Result(crmlFile, llmName, Classification.COMPILATION, e.getMessage());
        }

        // Stage 3: load — omc loads the generated Modelica
        String baseName = crmlFile.getFileName().toString().replaceFirst("\\.crml$", "");
        Path moFile = outputDir.resolve(baseName + ".mo");
        Path mosFile = outputDir.resolve(baseName + ".mos");
        try {
            Files.createDirectories(outputDir);
            Files.write(moFile, modelicaCode.getBytes(StandardCharsets.UTF_8));
            String unixMoPath = moFile.toAbsolutePath().toString().replace("\\", "/");
            String mosScript = "loadFile(\"" + unixMoPath + "\");\ngetErrorString();\n";
            Files.write(mosFile, mosScript.getBytes(StandardCharsets.UTF_8));

            String omc = OMCUtil.locateOMC();
            ProcessBuilder pb = new ProcessBuilder(omc, mosFile.toAbsolutePath().toString());
            pb.directory(outputDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String omcOutput = OMCUtil.checkInputStream(process.getInputStream());
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                return new Result(crmlFile, llmName, Classification.LOAD, "omc timed out after 30 s");
            }
            if (omcOutput.contains("false")) {
                return new Result(crmlFile, llmName, Classification.LOAD, omcOutput.trim());
            }
        } catch (InterruptedException | IOException e) {
            return new Result(crmlFile, llmName, Classification.LOAD, e.getMessage());
        }

        return new Result(crmlFile, llmName, Classification.CORRECT, "");
    }

    public static void main(String[] args) throws IOException {
        Path generatedDir = args.length > 0
                ? Paths.get(args[0])
                : Paths.get(System.getProperty("llm.generated.dir", "../../LLM/generated"));
        Path outputBase = generatedDir;

        List<Path> crmlFiles;
        try (Stream<Path> walk = Files.walk(generatedDir)) {
            crmlFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".crml"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        System.out.println("Classifying " + crmlFiles.size() + " CRML files in: " + generatedDir);
        System.out.println();

        Map<Classification, List<Result>> grouped = new LinkedHashMap<>();
        for (Classification c : Classification.values()) grouped.put(c, new ArrayList<>());

        StringBuilder csv = new StringBuilder("file,llm,classification,detail\n");

        for (Path file : crmlFiles) {
            String llmName = file.getParent().getFileName().toString();
            Path fileOutputDir = outputBase.resolve(llmName);
            Result r = classify(file, llmName, fileOutputDir);
            grouped.get(r.classification).add(r);
            System.out.printf("%-12s  [%s] %s%n", r.classification, llmName, file.getFileName());
            String detail = r.detail == null ? "" : r.detail.replace("\"", "\"\"");
            csv.append(String.format("\"%s\",\"%s\",%s,\"%s\"%n",
                    file.toAbsolutePath(), llmName, r.classification, detail));
        }

        System.out.println();
        System.out.println("=== Summary ===");
        int total = crmlFiles.size();
        for (Classification c : Classification.values()) {
            int count = grouped.get(c).size();
            System.out.printf("  %-12s : %3d  (%.0f%%)%n",
                    c, count, total == 0 ? 0.0 : 100.0 * count / total);
        }

        Files.createDirectories(outputBase);
        Path csvFile = outputBase.resolve("results.csv");
        Files.write(csvFile, csv.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println();
        System.out.println("Results written to: " + csvFile.toAbsolutePath());
    }
}
