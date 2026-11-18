package org.openmodelica.crml.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.openmodelica.crml.language.scope.BuiltIns;
import org.openmodelica.crml.language.scope.ScopeEntry;
import org.openmodelica.crml.language.scope.ScopeMode;
import org.openmodelica.crml.language.scope.Scopes;
import org.openmodelica.crml.language.workspace.SourceMap;
import org.openmodelica.crml.model.base.Namespace;
import org.openmodelica.crml.model.metamodel.BuiltinType;
import org.openmodelica.crml.model.metamodel.BuiltinTypeReference;
import org.openmodelica.crml.model.metamodel.CRMLPackage;
import org.openmodelica.crml.model.metamodel.CustomOperator;
import org.openmodelica.crml.model.metamodel.Keyword;
import org.openmodelica.crml.model.metamodel.NamedEntity;
import org.openmodelica.crml.model.metamodel.OperatorHeaderElement;
import org.openmodelica.crml.model.metamodel.Template;
import org.openmodelica.crml.model.metamodel.TypeReference;
import org.openmodelica.crml.model.metamodel.UserTypereference;
import org.openmodelica.crml.model.metamodel.Variable;

/**
 * What is visible at a point of a DOM, as plain data: the operators that can
 * be called in natural syntax, the values and the types. Plain data, so it can
 * be kept after the DOM is replaced, as the fallback for a text that builds no
 * DOM at all.
 */
final class ScopeSnapshot {

    static final ScopeSnapshot EMPTY = new ScopeSnapshot(
        Collections.<OperatorInfo>emptyList(), Collections.<Declaration>emptyList(), Collections.<Declaration>emptyList());

    /** One word or parameter of an operator's header. */
    static final class Part {
        /** The keyword with its quotes, e.g. {@code 'over'}; null for a parameter. */
        final String keyword;
        /** The parameter's name and type; null for a keyword. */
        final String name;
        final String type;

        private Part(String keyword, String name, String type) {
            this.keyword = keyword;
            this.name = name;
            this.type = type;
        }

        boolean isKeyword() {
            return keyword != null;
        }
    }

    /** An operator that can be called in natural syntax. */
    static final class OperatorInfo {
        final List<Part> header;
        final String resultType;
        final boolean template;
        final Source source;

        OperatorInfo(List<Part> header, String resultType, boolean template, Source source) {
            this.header = header;
            this.resultType = resultType;
            this.template = template;
            this.source = source;
        }

        /** True if a call starts with an operand: an infix or postfix operator. */
        boolean leftOperand() {
            return !header.isEmpty() && !header.get(0).isKeyword();
        }

        /** The declaration as written: {@code Operator [ Boolean ] 'check' Boolean phi 'over' Periods P}. */
        String signature() {
            StringBuilder text = new StringBuilder(template ? "Template" : "Operator [ " + resultType + " ]");
            for (Part part : header) {
                text.append(' ');
                if (part.isKeyword()) text.append(part.keyword);
                else text.append(template || part.type == null ? "" : part.type + " ").append(part.name);
            }
            return text.toString();
        }
    }

    /** A value or a type. */
    static final class Declaration {
        final String name;
        /** The type of a value, e.g. {@code Boolean}; null for a type or if unknown. */
        final String type;
        final Source source;

        Declaration(String name, String type, Source source) {
            this.name = name;
            this.type = type;
            this.source = source;
        }
    }

    /** Where a declaration comes from. */
    static final class Source {
        /** The model or library declaring it. */
        final String root;
        /** True if it is declared in the document itself. */
        final boolean own;
        /** The comment documenting it; null if there is none. */
        final String documentation;

        Source(String root, boolean own, String documentation) {
            this.root = root;
            this.own = own;
            this.documentation = documentation;
        }
    }

    final List<OperatorInfo> operators;
    final List<Declaration> values;
    final List<Declaration> types;

    private ScopeSnapshot(List<OperatorInfo> operators, List<Declaration> values, List<Declaration> types) {
        this.operators = operators;
        this.values = values;
        this.types = types;
    }

