package org.openmodelica.crml.cql.syntax;

import java.util.Arrays;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Builds syntax trees in tests, one static method per node, named after the text it
 * stands for: {@code path(name("M"), filter(...))} for {@code M/[...]}. Trees carry no
 * positions, so {@link EcoreUtil#equals(EObject, EObject)} compares them.
 */
public final class Trees {

    private static final SyntaxFactory F = SyntaxFactory.eINSTANCE;

    private Trees() {
    }

    // ── Validation ───────────────────────────────────────────────────────────

    /**
     * EMF's validation of {@code tree}: multiplicities (an {@code Or} has two
     * operands or more, a {@code Path} one step or more, ...) and required children.
     */
    public static Diagnostic validate(EObject tree) {
        BasicDiagnostic diagnostic = Diagnostician.INSTANCE.createDefaultDiagnostic(tree);
        Diagnostician.INSTANCE.validate(tree, diagnostic);
        return diagnostic;
    }

    // ── Files and declarations ───────────────────────────────────────────────

    public static CqlFile file(Statement... statements) {
        CqlFile file = F.createCqlFile();
        file.getStatements().addAll(Arrays.asList(statements));
        return file;
    }

    public static ResourceDecl resource(String uri, String name) {
        ResourceDecl decl = F.createResourceDecl();
        decl.setUri(uri);
        decl.setName(name);
        return decl;
    }

    public static LetDecl let(String name, SetExpr value) {
        LetDecl decl = F.createLetDecl();
        decl.setName(name);
        decl.setValue(value);
        return decl;
    }

    /** {@code source -[name]-> target := body}. */
    public static RelationDecl directed(SetExpr source, String name, SetExpr target, Pairs body) {
        return relation(false, source, name, false, target, body);
    }

    /** {@code source -[name]- target := body}. */
    public static RelationDecl bidirectional(SetExpr source, String name, SetExpr target, Pairs body) {
        return relation(false, source, name, true, target, body);
    }

    public static RelationDecl relation(boolean isDefault, SetExpr source, String name, boolean bidirectional,
            SetExpr target, Pairs body) {
        RelationDecl decl = F.createRelationDecl();
        decl.setDefault(isDefault);
        decl.setSource(source);
        decl.setName(name);
        decl.setBidirectional(bidirectional);
        decl.setTarget(target);
        decl.setBody(body);
        return decl;
    }

    /** {@code set/<source, target> filters...}. */
    public static Pairs pairs(SetExpr set, PairComponent source, PairComponent target, FilterStep... filters) {
        Pairs pairs = F.createPairs();
        pairs.setSet(set);
        pairs.setSource(source);
        pairs.setTarget(target);
        pairs.getFilters().addAll(Arrays.asList(filters));
        return pairs;
    }

    /** A pair component without {@code as}. */
    public static PairComponent component(SetExpr value) {
        return component(value, null);
    }

    /** {@code value as name}. */
    public static PairComponent component(SetExpr value, String name) {
        PairComponent component = F.createPairComponent();
        component.setValue(value);
        component.setName(name);
        return component;
    }

    public static Assertion assertion(Condition condition) {
        Assertion assertion = F.createAssertion();
        assertion.setCondition(condition);
        return assertion;
    }

    /** Adds {@code ! severity "message"} to {@code statement}; {@code message} may be null. */
    public static <S extends Statement> S companion(S statement, Severity severity, String message) {
        Companion companion = F.createCompanion();
        companion.setSeverity(severity);
        companion.setMessage(message);
        statement.setCompanion(companion);
        return statement;
    }

    // ── Conditions ───────────────────────────────────────────────────────────

    public static Or or(Condition... operands) {
        Or or = F.createOr();
        or.getOperands().addAll(Arrays.asList(operands));
        return or;
    }

    public static And and(Condition... operands) {
        And and = F.createAnd();
        and.getOperands().addAll(Arrays.asList(operands));
        return and;
    }

    public static Not not(Condition operand) {
        Not not = F.createNot();
        not.setOperand(operand);
        return not;
    }

    /** {@code T} as a clause. */
    public static TypedClause typed(TypeRef type) {
        return typed(type, null);
    }

    /** {@code T :: condition}. */
    public static TypedClause typed(TypeRef type, Condition condition) {
        TypedClause clause = F.createTypedClause();
        clause.setType(type);
        clause.setCondition(condition);
        return clause;
    }

    public static Quantified all(String variable, SetExpr range, Condition body) {
        return quantified(Quantifier.ALL, variable, range, body);
    }

    public static Quantified some(String variable, SetExpr range, Condition body) {
        return quantified(Quantifier.SOME, variable, range, body);
    }

    public static Quantified no(String variable, SetExpr range, Condition body) {
        return quantified(Quantifier.NO, variable, range, body);
    }

    public static Quantified quantified(Quantifier quantifier, String variable, SetExpr range, Condition body) {
        Quantified quantified = F.createQuantified();
        quantified.setQuantifier(quantifier);
        quantified.setVariable(variable);
        quantified.setRange(range);
        quantified.setBody(body);
        return quantified;
    }

    public static Comparison compare(SetExpr left, ComparisonOp op, SetExpr right) {
        Comparison comparison = F.createComparison();
        comparison.setLeft(left);
        comparison.setOp(op);
        comparison.setRight(right);
        return comparison;
    }

    public static IsType is(SetExpr set, TypeRef type) {
        IsType isType = F.createIsType();
        isType.setSet(set);
        isType.setType(type);
        return isType;
    }

    /** {@code no set}. */
    public static Emptiness no(SetExpr set) {
        return emptiness(EmptinessOp.NO, set);
    }

    /** {@code some set}. */
    public static Emptiness some(SetExpr set) {
        return emptiness(EmptinessOp.SOME, set);
    }

    public static Emptiness emptiness(EmptinessOp op, SetExpr set) {
        Emptiness emptiness = F.createEmptiness();
        emptiness.setOp(op);
        emptiness.setSet(set);
        return emptiness;
    }

    /** A set as a condition. */
    public static BooleanSet holds(SetExpr set) {
        BooleanSet booleanSet = F.createBooleanSet();
        booleanSet.setSet(set);
        return booleanSet;
    }

    /**
     * {@code source -[relation m:n]-> target}, or {@code -} when {@code bidirectional};
     * {@code relation} and {@code sourceRange} may be null.
     */
    public static LinkConstraint link(SetExpr source, String relation, Range sourceRange, Range targetRange,
            boolean bidirectional, SetExpr target) {
        LinkConstraint link = F.createLinkConstraint();
        link.setSource(source);
        link.setRelation(relation);
        link.setSourceRange(sourceRange);
        link.setTargetRange(targetRange);
        link.setBidirectional(bidirectional);
        link.setTarget(target);
        return link;
    }

    /** {@code low..high}, with -1 for {@code *}. */
    public static Range range(int low, int high) {
        Range range = F.createRange();
        range.setLow(low);
        range.setHigh(high);
        return range;
    }

    /** {@code k}. */
    public static Range exactly(int k) {
        return range(k, k);
    }

    public static TypeRef type(String name) {
        return type(null, name);
    }

    /** {@code packageName::name}; {@code packageName} may be null. */
    public static TypeRef type(String packageName, String name) {
        TypeRef type = F.createTypeRef();
        type.setPackageName(packageName);
        type.setName(name);
        return type;
    }

    // ── Sets ─────────────────────────────────────────────────────────────────

    public static SetOperation setOp(SetExpr left, SetOperator op, SetExpr right) {
        SetOperation operation = F.createSetOperation();
        operation.setLeft(left);
        operation.setOp(op);
        operation.setRight(right);
        return operation;
    }

    /** {@code head} followed by {@code steps}. */
    public static Path path(SetExpr head, Step... steps) {
        Path path = F.createPath();
        path.setHead(head);
        path.getSteps().addAll(Arrays.asList(steps));
        return path;
    }

    /** A path from the current element: {@code .f...}, {@code @x...}. */
    public static Path relative(Step... steps) {
        return path(null, steps);
    }

    public static NameRef name(String name) {
        NameRef ref = F.createNameRef();
        ref.setName(name);
        return ref;
    }

    public static Universe universe() {
        return F.createUniverse();
    }

    public static It it() {
        return F.createIt();
    }

    public static Count count(SetExpr set) {
        Count count = F.createCount();
        count.setSet(set);
        return count;
    }

    public static LiteralSet literals(Literal... values) {
        LiteralSet set = F.createLiteralSet();
        set.getValues().addAll(Arrays.asList(values));
        return set;
    }

    public static StringLiteral string(String value) {
        StringLiteral literal = F.createStringLiteral();
        literal.setValue(value);
        return literal;
    }

    public static IntLiteral integer(long value) {
        IntLiteral literal = F.createIntLiteral();
        literal.setValue(value);
        return literal;
    }

    public static RealLiteral real(double value) {
        RealLiteral literal = F.createRealLiteral();
        literal.setValue(value);
        return literal;
    }

    public static BooleanLiteral bool(boolean value) {
        BooleanLiteral literal = F.createBooleanLiteral();
        literal.setValue(value);
        return literal;
    }

    /** {@code enumName::literal}. */
    public static EnumLiteral enumLiteral(String enumName, String literal) {
        EnumLiteral enumLiteral = F.createEnumLiteral();
        enumLiteral.setEnumName(enumName);
        enumLiteral.setLiteral(literal);
        return enumLiteral;
    }

    /** {@code .name}. */
    public static Navigation nav(String name) {
        return navigation(name, false, false, false);
    }

    /** {@code .~name}. */
    public static Navigation inverse(String name) {
        return navigation(name, false, true, false);
    }

    /** {@code .name+}. */
    public static Navigation closure(String name) {
        return navigation(name, false, false, true);
    }

    /** {@code @name}. */
    public static Navigation builtin(String name) {
        return navigation(name, true, false, false);
    }

    public static Navigation navigation(String name, boolean builtin, boolean inverse, boolean transitive) {
        Navigation navigation = F.createNavigation();
        navigation.setName(name);
        navigation.setBuiltin(builtin);
        navigation.setInverse(inverse);
        navigation.setTransitive(transitive);
        return navigation;
    }

    /** {@code /[condition]}. */
    public static FilterStep filter(Condition condition) {
        FilterStep step = F.createFilterStep();
        step.setCondition(condition);
        return step;
    }
}
