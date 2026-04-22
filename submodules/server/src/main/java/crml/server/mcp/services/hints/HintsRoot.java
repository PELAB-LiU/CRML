package crml.server.mcp.services.hints;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class HintsRoot {
    public static final Path ROOT;
    @SuppressWarnings("unused")
    private static FileSystem jarFs; // held open so jar-backed Paths remain valid

    static {
        try {
            URI uri = Thread.currentThread().getContextClassLoader()
                .getResource("llm_hints/_llm_docs_marker").toURI();
            if ("jar".equals(uri.getScheme())) {
                jarFs = FileSystems.newFileSystem(uri, Map.of());
                ROOT = jarFs.getPath("llm_hints");
            } else {
                ROOT = Paths.get(uri).getParent();
            }
        } catch (Exception e) {
            throw new RuntimeException("Incorrect project setup. Resources not found.", e);
        }
    }
}
