package crml.experiments;

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
import crml.language.util.Parser;
import crml.language.util.Parser.ParserResult;
import crml.util.IOUtil;
import crml.util.SafeResource;

public class TwoTanksExperimentTest {

    static final Path OUTPUT_DIR = Paths.get("build", "experiments", "fluid_storage");
    static final Path PKG_DIR = OUTPUT_DIR.resolve("fluid_storage");

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
    void exportCrmlLibrary() throws IOException {
        Path library = SafeResource.get("modelica_libraries/CRMLtoModelica.mo");
        Files.copy(library, OUTPUT_DIR.resolve("CRMLtoModelica.mo"), StandardCopyOption.REPLACE_EXISTING);

        assertTrue(Files.exists(OUTPUT_DIR.resolve("CRMLtoModelica.mo")));
    }

    @Test
    void exportPackageDeclaration() throws IOException {
        Path packageFile = SafeResource.get("models/fluid_storage/package.mo");
        Files.copy(packageFile, PKG_DIR.resolve("package.mo"), StandardCopyOption.REPLACE_EXISTING);

        assertTrue(Files.exists(PKG_DIR.resolve("package.mo")));
    }

    @Test
    void compileRequirements() throws Exception {
        Path crmlFile = SafeResource.get("models/fluid_storage/fluid_storage.crml");
        String fileName = IOUtil.strip(crmlFile,".crml");

        ParserResult result = new Parser().parse(crmlFile);
        OMGenerator code = new OMGenerator(result, false);

        
        IOUtil.write(PKG_DIR.resolve(code.filename()), code.getModelicaCode(fileName));

        assertTrue(Files.exists(PKG_DIR.resolve(code.filename())));
    }

    @Test
    void exportPhysicalSystem() throws IOException {
        Path systemModel = SafeResource.get("models/fluid_storage/FluidStorageSystem.mo");
        Files.copy(systemModel, PKG_DIR.resolve("FluidStorageSystem.mo"), StandardCopyOption.REPLACE_EXISTING);

        assertTrue(Files.exists(PKG_DIR.resolve("FluidStorageSystem.mo")));
    }

    @Test
    void exportBindings() throws IOException {
        Path bindingsModel = SafeResource.get("models/fluid_storage/Bindings.mo");
        Files.copy(bindingsModel, PKG_DIR.resolve("Bindings.mo"), StandardCopyOption.REPLACE_EXISTING);

        assertTrue(Files.exists(PKG_DIR.resolve("Bindings.mo")));
    }
}
