package crml.server.mcp.services.hints;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HintsRoot {
    public static final Path ROOT;
    static {
		try {
			ROOT = Paths.get(Thread.currentThread().getContextClassLoader().getResource("llm_hints/_llm_docs_marker").toURI()).getParent();
		} catch (URISyntaxException e) {
			// Test configuration is expected to be correct, 
			// therefore, we just wrap potential errors in a runtime exception to hide it in code.
			throw new RuntimeException("Incorrect project setup. Resources not found.");
		}
	}
}
