package org.openmodelica.crml.lsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * The completion items at a cursor: the operators, values and types in scope
 * (from a {@link ScopeSnapshot}), the built-in expression forms, and the
 * keywords the grammar allows ({@link KeywordCompletion}).
 *
 * <p>An operator is inserted with a tab stop for each operand, filled with
 * the parameter's name. Where a value can start, the operators taking an
 * operand on their left come first, with a tab stop for it too; after a
 * value, the same operators are proposed without it. Items are ranked by
 * group, then by whether their type is the one declared for the value being
 * written, then the document's own declarations before those of libraries.
 */
final class CompletionEngine {

    /** What the client can do, from its capabilities. */
    static final class ClientSupport {
        static final ClientSupport FULL = new ClientSupport(true, true, true);

        final boolean snippets;
        final boolean labelDetails;
        final boolean markdown;

        ClientSupport(boolean snippets, boolean labelDetails, boolean markdown) {
            this.snippets = snippets;
            this.labelDetails = labelDetails;
            this.markdown = markdown;
        }
    }

    /** A model or library a document can depend on. */
    static final class Root {
        final String name;
        /** {@code model} or {@code library}. */
        final String kind;
        /** The comment at the top of its file; null if there is none. */
        final String documentation;

        Root(String name, String kind, String documentation) {
            this.name = name;
            this.kind = kind;
            this.documentation = documentation;
        }
    }

    /** The values of the Boolean type, proposed with the values in scope. */
    private static final List<String> BOOLEAN_VALUES = Arrays.asList("true", "false", "undecided", "undefined");

    /** A built-in expression made of several keywords, inserted whole. */
    private static final class Form {
        final String keyword;
        final String label;
        final String[] parts;

        /** @param parts the form as words and {@code $name} operands */
        Form(String keyword, String label, String... parts) {
            this.keyword = keyword;
            this.label = label;
            this.parts = parts;
        }
    }

    private static final List<Form> FORMS = Arrays.asList(
        new Form("if", "if … then … else …", "if", "$condition", "then", "$value", "else", "$otherwise"),
        new Form("integrate", "integrate … on …", "integrate", "$value", "on", "$period"),
        new Form("duration", "duration … on …", "duration", "$condition", "on", "$period"),
        new Form("trim", "trim … on …", "trim", "$value", "on", "$period"),
        new Form("when", "when … then …", "when", "$event", "then", "$value"),
        new Form("new", "new … …", "new", "$Type", "$value"));

    // Groups, in the order they are listed.
    private static final int LEFT_OPERAND_OPERATOR = 0;
    private static final int OPERATOR = 1;
    private static final int DECLARATION = 2;
    private static final int FORM = 3;
    private static final int KEYWORD = 4;

    private final ParsedDocument document;
    private final CompletionContext context;
    private final ClientSupport support;
    private final Range range;
    /** The documentation of the items, by the id in their data, for completionItem/resolve. */
    private final Map<String, String> documentation;
    private final List<CompletionItem> items = new ArrayList<>();

    private CompletionEngine(ParsedDocument document, CompletionContext context, ClientSupport support,
                             Map<String, String> documentation) {
        this.document = document;
        this.context = context;
        this.support = support;
        this.range = context.range(document);
        this.documentation = documentation;
    }

    /**
     * The items at {@code position}, given what is in scope there.
     *
     * @param documentation receives the documentation of the items, by the id in their data
     */
    static List<CompletionItem> complete(ParsedDocument document, Position position, ScopeSnapshot scope,
                                         ClientSupport support, Map<String, String> documentation) {
        return complete(document, position, scope, java.util.Collections.<Root>emptyList(), support, documentation);
    }

    /**
     * As {@link #complete(ParsedDocument, Position, ScopeSnapshot, ClientSupport, Map)},
     * with the models and libraries the document can depend on.
     */
    static List<CompletionItem> complete(ParsedDocument document, Position position, ScopeSnapshot scope,
                                         List<Root> roots, ClientSupport support, Map<String, String> documentation) {
        CompletionContext context = CompletionContext.at(document, position);
        CompletionEngine engine = new CompletionEngine(document, context, support, documentation);
        engine.collect(scope, roots);
        return engine.items;
    }

