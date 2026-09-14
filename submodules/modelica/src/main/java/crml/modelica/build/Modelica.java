package crml.modelica.build;

import java.util.Arrays;
import java.util.List;

import crml.model.modelica.ArrayConstructor;
import crml.model.modelica.ArrayDimension;
import crml.model.modelica.BinaryExpression;
import crml.model.modelica.BinaryOperatorKind;
import crml.model.modelica.BooleanLiteral;
import crml.model.modelica.Causality;
import crml.model.modelica.ClassDefinition;
import crml.model.modelica.ClassKind;
import crml.model.modelica.ComponentDeclaration;
import crml.model.modelica.ComponentReference;
import crml.model.modelica.Equation;
import crml.model.modelica.Expression;
import crml.model.modelica.ExtendsClause;
import crml.model.modelica.FunctionCall;
import crml.model.modelica.IfExpression;
import crml.model.modelica.IntegerLiteral;
import crml.model.modelica.ModelicaFactory;
import crml.model.modelica.ModificationElement;
import crml.model.modelica.ParenthesizedExpression;
import crml.model.modelica.Placeholder;
import crml.model.modelica.RealLiteral;
import crml.model.modelica.ReferencePart;
import crml.model.modelica.Statement;
import crml.model.modelica.StringLiteral;
import crml.model.modelica.UnaryExpression;
import crml.model.modelica.UnaryOperatorKind;
import crml.model.modelica.Visibility;

/**
 * Hand-written construction helpers for the Modelica object model. Everything
 * here is a thin wrapper over {@link ModelicaFactory}; the point is that a
 * transformation reads as Modelica rather than as EMF plumbing.
 */
public final class Modelica {

    private static final ModelicaFactory FACTORY = ModelicaFactory.eINSTANCE;

    private Modelica() {
    }

    // --- classes ------------------------------------------------------------

    public static ClassDefinition classDefinition(ClassKind kind, String name) {
        ClassDefinition cls = FACTORY.createClassDefinition();
        cls.setKind(kind);
        cls.setName(name);
        return cls;
    }

    public static ClassDefinition model(String name) {
        return classDefinition(ClassKind.MODEL, name);
    }

    public static ClassDefinition record(String name) {
        return classDefinition(ClassKind.RECORD, name);
    }

    public static ClassDefinition block(String name) {
        return classDefinition(ClassKind.BLOCK, name);
    }

    public static ClassDefinition function(String name) {
        return classDefinition(ClassKind.FUNCTION, name);
    }

    public static ClassDefinition packageDefinition(String name) {
        return classDefinition(ClassKind.PACKAGE, name);
    }

    public static ExtendsClause extendsClause(String typeName) {
        ExtendsClause clause = FACTORY.createExtendsClause();
        clause.setTypeName(typeName);
        return clause;
    }

    public static Placeholder placeholder(String message) {
        Placeholder placeholder = FACTORY.createPlaceholder();
        placeholder.setMessage(message);
        return placeholder;
    }

    // --- components ---------------------------------------------------------

    public static ComponentDeclaration component(String typeName, String name) {
        ComponentDeclaration component = FACTORY.createComponentDeclaration();
        component.setTypeName(typeName);
        component.setName(name);
        component.setVisibility(Visibility.PUBLIC);
        component.setCausality(Causality.NONE);
        return component;
    }

    public static ComponentDeclaration component(String typeName, String name, Expression binding) {
        ComponentDeclaration component = component(typeName, name);
        component.setBinding(binding);
        return component;
    }

    public static ComponentDeclaration input(String typeName, String name) {
        ComponentDeclaration component = component(typeName, name);
        component.setCausality(Causality.INPUT);
        return component;
    }

    public static ComponentDeclaration output(String typeName, String name) {
        ComponentDeclaration component = component(typeName, name);
        component.setCausality(Causality.OUTPUT);
        return component;
    }

    public static ModificationElement mod(String name, Expression value) {
        ModificationElement modification = FACTORY.createModificationElement();
        modification.setName(name);
        modification.setValue(value);
        return modification;
    }

