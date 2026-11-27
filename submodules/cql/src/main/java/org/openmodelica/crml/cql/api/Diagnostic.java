package org.openmodelica.crml.cql.api;

import org.openmodelica.crml.cql.syntax.Span;

/**
 * A problem found in a CQL file: its severity, a stable code (one of
 * {@link DiagnosticCodes}) that tests match on, a message for people, and where it
 * is. Problems in the input are reported as diagnostics, never as exceptions
 * (QUERY_LANGUAGE_ARCHITECTURE.md, decision A7).
 */
public final class Diagnostic {

    /** How bad a diagnostic is. */
    public enum Severity {
        ERROR,
        WARNING,
        INFO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    private final Severity severity;
    private final String code;
    private final String message;
    private final Span span;

    public Diagnostic(Severity severity, String code, String message, Span span) {
        if (severity == null || code == null || message == null) {
            throw new IllegalArgumentException("severity, code and message are required");
        }
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.span = span;
    }

    public static Diagnostic error(String code, String message, Span span) {
        return new Diagnostic(Severity.ERROR, code, message, span);
    }

    public static Diagnostic warning(String code, String message, Span span) {
        return new Diagnostic(Severity.WARNING, code, message, span);
    }

    public Severity severity() {
        return severity;
    }

    /** The stable code, e.g. {@value DiagnosticCodes#UNKNOWN_CHARACTER}. */
    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    /** Where the problem is, or null for a problem of the file as a whole. */
    public Span span() {
        return span;
    }

    /** {@code line:column severity code: message}. */
    @Override
    public String toString() {
        return (span == null ? "" : span + " ") + severity + " " + code + ": " + message;
    }
}
