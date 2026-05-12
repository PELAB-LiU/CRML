package crml.compiler.translation;

import java.util.HashMap;

/**
 * Immutable snapshot of the operators and categories exported by a parsed CRML library.
 * Produced by LibraryRegistry and merged into a model's visitor on dependency resolution.
 */
public class LibraryDefinition {

    final HashMap<String, Signature> operators;
    final CategoryMapping categories;

    LibraryDefinition(HashMap<String, Signature> operators, CategoryMapping categories) {
        this.operators = new HashMap<>(operators);
        this.categories = categories;
    }

    public HashMap<String, Signature> operators() { return operators; }
    public CategoryMapping categories() { return categories; }
}
