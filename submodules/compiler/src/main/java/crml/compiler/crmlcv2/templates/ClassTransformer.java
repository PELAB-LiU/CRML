package crml.compiler.crmlcv2.templates;

import crml.compiler.crmlcv2.Diagnostic;
import crml.compiler.crmlcv2.Diagnostics;
import crml.compiler.crmlcv2.TransformationContext;
import crml.compiler.crmlcv2.UnsupportedConstruct;
import crml.compiler.crmlcv2.util.TypeResolver;
import crml.model.language.Variable;
import crml.model.modelica.ClassDefinition;
import crml.model.modelica.ComponentDeclaration;
import crml.model.modelica.Expression;
import crml.model.modelica.Variability;
import crml.modelica.build.Modelica;

/**
 * A CRML class becomes a nested Modelica {@code model}.
 *
 * <p>Not a {@code record}: a CRML class variable may carry an expression
 * ("Boolean nostart is (...)", "Requirement noStartProb is ..."), and those
 * become equations, which a Modelica record cannot hold. Where a class happens
 * to have only external variables a record would work, but mixing record and
 * model in one extends hierarchy is illegal in Modelica, so every class is a
 * model.
 *
 * <p>A CRML partial class - one that "cannot have any instances because [it is]
 * incompletely defined" - is a Modelica {@code partial} model.
 */
public final class ClassTransformer {

    private ClassTransformer() {
    }

    public static ClassDefinition transform(TransformationContext ctx, crml.model.language.Class clazz) {
        ClassDefinition definition = Modelica.model(clazz.getName());
        definition.setPartial(Boolean.valueOf(Boolean.TRUE.equals(clazz.getPartial())));

        for (crml.model.language.Class superClass : clazz.getSuperClasses()) {
            if (superClass.getName() == null) {
                ctx.report(Diagnostics.error("Class.superClasses",
                    "class '" + clazz.getName() + "' extends an unnamed class", clazz));
                continue;
            }
            definition.getExtendsClauses().add(Modelica.extendsClause(superClass.getName()));
        }

        TransformationContext body = ctx.nested(definition);
        for (Variable variable : clazz.getVariables()) {
            body.reserveName(variable.getName());
        }
        for (Variable variable : clazz.getVariables()) {
            declare(body, variable);
        }
        return definition;
    }

    /**
     * One component per class variable, and an equation for each variable that
     * has a definition. The definition becomes an equation rather than a binding
     * because that is what makes the enclosing class a model rather than a
     * record.
     */
    private static void declare(TransformationContext ctx, Variable variable) {
        String typeName;
        try {
            typeName = TypeResolver.resolve(variable.getDomain());
        } catch (RuntimeException e) {
            ctx.reportWithPlaceholder(Diagnostics.error("Class.variables",
                "cannot resolve the type of member '" + variable.getName() + "': " + e.getMessage(), variable));
            return;
        }

        Expression definition = null;
        if (variable.getDefinition() != null && !ValueTransformer.isDeclarationOnly(variable.getDefinition())) {
            try {
                definition = ValueTransformer.transform(ctx, variable.getDefinition());
            } catch (UnsupportedConstruct e) {
                Diagnostic cause = e.diagnostic();
                ctx.reportWithPlaceholder(new Diagnostic(cause.severity(), cause.constructKind(),
                    "member '" + variable.getName() + "' was dropped: " + cause.message(),
                    cause.crmlSource() == null ? variable : cause.crmlSource()));
                return;
            }
        }

        ComponentDeclaration component = Modelica.component(typeName, variable.getName());
        if (Boolean.TRUE.equals(variable.getConstant())) {
            component.setVariability(Variability.CONSTANT);
        }
        ctx.declare(component, variable);

        if (definition != null) {
            ctx.equate(Modelica.eq(Modelica.ref(variable.getName()), definition), variable);
        }
    }
}
