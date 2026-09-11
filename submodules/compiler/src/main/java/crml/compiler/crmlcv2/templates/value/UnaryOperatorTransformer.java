package crml.compiler.crmlcv2.templates.value;

import static crml.compiler.crmlcv2.templates.value.TypeCategories.isBooleanOrUnknown;
import static crml.compiler.crmlcv2.templates.value.TypeCategories.isNumericOrUnknown;
import static crml.compiler.crmlcv2.templates.value.TypeCategories.isPeriodOrUnknown;
import static crml.modelica.build.Modelica.call;
import static crml.modelica.build.Modelica.parens;
import static crml.modelica.build.Modelica.unary;

import crml.compiler.crmlcv2.Diagnostics;
import crml.compiler.crmlcv2.TransformationContext;
import crml.compiler.crmlcv2.UnsupportedConstruct;
import crml.compiler.crmlcv2.templates.ValueTransformer;
import crml.compiler.crmlcv2.util.TypeResolver;
import crml.model.language.BuiltinType;
import crml.model.language.BuiltinUnaryOperatorKind;
import crml.model.language.UnaryOperator;
import crml.model.modelica.Expression;
import crml.model.modelica.UnaryOperatorKind;

/**
 * CRML unary operators to Modelica expressions. Type dispatch ported unchanged
 * from the string generator; see {@link BinaryOperatorTransformer}.
 */
public final class UnaryOperatorTransformer {

    private UnaryOperatorTransformer() {
    }

    public static Expression transform(TransformationContext ctx, UnaryOperator op) {
        Expression operand = ValueTransformer.transform(ctx, op.getValue());
        BuiltinType t = TypeResolver.resolveBuiltin(op.getValue().getReturnType());
        BuiltinUnaryOperatorKind opType = op.getOptype();

        switch (opType) {
            case SUBEXPRESSION:
                // Parentheses the CRML author wrote. This is the only producer of
                // ParenthesizedExpression; everything else the printer decides.
                return parens(operand);
            case ADD:
                return transformSign(op, opType, operand, t, UnaryOperatorKind.PLUS);
            case SUB:
                return transformSign(op, opType, operand, t, UnaryOperatorKind.MINUS);
            case NOT:
                // Boolean4 is four-valued, so CRML's "not" is a library call, never
                // Modelica's own not.
                return transformNot(op, opType, operand, t);
            case SIN:
                return transformMath(op, opType, operand, t, "Modelica.Math.sin");
            case ASIN:
                return transformMath(op, opType, operand, t, "Modelica.Math.asin");
            case COS:
                return transformMath(op, opType, operand, t, "Modelica.Math.cos");
            case ACOS:
                return transformMath(op, opType, operand, t, "Modelica.Math.acos");
            case LOG:
                return transformMath(op, opType, operand, t, "Modelica.Math.log");
            case LOG10:
                return transformMath(op, opType, operand, t, "Modelica.Math.log10");
            case EXP_OP:
                return transformMath(op, opType, operand, t, "Modelica.Math.exp");
            case START:
                return transformPeriodEndpoint(op, opType, operand, t, "CRMLtoModelica.Functions.PStart");
            case END:
                return transformPeriodEndpoint(op, opType, operand, t, "CRMLtoModelica.Functions.PEnd");
            case CARD:
                // CRMLtoModelica.Blocks.CardClock is a block: it has to be
                // instantiated as a component and wired with equations, which is M4.
                throw new UnsupportedConstruct(Diagnostics.notYetImplemented("UnaryOperator." + opType,
                    "needs CRMLtoModelica.Blocks.CardClock instantiated as a component (M4)", op));
            case TICK:
                throw new UnsupportedConstruct(Diagnostics.notYetImplemented("UnaryOperator." + opType,
                    "needs CRMLtoModelica.Blocks.ClockTick instantiated as a component (M4)", op));
            default:
                throw new UnsupportedConstruct(Diagnostics.unsupported(opType,
                    "this operator kind is not produced by the current AST builders", op));
        }
    }

    private static Expression transformSign(UnaryOperator op, BuiltinUnaryOperatorKind opType,
            Expression operand, BuiltinType t, UnaryOperatorKind sign) {
        if (isNumericOrUnknown(t)) {
            return unary(sign, operand);
        }
        throw new UnsupportedConstruct(Diagnostics.incompatibleTypes(opType, t, op));
    }

    private static Expression transformNot(UnaryOperator op, BuiltinUnaryOperatorKind opType,
            Expression operand, BuiltinType t) {
        if (isBooleanOrUnknown(t)) {
            return call("CRMLtoModelica.Functions.not4", operand);
        }
        throw new UnsupportedConstruct(Diagnostics.incompatibleTypes(opType, t, op));
    }

    private static Expression transformMath(UnaryOperator op, BuiltinUnaryOperatorKind opType,
            Expression operand, BuiltinType t, String function) {
        if (isNumericOrUnknown(t)) {
            // Modelica.Math is the standard library, which CRMLtoModelica.mo does
            // not itself depend on: a generated model using these needs MSL on the
            // load path.
            return call(function, operand);
        }
        throw new UnsupportedConstruct(Diagnostics.incompatibleTypes(opType, t, op));
    }

    private static Expression transformPeriodEndpoint(UnaryOperator op, BuiltinUnaryOperatorKind opType,
            Expression operand, BuiltinType t, String function) {
        if (isPeriodOrUnknown(t)) {
            return call(function, operand);
        }
        throw new UnsupportedConstruct(Diagnostics.incompatibleTypes(opType, t, op));
    }
}