    /** An array dimension of the given size; a null size prints as ":". */
    public static ArrayDimension dimension(Expression size) {
        ArrayDimension dimension = FACTORY.createArrayDimension();
        dimension.setSize(size);
        return dimension;
    }

    // --- equations and statements -------------------------------------------

    public static Equation eq(Expression lhs, Expression rhs) {
        Equation equation = FACTORY.createEquation();
        equation.setLhs(lhs);
        equation.setRhs(rhs);
        return equation;
    }

    public static Statement assign(ComponentReference target, Expression value) {
        Statement statement = FACTORY.createStatement();
        statement.setTarget(target);
        statement.setValue(value);
        return statement;
    }

    // --- expressions --------------------------------------------------------

    /** A dotted component reference: {@code ref("a.b.c")}. */
    public static ComponentReference ref(String dottedName) {
        ComponentReference reference = FACTORY.createComponentReference();
        for (String part : dottedName.split("\\.")) {
            reference.getParts().add(part(part));
        }
        return reference;
    }

    public static ReferencePart part(String name, Expression... subscripts) {
        ReferencePart part = FACTORY.createReferencePart();
        part.setName(name);
        part.getSubscripts().addAll(Arrays.asList(subscripts));
        return part;
    }

    public static FunctionCall call(String functionName, Expression... arguments) {
        FunctionCall call = FACTORY.createFunctionCall();
        call.setFunctionName(functionName);
        call.getArguments().addAll(Arrays.asList(arguments));
        return call;
    }

    public static FunctionCall call(String functionName, List<? extends Expression> arguments) {
        FunctionCall call = FACTORY.createFunctionCall();
        call.setFunctionName(functionName);
        call.getArguments().addAll(arguments);
        return call;
    }

    public static BinaryExpression binary(BinaryOperatorKind operator, Expression lhs, Expression rhs) {
        BinaryExpression expression = FACTORY.createBinaryExpression();
        expression.setOperator(operator);
        expression.setLhs(lhs);
        expression.setRhs(rhs);
        return expression;
    }

    public static UnaryExpression unary(UnaryOperatorKind operator, Expression operand) {
        UnaryExpression expression = FACTORY.createUnaryExpression();
        expression.setOperator(operator);
        expression.setOperand(operand);
        return expression;
    }

    public static IfExpression ifExpr(Expression condition, Expression thenExpression, Expression elseExpression) {
        IfExpression expression = FACTORY.createIfExpression();
        expression.setCondition(condition);
        expression.setThenExpression(thenExpression);
        expression.setElseExpression(elseExpression);
        return expression;
    }

    public static ArrayConstructor array(Expression... elements) {
        ArrayConstructor constructor = FACTORY.createArrayConstructor();
        constructor.getElements().addAll(Arrays.asList(elements));
        return constructor;
    }

    public static ArrayConstructor array(List<? extends Expression> elements) {
        ArrayConstructor constructor = FACTORY.createArrayConstructor();
        constructor.getElements().addAll(elements);
        return constructor;
    }

    /**
     * Parentheses the CRML author wrote. The printer parenthesises on its own
     * wherever precedence requires it, so do not use this defensively.
     */
    public static ParenthesizedExpression parens(Expression inner) {
        ParenthesizedExpression expression = FACTORY.createParenthesizedExpression();
        expression.setInner(inner);
        return expression;
    }

    public static IntegerLiteral integer(int value) {
        IntegerLiteral literal = FACTORY.createIntegerLiteral();
        literal.setValue(value);
        return literal;
    }

    public static RealLiteral real(double value) {
        RealLiteral literal = FACTORY.createRealLiteral();
        literal.setValue(value);
        return literal;
    }

    /** A Real literal that keeps the spelling it had in the CRML source. */
    public static RealLiteral real(String literalText) {
        RealLiteral literal = FACTORY.createRealLiteral();
        literal.setLiteral(literalText);
        return literal;
    }

    public static StringLiteral string(String value) {
        StringLiteral literal = FACTORY.createStringLiteral();
        literal.setValue(value);
        return literal;
    }

    public static BooleanLiteral bool(boolean value) {
        BooleanLiteral literal = FACTORY.createBooleanLiteral();
        literal.setValue(value);
        return literal;
    }
}
