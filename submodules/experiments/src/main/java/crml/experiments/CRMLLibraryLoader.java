package crml.experiments;

import crml.compiler.translation.CRMLLibraryExtractor;
import crml.compiler.translation.CRMLModelModifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Preprocesses a CRML model by inlining all referenced library bodies before
 * parsing.  The compiler visitor does not implement library imports; this class
 * resolves them by reading the bundled crml-libs resources and injecting their
 * content directly into the model string.
 *
 * Usage:
 *   String flat = CRMLLibraryLoader.preprocessModel(crmlFile);
 *   ParserResult result = new Parser().parse(flat);
 */
public class CRMLLibraryLoader {

    public static String preprocessModel(Path modelFile) throws IOException {
        return preprocessModel(new String(Files.readAllBytes(modelFile), StandardCharsets.UTF_8));
    }

    public static String preprocessModel(String model) throws IOException {
        CRMLModelModifier modifier = new CRMLModelModifier();
        modifier.inject(model, "");  // discover referenced library names
        String payload = resolveLibraries(modifier.getFoundLibNames(), new LinkedHashSet<>());
        return modifier.inject(model, payload);
    }

    // -------------------------------------------------------------------------

    private static String resolveLibraries(List<String> names, Set<String> seen) throws IOException {
        StringBuilder bodies = new StringBuilder();
        for (String name : names) {
            if (!seen.add(name)) continue;
            String libSource = loadLibrarySource(name);
            CRMLLibraryExtractor ex = CRMLLibraryExtractor.parse(libSource);
            String parent = ex.getParentName();
            if (parent != null) {
                bodies.append(resolveLibraries(Collections.singletonList(parent), seen));
            }
            bodies.append(ex.getBody());
        }
        return bodies.toString();
    }

    /**
     * Loads library source from the crml-libs classpath resources.
     * Tries {@code <name>.crml} first, then {@code <name with _ replaced by ->.crml}
     * to handle the FORM_L / FORM-L naming mismatch.
     */
    private static String loadLibrarySource(String name) throws IOException {
        String[] candidates = {
            "crml-libs/" + name + ".crml",
            "crml-libs/" + name.replace('_', '-') + ".crml"
        };
        for (String resource : candidates) {
            try (InputStream is = CRMLLibraryLoader.class.getClassLoader().getResourceAsStream(resource)) {
                if (is != null) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] chunk = new byte[4096];
                    int n;
                    while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
                    return buf.toString(StandardCharsets.UTF_8.name());
                }
            }
        }
        throw new IOException("Library resource not found for '" + name + "' (tried: "
            + candidates[0] + ", " + candidates[1] + ")");
    }
}