    /**
     * What is visible at {@code context}: an object of the DOM, e.g. the
     * innermost one at the cursor. The declaration {@code context} is in (the
     * variable being defined, the operator whose body it is) is left out, as
     * it cannot refer to itself.
     */
    static ScopeSnapshot at(EObject context) {
        Scopes scopes = new Scopes();
        EObject root = EcoreUtil.getRootContainer(context);
        Namespace namespace = namespace(context);
        if (namespace == null) return EMPTY;
        CustomOperator enclosing = enclosingOperator(context);

        List<OperatorInfo> operators = new ArrayList<>();
        for (CustomOperator operator : scopes.operators(namespace)) {
            if (operator != enclosing) operators.add(operator(operator, root));
        }
        List<Declaration> values = new ArrayList<>();
        for (ScopeEntry entry : list(scopes, context, namespace, CRMLPackage.Literals.VARIABLE)) {
            Variable variable = (Variable) entry.element();
            values.add(new Declaration(variable.getName(), typeName(variable.getDomain()), source(variable, root)));
        }
        List<Declaration> types = new ArrayList<>();
        for (ScopeEntry entry : list(scopes, context, namespace, CRMLPackage.Literals.CLASS)) {
            types.add(new Declaration(entry.name(), null, source(entry.element(), root)));
        }
        return new ScopeSnapshot(Collections.unmodifiableList(operators), Collections.unmodifiableList(values),
            Collections.unmodifiableList(types));
    }

    /**
     * The declarations of a kind visible at {@code context}. A namespace (the
     * cursor is between its elements) sees its own members; any other object
     * those of the namespaces around it, without its own declaration.
     */
    private static List<ScopeEntry> list(Scopes scopes, EObject context, Namespace namespace,
                                         org.eclipse.emf.ecore.EClass kind) {
        List<ScopeEntry> entries = context == namespace
            ? scopes.list(namespace, kind, ScopeMode.RESOLUTION)
            : scopes.list(context, kind, ScopeMode.RESOLUTION);
        List<ScopeEntry> named = new ArrayList<>();
        for (ScopeEntry entry : entries) {
            if (entry.name() != null) named.add(entry);
        }
        return named;
    }

    /** {@code context} itself if it is a namespace, else the nearest one containing it. */
    private static Namespace namespace(EObject context) {
        for (EObject object = context; object != null; object = object.eContainer()) {
            if (object instanceof Namespace) return (Namespace) object;
        }
        return null;
    }

    private static CustomOperator enclosingOperator(EObject context) {
        for (EObject object = context; object != null; object = object.eContainer()) {
            if (object instanceof CustomOperator) return (CustomOperator) object;
        }
        return null;
    }

    private static OperatorInfo operator(CustomOperator operator, EObject root) {
        List<Part> header = new ArrayList<>();
        for (OperatorHeaderElement element : operator.getHeader()) {
            if (element instanceof Keyword) {
                header.add(new Part(((Keyword) element).getKeyword(), null, null));
            } else if (element instanceof Variable) {
                Variable parameter = (Variable) element;
                header.add(new Part(null, parameter.getName(), typeName(parameter.getDomain())));
            }
        }
        return new OperatorInfo(Collections.unmodifiableList(header), typeName(operator.getDomain()),
            operator instanceof Template, source(operator, root));
    }

    private static Source source(EObject declaration, EObject documentRoot) {
        EObject declaringRoot = EcoreUtil.getRootContainer(declaration);
        String rootName = declaringRoot instanceof NamedEntity ? ((NamedEntity) declaringRoot).getName() : null;
        SourceMap map = SourceMap.of(declaration);
        return new Source(rootName, declaringRoot == documentRoot, map == null ? null : map.documentation(declaration));
    }

    /** The name of a type as written, e.g. {@code Boolean} or a class name; null if unknown. */
    static String typeName(TypeReference type) {
        if (type instanceof BuiltinTypeReference) {
            BuiltinType builtin = ((BuiltinTypeReference) type).getBuiltinType();
            for (Map.Entry<String, BuiltinType> entry : BuiltIns.TYPES.entrySet()) {
                if (entry.getValue() == builtin) return entry.getKey();
            }
            return null;
        }
        if (type instanceof UserTypereference) {
            EObject domain = ((UserTypereference) type).getDomain();
            return domain instanceof NamedEntity ? ((NamedEntity) domain).getName() : null;
        }
        return null;
    }
}
