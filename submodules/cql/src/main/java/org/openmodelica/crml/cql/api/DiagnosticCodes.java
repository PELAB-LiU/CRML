package org.openmodelica.crml.cql.api;

/**
 * The codes of {@link Diagnostic}s. A code never changes once released; tests and the
 * conformance suite match on it instead of on the message.
 */
public final class DiagnosticCodes {

    // ── Lexical ──────────────────────────────────────────────────────────────

    /** A character that starts no token, e.g. {@code ?} or a lone {@code @}. */
    public static final String UNKNOWN_CHARACTER = "unknown-character";

    /** A string without its closing quote on the same line. */
    public static final String UNTERMINATED_STRING = "unterminated-string";

    /** A name in backquotes without its closing backquote on the same line. */
    public static final String UNTERMINATED_NAME = "unterminated-name";

    /** A name in backquotes with nothing between them. */
    public static final String EMPTY_NAME = "empty-name";

    /** A backslash in a string followed by anything but {@code "} or {@code \}. */
    public static final String INVALID_ESCAPE = "invalid-escape";

    private DiagnosticCodes() {
    }
}