    private void collect(ScopeSnapshot scope, List<Root> roots) {
        Set<String> covered = new HashSet<>();
        switch (context.place) {
            case VALUE:
                for (ScopeSnapshot.OperatorInfo operator : scope.operators) {
                    operator(operator, operator.leftOperand() ? LEFT_OPERAND_OPERATOR : OPERATOR, false);
                }
                for (ScopeSnapshot.Declaration value : scope.values) {
                    declaration(value, CompletionItemKind.Variable);
                }
                for (String value : BOOLEAN_VALUES) {
                    if (expected(value)) {
                        constant(value, "Boolean");
                        covered.add(value);
                    }
                }
                for (Form form : FORMS) {
                    if (expected(form.keyword)) {
                        form(form);
                        covered.add(form.keyword);
                    }
                }
                break;
            case AFTER_VALUE:
                for (ScopeSnapshot.OperatorInfo operator : scope.operators) {
                    if (operator.leftOperand()) operator(operator, LEFT_OPERAND_OPERATOR, true);
                }
                break;
            case TYPE:
                for (ScopeSnapshot.Declaration type : scope.types) {
                    declaration(type, CompletionItemKind.Class);
                    covered.add(type.name);
                }
                break;
            case DEPENDENCY:
                for (Root root : roots) dependency(root, true);
                break;
            case FLATTENED:
                for (Root root : roots) dependency(root, false);
                break;
            case OTHER:
                break;
            case NONE:
                return;
        }
        if (!context.quoted()) keywords(covered);
    }

