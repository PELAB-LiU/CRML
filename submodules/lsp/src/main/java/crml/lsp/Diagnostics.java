package crml.lsp;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.Token;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

import crml.language.dom.DOMVisitor;
import crml.language.dom.util.BuildResult;
import crml.language.opcall.MixfixParser;
import crml.model.language.Library;
import crml.model.language.Model;

/** Finds the problems in a CRML document. */
public final class Diagnostics {
    public static final String SOURCE = "crml";

    private Diagnostics() {
    }

    /**
     * Syntax errors; when there are none, the problem that stopped the object
     * model from being built, if any. The builder stops at its first problem
     * and does not say where it was, so that is reported on the name of the
     * model.
     */
    public static List<Diagnostic> of(ParsedDocument document) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ParsedDocument.SyntaxError error : document.errors) {
            diagnostics.add(new Diagnostic(document.positions.range(error.start, error.end), error.message,
                DiagnosticSeverity.Error, SOURCE));
        }
        if (diagnostics.isEmpty()) {
            Diagnostic buildProblem = buildModel(document);
            if (buildProblem != null) diagnostics.add(buildProblem);
        }
        return diagnostics;
    }

    /** Builds the object model as the compiler does; returns why that failed, or null. */
    private static Diagnostic buildModel(ParsedDocument document) {
        try {
            DOMVisitor visitor = new DOMVisitor();
            BuildResult result = visitor.build(document.tree);
            EObject root = result instanceof BuildResult.SingleBuildResult
                ? (EObject) ((BuildResult.SingleBuildResult<?>) result).result() : null;
            visitor.linker();
            if (root instanceof Model) {
                new MixfixParser((Model) root, visitor).perform((Model) root);
            } else if (root instanceof Library) {
                new MixfixParser((Library) root, visitor).perform((Library) root);
            }
            visitor.modify();
            return null;
        } catch (Throwable e) {
            // The builder reports problems in the model as plain RuntimeExceptions.
            // Anything else is a failure of the builder itself.
            if (e.getClass() == RuntimeException.class) {
                return new Diagnostic(nameRange(document), "The model could not be built: " + describe(e.getMessage()),
                    DiagnosticSeverity.Error, SOURCE);
            }
            return new Diagnostic(nameRange(document),
                "The model could not be checked: the model builder failed with " + e.getClass().getSimpleName()
                    + where(e) + ". This is likely a CRML compiler bug; the model may still be valid.",
                DiagnosticSeverity.Warning, SOURCE);
        }
    }

    private static final Pattern UNRESOLVED = Pattern.compile("^Linker task: '[^']*' to '([^']*)'", Pattern.MULTILINE);

    /** The builder's message, with linker failures reduced to the names that could not be resolved. */
    private static String describe(String message) {
        if (message == null) return "no reason given";
        if (message.startsWith("Unable to resolve tasks:")) {
            Set<String> names = new LinkedHashSet<>();
            Matcher matcher = UNRESOLVED.matcher(message);
            while (matcher.find()) names.add(matcher.group(1));
            if (!names.isEmpty()) return "cannot resolve " + String.join(", ", names);
        }
        return message.trim();
    }

    /** " at Class.java:line", the first CRML frame of a stack trace, for bug reports. */
    private static String where(Throwable e) {
        for (StackTraceElement frame : e.getStackTrace()) {
            if (frame.getClassName().startsWith("crml.")) return " at " + frame.getFileName() + ":" + frame.getLineNumber();
        }
        return "";
    }

    private static Range nameRange(ParsedDocument document) {
        if (document.tree.id() != null) {
            Token name = document.tree.id().getStart();
            return document.positions.range(name.getStartIndex(), name.getStopIndex() + 1);
        }
        return document.positions.range(0, 0);
    }
}
