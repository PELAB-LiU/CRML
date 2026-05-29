package crml.compiler.translation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Injects code into a CRML model string while stripping the dependency clause
 * from the header, turning e.g.:
 *   model X is FORM_L union { ... }; -> model X is { <injected> ... };
 *
 * This prevents the compiler visitor from also calling visitLibrary for the
 * same libraries, which would otherwise cause every library element to appear
 * twice in the generated Modelica output.
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

    /**
     * group(1) = "model X is" prefix, group(2) = library name.
     * model X is LIB union {
     */
    private static final Pattern SINGLE_LIB = Pattern.compile(
        "^(\\s*model" + SEP1 + IDENT + SEP1 + "is)" + SEP1 + "(" + IDENT + ")" + SEP1 + "union" + SEP0 + "\\{",
        Pattern.DOTALL
    );

    /**
     * group(1) = "model X is" prefix, group(2) = comma-separated lib list.
     * model X is flatten { LIB1 , LIB2 } union {
     */
    private static final Pattern FLATTEN_LIBS = Pattern.compile(
        "^(\\s*model" + SEP1 + IDENT + SEP1 + "is)" + SEP1 + "flatten" + SEP0 + "\\{([^}]*)" + "\\}" + SEP0 + "union" + SEP0 + "\\{",
        Pattern.DOTALL
    );

    /** Names of the libraries found in the most recent {@link #inject} call. */
    private List<String> foundLibNames = Collections.emptyList();

    /**
     * Strips the dependency clause from the model header, injects {@code code}
     * right after the opening brace, and returns the modified model string.
     *
     * Example: {@code model X is FORM_L union { ... };} becomes
     * {@code model X is { <code> ... };}
     *
     * @param model  the raw CRML model text
     * @param code   the snippet to insert
     * @return       modified model string with the dependency clause removed
     * @throws IllegalArgumentException if the header does not match either pattern
     */
    public String inject(String model, String code) {
        // Try flatten pattern first (it is more specific).
        Matcher m = FLATTEN_LIBS.matcher(model);
        if (m.find()) {
            foundLibNames = parseLibList(m.group(2));
            return m.group(1) + " {\n" + code + model.substring(m.end());
        }

        m = SINGLE_LIB.matcher(model);
        if (m.find()) {
            foundLibNames = Collections.singletonList(m.group(2));
            return m.group(1) + " {\n" + code + model.substring(m.end());
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
}
