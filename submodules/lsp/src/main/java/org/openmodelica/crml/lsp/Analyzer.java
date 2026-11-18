package org.openmodelica.crml.lsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.openmodelica.crml.language.workspace.CrmlDiagnostic;
import org.openmodelica.crml.language.workspace.CrmlResource;
import org.openmodelica.crml.language.workspace.CrmlWorkspace;
import org.openmodelica.crml.language.workspace.RootHeader;
import org.openmodelica.crml.language.workspace.SourceMap;

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
    /** By document: what was visible in its root the last time it had a DOM. */
    private final Map<String, ScopeSnapshot> lastScopes = new HashMap<>();

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
        if (resource.getRoot() != null) lastScopes.put(uri, ScopeSnapshot.at(resource.getRoot()));
        return Diagnostics.of(workspace.validate(resource), document);
    }

    /**
     * The completion items at {@code position}. The document is analysed as
     * it is now: what is in scope comes from its DOM, partial if the text is
     * incomplete. If the text builds no DOM at all, what was in scope in its
     * root the last time it had one is used.
     *
     * @param documentation receives the documentation of the items, by the id in their data
     */
    public List<CompletionItem> complete(String uri, ParsedDocument document, Position position,
                                         CompletionEngine.ClientSupport support, Map<String, String> documentation) {
        CrmlResource resource = workspace.load(URI.createURI(uri), document.text);
        workspace.resolve();
        return CompletionEngine.complete(document, position, scopeAt(uri, resource, document, position),
            dependable(resource), support, documentation);
    }

    /**
     * The models and libraries the document of {@code resource} can depend
     * on: those of the workspace (the bundled libraries and the open
     * documents), but itself and those it already depends on; for a library,
     * only libraries.
     */
    private List<CompletionEngine.Root> dependable(CrmlResource resource) {
        RootHeader own = resource.getHeader();
        Set<String> excluded = new HashSet<>();
        if (own != null) {
            excluded.add(own.name());
            for (RootHeader.DependencyName dependency : own.dependencies()) excluded.add(dependency.name());
        }
        boolean library = own != null && !own.isModel();
        List<CompletionEngine.Root> roots = new ArrayList<>();
        for (String name : workspace.getIndex().names()) {
            if (name == null || excluded.contains(name)) continue;
            List<CrmlResource> declaring = workspace.getIndex().lookup(name).resources();
            if (declaring.isEmpty() || declaring.contains(resource)) continue;
            RootHeader header = declaring.get(0).getHeader();
            if (library && header.isModel()) continue;
            EObject root = declaring.get(0).getRoot();
            SourceMap map = declaring.get(0).getSourceMap();
            String documentation = root == null || map == null ? null : map.documentation(root);
            roots.add(new CompletionEngine.Root(name, header.kind(), documentation));
        }
        return roots;
    }

    private ScopeSnapshot scopeAt(String uri, CrmlResource resource, ParsedDocument document, Position position) {
        EObject root = resource.getRoot();
        if (root == null) return lastScopes.getOrDefault(uri, ScopeSnapshot.EMPTY);
        lastScopes.put(uri, ScopeSnapshot.at(root));
        // The innermost object at the last character before the cursor that
        // is not blank: what is being written, even if the parser gave up in
        // it before the cursor (Boolean b is |). Else at the cursor; else the root.
        SourceMap map = resource.getSourceMap();
        int offset = document.positions.offset(position);
        int before = offset;
        while (before > 0 && Character.isWhitespace(document.text.charAt(before - 1))) before--;
        EObject at = before > 0 ? map.objectAt(document.positions.codePointIndex(before - 1)) : null;
        if (at == null) at = map.objectAt(document.positions.codePointIndex(offset));
        return ScopeSnapshot.at(at == null ? root : at);
    }

    /** The object model of an analysed document, or null; for tests. */
    EObject root(String uri) {
        CrmlResource resource = resource(uri);
        return resource == null ? null : resource.getRoot();
    }

    /** Forgets a closed document: other documents can no longer depend on it. */
    public void close(String uri) {
        lastScopes.remove(uri);
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
