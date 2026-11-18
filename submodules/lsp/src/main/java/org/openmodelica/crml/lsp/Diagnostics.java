package org.openmodelica.crml.lsp;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.Token;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.openmodelica.crml.language.workspace.CrmlDiagnostic;
import org.openmodelica.crml.language.workspace.CrmlDiagnostic.Kind;

/** Turns what the parser and the CRML front end found into LSP diagnostics. */
public final class Diagnostics {
    public static final String SOURCE = "crml";
    /** The first stack frames of the CRML front end, for bug reports. */
    private static final String CRML_PACKAGE = "org.openmodelica.crml.";

    private Diagnostics() {
    }

    /** The syntax errors of the document, at the offending characters. */
    public static List<Diagnostic> syntax(ParsedDocument document) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ParsedDocument.SyntaxError error : document.errors) {
            diagnostics.add(new Diagnostic(document.positions.range(error.start, error.end), error.message,
                DiagnosticSeverity.Error, SOURCE));
        }
        return diagnostics;
    }

    /**
     * The problems in the validation result of a document, without syntax
     * errors (see {@link #syntax}). Each is at the token it was reported at;
     * one without a position, like those of the validation rules, is at the
     * name of the model.
     */
    public static List<Diagnostic> of(org.eclipse.emf.common.util.Diagnostic validation, ParsedDocument document) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        collect(validation, document, diagnostics);
        return diagnostics;
    }

    private static void collect(org.eclipse.emf.common.util.Diagnostic diagnostic, ParsedDocument document,
                                List<Diagnostic> into) {
        if (!diagnostic.getChildren().isEmpty()) {
            for (org.eclipse.emf.common.util.Diagnostic child : diagnostic.getChildren()) collect(child, document, into);
            return;
        }
        if (diagnostic.getSeverity() == org.eclipse.emf.common.util.Diagnostic.OK) return;
        Diagnostic converted = convert(diagnostic, document);
        if (converted != null) into.add(converted);
    }

    private static Diagnostic convert(org.eclipse.emf.common.util.Diagnostic diagnostic, ParsedDocument document) {
        Object reported = diagnostic.getData().isEmpty() ? null : diagnostic.getData().get(0);
        if (!(reported instanceof Resource.Diagnostic)) {
            // A validation rule: about an object of the model, which keeps no position.
            return new Diagnostic(nameRange(document), diagnostic.getMessage(), severity(diagnostic), SOURCE);
        }
        Resource.Diagnostic problem = (Resource.Diagnostic) reported;
        Range range = at(document, problem.getLine(), problem.getColumn());
        if (!(problem instanceof CrmlDiagnostic)) {
            return new Diagnostic(range, problem.getMessage(), severity(diagnostic), SOURCE);
        }
        CrmlDiagnostic crml = (CrmlDiagnostic) problem;
        if (crml.getKind() == Kind.SYNTAX) return null; // reported by syntax(), with exact ranges
        Diagnostic converted;
        if (crml.getCause() != null) {
            // The front end failed with an exception rather than reporting a problem.
            Throwable cause = crml.getCause();
            converted = new Diagnostic(range,
                "The model could not be checked: " + crml.getMessage() + " (" + cause.getClass().getSimpleName()
                    + where(cause) + "). This is likely a CRML compiler bug; the model may still be valid.",
                DiagnosticSeverity.Warning, SOURCE);
        } else {
            converted = new Diagnostic(range, crml.getMessage(), severity(diagnostic), SOURCE);
        }
        converted.setCode(crml.getKind().name());
        return converted;
    }

    private static DiagnosticSeverity severity(org.eclipse.emf.common.util.Diagnostic diagnostic) {
        switch (diagnostic.getSeverity()) {
            case org.eclipse.emf.common.util.Diagnostic.ERROR:
            case org.eclipse.emf.common.util.Diagnostic.CANCEL:
                return DiagnosticSeverity.Error;
            case org.eclipse.emf.common.util.Diagnostic.WARNING:
                return DiagnosticSeverity.Warning;
            default:
                return DiagnosticSeverity.Information;
        }
    }

    /**
     * The token starting at an ANTLR line (1-based) and column (code points);
     * the name of the model if there is no position, an empty range if no
     * token starts there.
     */
    static Range at(ParsedDocument document, int line, int column) {
        if (line <= 0) return nameRange(document);
        for (Token token : document.tokens) {
            if (token.getLine() == line && token.getCharPositionInLine() == column
                && token.getChannel() == Token.DEFAULT_CHANNEL) {
                return document.positions.range(token.getStartIndex(), token.getStopIndex() + 1);
            }
        }
        Position position = new Position(Math.min(line - 1, document.positions.lineCount() - 1), column);
        return new Range(position, position);
    }

    /** " at Class.java:line", the first CRML frame of a stack trace. */
    private static String where(Throwable e) {
        for (StackTraceElement frame : e.getStackTrace()) {
            if (frame.getClassName().startsWith(CRML_PACKAGE)) return " at " + frame.getFileName() + ":" + frame.getLineNumber();
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
