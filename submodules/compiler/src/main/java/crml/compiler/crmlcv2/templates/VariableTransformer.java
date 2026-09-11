package crml.compiler.crmlcv2.templates;

import crml.compiler.crmlcv2.Diagnostic;
import crml.compiler.crmlcv2.Diagnostics;
import crml.compiler.crmlcv2.TransformationContext;
import crml.compiler.crmlcv2.UnsupportedConstruct;
import crml.compiler.crmlcv2.util.TypeResolver;
import crml.model.language.Set;
import crml.model.language.Variable;
import crml.model.modelica.ComponentDeclaration;
import crml.model.modelica.Expression;
import crml.model.modelica.Variability;
import crml.modelica.build.Modelica;

/**
 * A CRML {@code Variable} becomes one Modelica component declaration, with the
 * variable's definition as its binding where it has one.
 *
 * <p>This is the declaration boundary: if the definition cannot be translated,
 * the whole declaration is dropped - an expression hole cannot produce valid
 * Modelica - and the gap is recorded once, naming this variable.
 */
public final class VariableTransformer {

    private VariableTransformer() {
    }

    public static void transform(TransformationContext ctx, Variable variable) {
        String typeName;
        try {
            typeName = TypeResolver.resolve(variable.getDomain());
        } catch (RuntimeException e) {
            ctx.reportWithPlaceholder(Diagnostics.error("Variable",
                "cannot resolve the type of variable '" + variable.getName() + "': " + e.getMessage(), variable));
            return;
        }

        Expression binding = null;
        if (variable.getDefinition() != null && !ValueTransformer.isDeclarationOnly(variable.getDefinition())) {
            try {
                binding = ValueTransformer.transform(ctx, variable.getDefinition());
            } catch (UnsupportedConstruct e) {
                Diagnostic cause = e.diagnostic();
                ctx.reportWithPlaceholder(new Diagnostic(cause.severity(), cause.constructKind(),
                    "variable '" + variable.getName() + "' was dropped: " + cause.message(),
                    cause.crmlSource() == null ? variable : cause.crmlSource()));
                return;
            }
        }

        ComponentDeclaration declaration = Modelica.component(typeName, variable.getName(), binding);
        if (Boolean.TRUE.equals(variable.getConstant())) {
            declaration.setVariability(Variability.CONSTANT);
        }
        if (variable.getDefinition() instanceof Set<?>) {
            // A set-valued variable is an array of its element type, which is not
            // its declared CRML domain: "Periods P3 is { P1, P2, Pn }" declares a
            // set of Periods, and its elements are Periods, not CRMLPeriods.
            Set<?> set = (Set<?>) variable.getDefinition();
            String elementTypeName = SetTransformer.elementTypeName(set);
            if (elementTypeName != null) {
                declaration.setTypeName(elementTypeName);
            }
            declaration.getArrayDimensions().add(Modelica.dimension(Modelica.integer(SetTransformer.sizeOf(set))));
        }
        ctx.declare(declaration, variable);
    }

    /**
     * True when the variable has no value bound to it, so a class containing it
     * cannot be instantiated on its own.
     */
    public static boolean isUnbound(Variable variable) {
        return variable.getDefinition() == null
            || ValueTransformer.isDeclarationOnly(variable.getDefinition());
    }
}
