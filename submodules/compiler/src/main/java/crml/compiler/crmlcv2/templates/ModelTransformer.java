package crml.compiler.crmlcv2.templates;

import crml.compiler.crmlcv2.Diagnostics;
import crml.compiler.crmlcv2.TransformationContext;
import crml.compiler.crmlcv2.UnsupportedConstruct;
import crml.model.language.CustomOperator;
import crml.model.language.Model;
import crml.model.language.Operator;
import crml.model.language.Variable;
import crml.model.modelica.ClassDefinition;
import crml.modelica.build.Modelica;

/**
 * A CRML {@code Model} becomes one Modelica {@code model} class.
 *
 * <p>The class is {@code partial} whenever any of its variables is unbound: the
 * verification harnesses in {@code verificationModels/**} all do
 * {@code extends <Name>;} and then bind those variables, and a Modelica class
 * with unbound components is not a legal standalone class. The string generator
 * never marked anything partial.
 */
public final class ModelTransformer {

    private ModelTransformer() {
    }

    public static TransformationContext transform(Model model) {
        ClassDefinition definition = Modelica.model(model.getName());
        definition.setPartial(Boolean.valueOf(hasUnboundVariable(model)));

        TransformationContext ctx = TransformationContext.of(definition);
        ctx.linkRoot(model, definition);

        // Before anything is generated, so no allocated name can collide with a
        // name the model itself uses.
        for (Variable variable : model.getVariables()) {
            ctx.reserveName(variable.getName());
        }
        for (crml.model.language.Set<?> set : model.getSets()) {
            ctx.reserveName(set.getName());
        }

        // Operators first: a variable's definition may call one, and every
        // operator the model declares is emitted whether or not it is called.
        for (Operator operator : model.getOperators()) {
            if (operator instanceof CustomOperator) {
                try {
                    OperatorTransformer.declare(ctx, (CustomOperator) operator);
                } catch (UnsupportedConstruct e) {
                    ctx.reportWithPlaceholder(e.diagnostic());
                }
            } else {
                ctx.report(Diagnostics.libraryGap("Operator." + operator.eClass().getName(),
                    operator.eClass().getName() + " has no Modelica mapping", operator));
            }
        }

        for (Variable variable : model.getVariables()) {
            VariableTransformer.transform(ctx, variable);
        }

        for (crml.model.language.Class clazz : model.getClasses()) {
            ctx.report(Diagnostics.notYetImplemented("Class",
                "class '" + clazz.getName() + "' becomes a nested model (M5)", clazz));
        }
        for (crml.model.language.Set<?> set : model.getSets()) {
            ctx.report(Diagnostics.notYetImplemented("Set",
                "set '" + set.getName() + "' becomes an array component (M5)", set));
        }
        for (crml.model.language.Object object : model.getObjects()) {
            ctx.report(Diagnostics.libraryGap("Object",
                "Object has no Modelica mapping and is never built by any DOM builder", object));
        }
        if (model.getFrame() != null) {
            ctx.report(Diagnostics.libraryGap("Model.frame",
                "a model frame has no Modelica mapping", model.getFrame()));
        }

        return ctx;
    }

    private static boolean hasUnboundVariable(Model model) {
        for (Variable variable : model.getVariables()) {
            if (VariableTransformer.isUnbound(variable)) {
                return true;
            }
        }
        return false;
    }
}
