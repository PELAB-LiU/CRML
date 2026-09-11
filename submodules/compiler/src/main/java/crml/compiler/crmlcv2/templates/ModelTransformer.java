package crml.compiler.crmlcv2.templates;

import crml.compiler.crmlcv2.Diagnostics;
import crml.compiler.crmlcv2.TransformationContext;
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

        for (Variable variable : model.getVariables()) {
            VariableTransformer.transform(ctx, variable);
        }

        // Reached but not yet mapped; each becomes real output in a later
        // milestone. Recorded without a placeholder so the generated text keeps
        // to what the previous generator produced.
        for (Operator operator : model.getOperators()) {
            ctx.report(Diagnostics.notYetImplemented("Operator",
                "operator '" + operator.getName() + "' becomes a nested function or block (M3)", operator));
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
