package org.openmodelica.crml.lsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.lsp4j.Diagnostic;
import org.openmodelica.crml.language.workspace.CrmlDiagnostic;
import org.openmodelica.crml.language.workspace.CrmlResource;
import org.openmodelica.crml.language.workspace.CrmlWorkspace;

/**
 * Analyses CRML documents the way the compiler front end does, in one
 * {@link CrmlWorkspace} shared by the open documents and the bundled
 * libraries. Only the object model is built (parse, build, link, resolve
 * operator calls, validate); nothing is translated.
 *
 * <p>The libraries are loaded and resolved once. Each analysis reloads the
 * document under its URI, so a model sees the libraries, and the other open
 * documents, by their declared names. A document that others depend on is
 * not reanalysed when it changes: theirs is, the next time they change.
 *
 * <p>Not thread-safe: the server analyses on a single thread.
 */
public final class Analyzer {
    /** Where the bundled libraries are, relative to this class. */
    static final String LIBRARY_FOLDER = "libraries/";
    /** The bundled libraries, dependencies first. */
    static final List<String> LIBRARIES = Collections.unmodifiableList(List.of("ETL.crml", "FORML.crml"));
    /** The URI scheme of the bundled libraries, as diagnostics name them. */
    static final String LIBRARY_SCHEME = "crml-library";

    private final CrmlWorkspace workspace = new CrmlWorkspace();
    private final List<CrmlResource> libraries = new ArrayList<>();

    public Analyzer() {
        for (String name : LIBRARIES) {
            libraries.add(workspace.load(URI.createURI(LIBRARY_SCHEME + ":/" + name), read(name)));
        }
        workspace.resolve();
        // Documents are not told about these: they are the server's own.
        for (CrmlResource library : libraries) {
            for (Resource.Diagnostic problem : library.getErrors()) {
                System.err.println(CrmlDiagnostic.format(problem, CrmlDiagnostic.LocationStyle.FULL));
            }
        }
    }

    /** The bundled libraries, as loaded; their problems are not reported to documents. */
    public List<CrmlResource> libraries() {
        return Collections.unmodifiableList(libraries);
    }

    /**
     * The problems of a document: its syntax errors, or, if it has none, what
     * building, linking and validating its object model found.
     *
     * @param uri the document's URI, as the client names it
     */
    public List<Diagnostic> diagnose(String uri, ParsedDocument document) {
        // Loaded even with syntax errors, so documents depending on it are
        // told it is broken rather than missing.
        CrmlResource resource = workspace.load(URI.createURI(uri), document.text);
        if (!document.errors.isEmpty()) {
            return Diagnostics.syntax(document);
        }
        workspace.resolve();
        return Diagnostics.of(workspace.validate(resource), document);
    }

    /** The object model of an analysed document, or null; for tests. */
    EObject root(String uri) {
        CrmlResource resource = resource(uri);
        return resource == null ? null : resource.getRoot();
    }

    /** Forgets a closed document: other documents can no longer depend on it. */
    public void close(String uri) {
        CrmlResource resource = resource(uri);
        if (resource == null) return;
        workspace.getIndex().remove(resource);
        resource.unload();
        workspace.getResourceSet().getResources().remove(resource);
    }

    private CrmlResource resource(String uri) {
        URI key = URI.createURI(uri);
        for (CrmlResource resource : workspace.resources()) {
            if (key.equals(resource.getURI())) return resource;
        }
        return null;
    }

    private static String read(String name) {
        try (InputStream in = Analyzer.class.getResourceAsStream(LIBRARY_FOLDER + name)) {
            if (in == null) throw new IllegalStateException("The bundled library " + name + " is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
