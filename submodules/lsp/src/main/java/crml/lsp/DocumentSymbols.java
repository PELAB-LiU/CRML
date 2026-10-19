package crml.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolKind;

import crml.language.grammar.crmlParser.CategoryElementContext;
import crml.language.grammar.crmlParser.Class_defContext;
import crml.language.grammar.crmlParser.Class_var_defContext;
import crml.language.grammar.crmlParser.ClassElementContext;
import crml.language.grammar.crmlParser.DefinitionContext;
import crml.language.grammar.crmlParser.Element_defContext;
import crml.language.grammar.crmlParser.IdContext;
import crml.language.grammar.crmlParser.OperatorContext;
import crml.language.grammar.crmlParser.OperatorElementContext;
import crml.language.grammar.crmlParser.OperatorKeywordContext;
import crml.language.grammar.crmlParser.OperatorParameterContext;
import crml.language.grammar.crmlParser.TemplateContext;
import crml.language.grammar.crmlParser.TemplateElementContext;
import crml.language.grammar.crmlParser.TemplateKeywordContext;
import crml.language.grammar.crmlParser.TemplateParameterContext;
import crml.language.grammar.crmlParser.Type_defContext;
import crml.language.grammar.crmlParser.TypeElementContext;
import crml.language.grammar.crmlParser.Uninstantiated_defContext;
import crml.language.grammar.crmlParser.UninstantiatedElementContext;
import crml.language.grammar.crmlParser.Var_defContext;
import crml.language.grammar.crmlParser.VaraibleElementContext;

/**
 * The outline of a CRML document, read from the parse tree, so it also works
 * while the document has syntax errors. Elements whose name is missing are
 * left out.
 */
public final class DocumentSymbols {
    private final ParsedDocument document;

    private DocumentSymbols(ParsedDocument document) {
        this.document = document;
    }

    public static List<DocumentSymbol> of(ParsedDocument document) {
        List<DocumentSymbol> symbols = new ArrayList<>();
        DocumentSymbols outline = new DocumentSymbols(document);
        DefinitionContext definition = document.tree;
        String kind = definition.definition_type() == null ? "" : definition.definition_type().getText();
        DocumentSymbol root = outline.symbol(definition, definition.id(), null,
            "model".equals(kind) ? SymbolKind.Module : SymbolKind.Package, kind);
        if (root == null) return symbols;
        for (Element_defContext element : definition.element_def()) {
            outline.element(element, root.getChildren());
        }
        symbols.add(root);
        return symbols;
    }

    private void element(Element_defContext element, List<DocumentSymbol> into) {
        if (element instanceof ClassElementContext) {
            Class_defContext cls = ((ClassElementContext) element).class_def();
            DocumentSymbol symbol = symbol(cls, cls.id(), null, SymbolKind.Class, "class");
            if (symbol == null) return;
            for (Class_var_defContext member : cls.class_var_def()) {
                classMember(member, symbol.getChildren());
            }
            into.add(symbol);
        } else if (element instanceof TypeElementContext) {
            Type_defContext type = ((TypeElementContext) element).type_def();
            DocumentSymbol symbol = symbol(type, type.id().isEmpty() ? null : type.id(0), null, SymbolKind.Struct, "type");
            if (symbol == null) return;
            for (Class_var_defContext member : type.class_var_def()) {
                classMember(member, symbol.getChildren());
            }
            into.add(symbol);
        } else if (element instanceof VaraibleElementContext) {
            add(into, variable(((VaraibleElementContext) element).var_def(), false));
        } else if (element instanceof OperatorElementContext) {
            add(into, operator(((OperatorElementContext) element).operator()));
        } else if (element instanceof TemplateElementContext) {
            add(into, template(((TemplateElementContext) element).template()));
        } else if (element instanceof CategoryElementContext) {
            CategoryElementContext category = (CategoryElementContext) element;
            add(into, symbol(category.category(), category.category().id(), null, SymbolKind.Enum, "Category"));
        } else if (element instanceof UninstantiatedElementContext) {
            parameters(((UninstantiatedElementContext) element).uninstantiated_def(), into);
        }
    }