    private boolean expected(String keyword) {
        for (Map.Entry<Integer, String> word : Vocabulary.WORDS.entrySet()) {
            if (word.getValue().equals(keyword)) return context.expected.contains(word.getKey());
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Operators
    // ---------------------------------------------------------------------

    /**
     * @param afterValue the left operand is already written: the item starts
     *        at the operator's first keyword
     */
    private void operator(ScopeSnapshot.OperatorInfo operator, int group, boolean afterValue) {
        List<String> label = new ArrayList<>();
        List<String> snippet = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        List<String> keywords = new ArrayList<>();
        int stop = 0;
        for (int i = 0; i < operator.header.size(); i++) {
            ScopeSnapshot.Part part = operator.header.get(i);
            if (part.isKeyword()) {
                label.add(part.keyword);
                snippet.add(escape(part.keyword));
                plain.add(part.keyword);
                keywords.add(part.keyword);
            } else {
                label.add(part.name);
                if (afterValue && i == 0) continue;
                snippet.add("${" + (++stop) + ":" + escape(part.name) + "}");
                plain.add(part.name);
            }
        }
        CompletionItem item = new CompletionItem(String.join(" ", label));
        item.setKind(CompletionItemKind.Operator);
        String filter = String.join(" ", keywords);
        item.setFilterText(context.quoted() ? filter : filter.replace("'", ""));
        insert(item, String.join(" ", snippet), String.join(" ", plain));
        details(item, operator.resultType, operator.source);
        rank(item, group, operator.resultType, operator.source.own);
        document(item, "```crml\n" + operator.signature() + "\n```", operator.source);
        items.add(item);
    }

    // ---------------------------------------------------------------------
    // Values, types, built-in forms, keywords
    // ---------------------------------------------------------------------

    private void declaration(ScopeSnapshot.Declaration declaration, CompletionItemKind kind) {
        CompletionItem item = new CompletionItem(declaration.name);
        item.setKind(kind);
        item.setTextEdit(Either.forLeft(new TextEdit(range, declaration.name)));
        details(item, declaration.type, declaration.source);
        rank(item, DECLARATION, declaration.type, declaration.source.own);
        String signature = declaration.type == null ? "class " + declaration.name : declaration.type + " " + declaration.name;
        document(item, "```crml\n" + signature + "\n```", declaration.source);
        items.add(item);
    }

    private void constant(String value, String type) {
        CompletionItem item = new CompletionItem(value);
        item.setKind(CompletionItemKind.Constant);
        item.setTextEdit(Either.forLeft(new TextEdit(range, value)));
        if (support.labelDetails) {
            CompletionItemLabelDetails details = new CompletionItemLabelDetails();
            details.setDetail(": " + type);
            item.setLabelDetails(details);
        } else {
            item.setDetail(type);
        }
        rank(item, DECLARATION, type, true);
        items.add(item);
    }

    /**
     * A model or library to depend on.
     *
     * @param union with the {@code union} that must follow it: not in {@code flatten { … }}
     */
    private void dependency(Root root, boolean union) {
        CompletionItem item = new CompletionItem(root.name);
        item.setKind(CompletionItemKind.Module);
        item.setTextEdit(Either.forLeft(new TextEdit(range, union ? root.name + " union " : root.name)));
        if (support.labelDetails) {
            CompletionItemLabelDetails details = new CompletionItemLabelDetails();
            details.setDescription(root.kind);
            item.setLabelDetails(details);
        } else {
            item.setDetail(root.kind);
        }
        rank(item, DECLARATION, null, true);
        String id = String.valueOf(documentation.size());
        documentation.put(id, "```crml\n" + root.kind + " " + root.name + "\n```"
            + (root.documentation == null ? "" : "\n\n" + root.documentation));
        item.setData(id);
        items.add(item);
    }

    private void form(Form form) {
        List<String> snippet = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        int stop = 0;
        for (String part : form.parts) {
            if (part.startsWith("$")) {
                snippet.add("${" + (++stop) + ":" + part.substring(1) + "}");
                plain.add(part.substring(1));
            } else {
                snippet.add(part);
                plain.add(part);
            }
        }
        CompletionItem item = new CompletionItem(form.label);
        item.setKind(CompletionItemKind.Snippet);
        item.setFilterText(form.keyword);
        insert(item, String.join(" ", snippet), String.join(" ", plain));
        rank(item, FORM, null, true);
        items.add(item);
    }

    /** The keywords the grammar allows, but those an item of its own already proposes. */
    private void keywords(Set<String> covered) {
        for (CompletionItem keyword : KeywordCompletion.at(document, document.positions.positionOfOffset(context.end))) {
            if (covered.contains(keyword.getLabel())) continue;
            keyword.setTextEdit(Either.forLeft(new TextEdit(range, keyword.getLabel())));
            rank(keyword, KEYWORD, null, true);
            items.add(keyword);
        }
    }

    // ---------------------------------------------------------------------

    private void insert(CompletionItem item, String snippet, String plain) {
        if (support.snippets) {
            item.setInsertTextFormat(InsertTextFormat.Snippet);
            item.setTextEdit(Either.forLeft(new TextEdit(range, snippet)));
        } else {
            item.setInsertTextFormat(InsertTextFormat.PlainText);
            item.setTextEdit(Either.forLeft(new TextEdit(range, plain)));
        }
    }

    /** The type after the label and where it is declared, or both in the detail if the client has no label details. */
    private void details(CompletionItem item, String type, ScopeSnapshot.Source source) {
        String from = source.own ? null : source.root;
        if (support.labelDetails) {
            CompletionItemLabelDetails details = new CompletionItemLabelDetails();
            details.setDetail(type == null ? null : ": " + type);
            details.setDescription(from);
            item.setLabelDetails(details);
        } else {
            String detail = (type == null ? "" : type) + (from == null ? "" : (type == null ? "" : " · ") + from);
            if (!detail.isEmpty()) item.setDetail(detail);
        }
    }

    /**
     * Sorts by group, then puts an item of another type than the one
     * declared after those of that type, then the document's own items
     * before those of libraries, then by label.
     */
    private void rank(CompletionItem item, int group, String type, boolean own) {
        boolean mismatch = context.expectedType != null && type != null && !context.expectedType.equals(type);
        item.setSortText(group + (mismatch ? "1" : "0") + (own ? "0" : "1") + item.getLabel().toLowerCase());
    }

    /** Leaves the documentation for completionItem/resolve: the item carries its id. */
    private void document(CompletionItem item, String signature, ScopeSnapshot.Source source) {
        StringBuilder text = new StringBuilder(signature);
        if (source.documentation != null) text.append("\n\n").append(source.documentation);
        if (!source.own && source.root != null) text.append("\n\nFrom ").append(source.root);
        String id = String.valueOf(documentation.size());
        documentation.put(id, text.toString());
        item.setData(id);
    }

    /** The documentation as the client can show it. */
    static Either<String, MarkupContent> documentation(String markdown, ClientSupport support) {
        if (support.markdown) return Either.forRight(new MarkupContent(MarkupKind.MARKDOWN, markdown));
        return Either.forLeft(markdown.replace("```crml\n", "").replace("\n```", ""));
    }

    /** Escapes the characters that have a meaning in a snippet. */
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("$", "\\$").replace("}", "\\}");
    }
}
