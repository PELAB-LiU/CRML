package crml.compiler.translation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the body of a CRML library from its source text.
 *
 * Recognised header form:
 *   library <name> is {
 *       ...body...
 *   };
 *
 * Usage:
 *   CRMLLibraryExtractor ex = CRMLLibraryExtractor.parse(source);
 *   ex.getLibraryName();  // "ETL"
 *   ex.getBody();         // content between the outermost braces
 */
public class CRMLLibraryExtractor {

    // Matches both:  library NAME is {
    //           and:  library NAME is PARENT union {
    // Uses .*? (DOTALL) to skip over leading comments before the keyword.
    private static final Pattern HEADER = Pattern.compile(
        ".*?library\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+is(?:\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+union)?\\s*\\{",
        Pattern.DOTALL
    );

    private final String libraryName;
    private final String parentName;   // null when no parent
    private final String body;

    private CRMLLibraryExtractor(String libraryName, String parentName, String body) {
        this.libraryName = libraryName;
        this.parentName  = parentName;
        this.body        = body;
    }

    /**
     * Parses {@code source} and returns an extractor holding the result.
     *
     * @throws IllegalArgumentException if no valid library header is found or braces are unbalanced
     */
    public static CRMLLibraryExtractor parse(String source) {
        Matcher m = HEADER.matcher(source);
        if (!m.find()) {
            throw new IllegalArgumentException("No 'library <name> is {' header found");
        }

        String name     = m.group(1);
        String parent   = m.group(2);  // null when the optional group did not match
        int    openPos  = m.end();   // index of the character right after '{'
        int    closePos = findMatchingClose(source, openPos);

        return new CRMLLibraryExtractor(name, parent, source.substring(openPos, closePos));
    }

    /** The library name as declared in the header (e.g. {@code "ETL"}). */
    public String getLibraryName() { return libraryName; }

    /** The parent library name, or {@code null} when this library has no parent. */
    public String getParentName() { return parentName; }

    /**
     * The raw text between the opening and closing braces of the library block,
     * exclusive of the braces themselves.
     */
    public String getBody() { return body; }

    // -------------------------------------------------------------------------

    /**
     * Walks {@code source} from {@code fromIndex} counting braces until the
     * one that matches the '{' at {@code fromIndex - 1} is found.
     *
     * @param fromIndex  position right after the opening '{'
     * @return           position of the matching '}'
     */
    private static int findMatchingClose(String source, int fromIndex) {
        int depth = 1;
        boolean inLineComment  = false;
        boolean inBlockComment = false;

        for (int i = fromIndex; i < source.length(); i++) {
            char c = source.charAt(i);

            // Track line comments: // ...
            if (!inBlockComment && c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                inLineComment = true;
            }
            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }

            // Track block comments: /* ... */
            if (!inLineComment && c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                inBlockComment = true;
                i++; // skip '*'
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++; // skip '/'
                }
                continue;
            }

            if      (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }

        throw new IllegalArgumentException("Unbalanced braces: no closing '}' found for library block");
    }
}