    private void classMember(Class_var_defContext member, List<DocumentSymbol> into) {
        if (member.var_def() != null) {
            add(into, variable(member.var_def(), true));
        } else if (member.uninstantiated_def() != null) {
            parameters(member.uninstantiated_def(), into);
        } else if (member.id() != null) {
            add(into, symbol(member, member.id(), null, SymbolKind.Field, "alias"));
        }
    }

    private DocumentSymbol variable(Var_defContext variable, boolean member) {
        SymbolKind kind = variable.cnst != null ? SymbolKind.Constant : member ? SymbolKind.Field : SymbolKind.Variable;
        return symbol(variable, variable.id(), null, kind, variable.type() == null ? null : variable.type().getText());
    }

    private void parameters(Uninstantiated_defContext parameters, List<DocumentSymbol> into) {
        for (IdContext id : parameters.id()) {
            add(into, symbol(parameters, id, null, SymbolKind.TypeParameter, "parameter"));
        }
    }

    /** An operator, named by its call shape: 'during' x, x 'ensure' y. */
    private DocumentSymbol operator(OperatorContext operator) {
        if (operator.operator_def() == null) return null;
        String shape = operator.operator_def().args.stream()
            .map(arg -> arg instanceof OperatorKeywordContext
                ? ((OperatorKeywordContext) arg).kw.getText()
                : nameOf(((OperatorParameterContext) arg).name))
            .collect(Collectors.joining(" "));
        return symbol(operator, null, shape, SymbolKind.Operator,
            operator.domain == null ? "Operator" : "Operator [" + operator.domain.getText() + "]");
    }

    private DocumentSymbol template(TemplateContext template) {
        String shape = template.args.stream()
            .map(arg -> arg instanceof TemplateKeywordContext
                ? ((TemplateKeywordContext) arg).kw.getText()
                : nameOf(((TemplateParameterContext) arg).name))
            .collect(Collectors.joining(" "));
        return symbol(template, null, shape, SymbolKind.Function, "Template");
    }

    private static String nameOf(IdContext id) {
        return id == null || !present(id.getStart()) ? "?" : id.getText();
    }

    /**
     * A symbol spanning a parse tree node, selected at its name. The name
     * comes from the id, or from explicitName when the element has no single
     * identifier; then the whole element is the selection.
     */
    private DocumentSymbol symbol(ParserRuleContext node, IdContext id, String explicitName, SymbolKind kind, String detail) {
        Token start = node.getStart();
        Token stop = node.getStop() != null && node.getStop().getStopIndex() >= start.getStartIndex() ? node.getStop() : start;
        if (!present(start)) return null;
        int nodeStart = start.getStartIndex();
        int nodeEnd = stop.getStopIndex() + 1;

        String name;
        int selectionStart;
        int selectionEnd;
        if (explicitName != null) {
            if (explicitName.isBlank()) return null;
            name = explicitName;
            selectionStart = nodeStart;
            selectionEnd = nodeEnd;
        } else {
            if (id == null || !present(id.getStart())) return null;
            name = id.getText();
            selectionStart = id.getStart().getStartIndex();
            selectionEnd = id.getStart().getStopIndex() + 1;
        }

        DocumentSymbol symbol = new DocumentSymbol(name, kind,
            document.positions.range(nodeStart, Math.max(nodeEnd, selectionEnd)),
            document.positions.range(selectionStart, selectionEnd));
        symbol.setDetail(detail);
        symbol.setChildren(new ArrayList<>());
        return symbol;
    }

    /** False for tokens the parser invented while recovering from an error. */
    private static boolean present(Token token) {
        return token != null && token.getStartIndex() >= 0 && token.getType() != Token.EOF;
    }

    private static void add(List<DocumentSymbol> into, DocumentSymbol symbol) {
        if (symbol != null) into.add(symbol);
    }
}
