package crml.compiler.translation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import crml.language.util.Parser;
import crml.language.util.Parser.ParserResult;
import crml.util.SafeResource;

/**
 * Parse-once cache for CRML library definitions.
 *
 * On the first request for a named library the corresponding .crml resource is
 * located via SafeResource (works on both the local filesystem and inside a
 * jar), parsed, and visited in library-extraction mode to collect operator
 * signatures and category mappings.  The resulting LibraryDefinition is cached
 * and returned for every subsequent call without re-parsing.
 *
 * Resource lookup order for library name N:
 *   1. N.crml          (e.g. ETL    → ETL.crml)
 *   2. N-with-_→-.crml (e.g. FORM_L → FORM-L.crml)
 */
public class LibraryRegistry {

    private static final Logger logger = LogManager.getLogger();

    private static final Map<String, LibraryDefinition> cache = new HashMap<>();

    public static synchronized LibraryDefinition getLibrary(String name) {
        if (cache.containsKey(name))
            return cache.get(name);

        LibraryDefinition def = load(name);
        cache.put(name, def);
        return def;
    }

    private static LibraryDefinition load(String name) {
        Path resource = findResource(name);
        if (resource == null)
            throw new ParseCancellationException("Library not found on classpath: " + name);

        logger.trace("Loading library: " + name);
        try {
            ParserResult parsed = new Parser().parse(resource);

            crmlVisitorImpl visitor = new crmlVisitorImpl(parsed.parser(), false);
            visitor.visit(parsed.ast());

            LibraryDefinition def = new LibraryDefinition(
                    visitor.getUserOperators(),
                    visitor.getCategoryMap());

            logger.trace("Loaded library " + name + ": "
                    + def.operators.size() + " operator(s), "
                    + def.categories.size() + " category/categories");
            return def;

        } catch (IOException e) {
            throw new ParseCancellationException(
                    "I/O error loading library " + name + ": " + e.getMessage());
        }
    }

    /**
     * Locates the .crml resource for the given library name.
     * Returns null if no matching resource is found.
     */
    private static Path findResource(String name) {
        try {
            return SafeResource.get(name + ".crml");
        } catch (RuntimeException ignored) {
            // not found under exact name; fall through
        }
        // Try mapping underscores to hyphens: FORM_L → FORM-L.crml
        try {
            return SafeResource.get(name.replace("_", "-") + ".crml");
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
