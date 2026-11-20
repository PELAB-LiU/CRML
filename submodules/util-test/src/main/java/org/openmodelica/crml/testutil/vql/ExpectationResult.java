package org.openmodelica.crml.testutil.vql;

/** The outcome of one {@code @Constraint} pattern of a VQL file, or an error in the file. */
public final class ExpectationResult {

    public enum Status {
        /** The constraint has no violation. */
        PASS,
        /** A constraint of severity error has violations; the detail lists the first ones. */
        FAIL,
        /** A constraint of severity warning or info has violations; does not fail a test. */
        WARN,
        /** The pattern, its annotation or the file has an error, or evaluating it failed. */
        ERROR;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** 1-based line of the pattern name (or of the error). */
    public final int line;
    /** The pattern name; {@code (file)} for an error outside every pattern. */
    public final String pattern;
    /** The severity of the constraint; {@code (helper)} for a pattern without {@code @Constraint}. */
    public final String severity;
    /** The pattern as written. */
    public final String source;
    public final Status status;
    public final String detail;

    ExpectationResult(int line, String pattern, String severity, String source, Status status, String detail) {
        this.line = line;
        this.pattern = pattern;
        this.severity = severity;
        this.source = source;
        this.status = status;
        this.detail = detail == null ? "" : detail;
    }

    /** True for {@link Status#FAIL} and {@link Status#ERROR}. */
    public boolean failed() {
        return status == Status.FAIL || status == Status.ERROR;
    }

    @Override
    public String toString() {
        return "line " + line + " " + pattern + " [" + status + "]" + (detail.isEmpty() ? "" : ": " + detail);
    }
}
