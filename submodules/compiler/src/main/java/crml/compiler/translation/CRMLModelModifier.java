package crml.compiler.translation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Injects code into a CRML model string right after the opening brace of the
 * top-level union block.
 *
 * Recognised header forms (whitespace/newlines between tokens are allowed):
 *   model <name> is <LIBNAME> union {
 *   model <name> is flatten { <LIBNAME> , <LIBNAME> [, ...] } union {
 */
public class CRMLModelModifier {

    private static final String IDENT = "[A-Za-z_][A-Za-z0-9_]*";

    /** One or more whitespace chars or // line comments. */
    private static final String SEP1 = "(?:\\s|//[^\\n]*\\n)+";
    /** Zero or more whitespace chars or // line comments. */
    private static final String SEP0 = "(?:\\s|//[^\\n]*\\n)*";

    /** Captures a single library name: model X is LIB union { */
    private static final Pattern SINGLE_LIB = Pattern.compile(
        "^\\s*model" + SEP1 + IDENT + SEP1 + "is" + SEP1 + "(" + IDENT + ")" + SEP1 + "union" + SEP0 + "\\{",
        Pattern.DOTALL
    );

    /**
     * Captures the comma-separated lib list inside flatten { ... }:
     * model X is flatten { LIB1 , LIB2 } union {
     */
    private static final Pattern FLATTEN_LIBS = Pattern.compile(
        "^\\s*model" + SEP1 + IDENT + SEP1 + "is" + SEP1 + "flatten" + SEP0 + "\\{([^}]*)" + "\\}" + SEP0 + "union" + SEP0 + "\\{",
        Pattern.DOTALL
    );

    /** Names of the libraries found in the most recent {@link #inject} call. */
    private List<String> foundLibNames = Collections.emptyList();

    /**
     * Injects {@code code} immediately after the opening {@code {} of the
     * top-level union block and returns the modified model string.
     *
     * @param model  the raw CRML model text
     * @param code   the snippet to insert (a trailing newline is added automatically)
     * @return       modified model string
     * @throws IllegalArgumentException if the header does not match either pattern
     */
    public String inject(String model, String code) {
        // Try flatten pattern first (it is more specific).
        Matcher m = FLATTEN_LIBS.matcher(model);
        if (m.find()) {
            foundLibNames = parseLibList(m.group(1));
            return insertAfter(model, m.end(), code);
        }

        m = SINGLE_LIB.matcher(model);
        if (m.find()) {
            foundLibNames = Collections.singletonList(m.group(1));
            return insertAfter(model, m.end(), code);
        }

        throw new IllegalArgumentException(
            "CRML model does not start with a recognised header " +
            "(expected 'model X is LIB union {' or 'model X is flatten { ... } union {')");
    }

    /**
     * Returns the library names found during the last {@link #inject} call.
     * The list is empty if {@code inject} has not been called yet.
     */
    public List<String> getFoundLibNames() {
        return Collections.unmodifiableList(foundLibNames);
    }

    // -------------------------------------------------------------------------

    private static List<String> parseLibList(String raw) {
        List<String> libs = new ArrayList<>();
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (!name.isEmpty()) {
                libs.add(name);
            }
        }
        return libs;
    }

    private static String insertAfter(String model, int pos, String code) {
        return model.substring(0, pos) + "\n" + code + model.substring(pos);
    }
}
