package crml.compiler.crmlcv2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import crml.model.language.CustomOperator;
import crml.model.language.Model;
import crml.model.language.Set;
import crml.model.language.Value;
import crml.model.language.Variable;
import crml.model.modelica.ClassDefinition;
import crml.model.modelica.ClassKind;
import crml.model.modelica.ComponentDeclaration;
import crml.model.modelica.ComponentReference;
import crml.model.modelica.Equation;
import crml.model.modelica.EquationSection;
import crml.model.modelica.ModelicaElement;
import crml.model.modelica.ModelicaFactory;
import crml.model.modelica.Placeholder;
import crml.model.trace.TraceFactory;
import crml.model.trace.TraceLink;
import crml.model.trace.TraceLinkKind;
import crml.model.trace.TraceModel;
import crml.modelica.build.Modelica;

/**
 * The mutable state of one class being generated, and the only way to add to
 * it.
 *
 * <p>This replaces the {@code Scope} tree. {@code BlockScope} used to declare a
 * component and its wiring equations as a side effect of its constructor,
 * mutating a host {@code ModelScope}; that is the right idea - hoist a
 * component plus equations out of an expression - but it was invisible and
 * untraceable. Here the hoist is explicit, and because {@link #declare},
 * {@link #equate} and {@link #defineClass} are the only mutators, each of them
 * can record a trace link and the trace cannot be forgotten.
 */
public final class TransformationContext {

    /** State shared by every context of one translation. */
    private static final class Session {
        final TraceModel trace = TraceFactory.eINSTANCE.createTraceModel();
        final List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    }

    private final Session session;
    private final ClassDefinition target;
    /**
     * Name counters, scoped per target class and seeded deterministically. The
     * generator this replaces held a {@code static} allocator, so generated
     * names depended on how many models had been compiled earlier in the same
     * JVM.
     */
    private final Map<String, Integer> counters = new HashMap<String, Integer>();

    private TransformationContext(Session session, ClassDefinition target) {
        this.session = session;
        this.target = target;
    }

    /** Starts a fresh translation targeting {@code target}. */
    public static TransformationContext of(ClassDefinition target) {
        return new TransformationContext(new Session(), target);
    }

    public ClassDefinition target() {
        return target;
    }

    public TraceModel trace() {
        return session.trace;
    }

    public List<Diagnostic> diagnostics() {
        return session.diagnostics;
    }

    // --- the three mutators -------------------------------------------------

    /**
     * Adds a component to the target class and returns a reference to it, so an
     * expression that needed something hoisted can go on being an expression.
     */
    public ComponentReference declare(ComponentDeclaration declaration, EObject crmlSource) {
        target.getComponents().add(declaration);
        link(crmlSource, declaration, componentKind(crmlSource));
        return Modelica.ref(declaration.getName());
    }

    /** Adds an equation to the target class's equation section, creating it on first use. */
    public void equate(Equation equation, EObject crmlSource) {
        EquationSection section = target.getEquations();
        if (section == null) {
            section = ModelicaFactory.eINSTANCE.createEquationSection();
            target.setEquations(section);
        }
        section.getEquations().add(equation);
        link(crmlSource, equation, equationKind(crmlSource));
    }

    /** Nests a class inside the target class. */
    public ClassDefinition defineClass(ClassDefinition definition, EObject crmlSource) {
        target.getNestedClasses().add(definition);
        link(crmlSource, definition, classKind(crmlSource, definition));
        return definition;
    }

    // --- supporting operations ----------------------------------------------

    /** A name unique within the target class: {@code prefix1}, {@code prefix2}, ... */
    public String allocateName(String prefix) {
        Integer previous = counters.get(prefix);
        int next = previous == null ? 1 : previous + 1;
        counters.put(prefix, next);
        return prefix + next;
    }

    public void report(Diagnostic diagnostic) {
        session.diagnostics.add(diagnostic);
    }

    /**
     * Records a diagnostic and leaves a matching comment in the generated .mo,
     * so a gap is visible both in the diagnostics list and in the output.
     */
    public void reportWithPlaceholder(Diagnostic diagnostic) {
        report(diagnostic);
        Placeholder placeholder = Modelica.placeholder(
            diagnostic.constructKind() + ": " + diagnostic.message());
        target.getPlaceholders().add(placeholder);
        link(diagnostic.crmlSource(), placeholder, TraceLinkKind.UNSUPPORTED);
    }

    /** A context targeting another class, sharing this translation's trace and diagnostics. */
    public TransformationContext nested(ClassDefinition newTarget) {
        return new TransformationContext(session, newTarget);
    }

    // --- trace --------------------------------------------------------------

    private void link(EObject crmlSource, ModelicaElement modelicaTarget, TraceLinkKind kind) {
        if (crmlSource == null) {
            return;
        }
        TraceLink traceLink = TraceFactory.eINSTANCE.createTraceLink();
        traceLink.setSource(crmlSource);
        traceLink.setTarget(modelicaTarget);
        traceLink.setKind(kind);
        session.trace.getLinks().add(traceLink);
    }

    /**
     * Records a link the three mutators cannot: the top-level class, which has
     * no enclosing target to be added to.
     */
    public void linkRoot(EObject crmlSource, ClassDefinition definition) {
        link(crmlSource, definition, classKind(crmlSource, definition));
    }

    // The link kind follows from what the CRML source element is, so call sites
    // never have to pass it and can never pass the wrong one.

    private static TraceLinkKind componentKind(EObject crmlSource) {
        if (crmlSource instanceof Variable) {
            return TraceLinkKind.VARIABLE_TO_COMPONENT;
        }
        if (crmlSource instanceof Set<?>) {
            return TraceLinkKind.SET_TO_COMPONENT;
        }
        if (crmlSource instanceof Value) {
            return TraceLinkKind.VALUE_TO_COMPONENT;
        }
        return TraceLinkKind.UNSUPPORTED;
    }

    private static TraceLinkKind equationKind(EObject crmlSource) {
        if (crmlSource instanceof Variable) {
            return TraceLinkKind.VARIABLE_TO_EQUATION;
        }
        if (crmlSource instanceof Value) {
            return TraceLinkKind.VALUE_TO_EQUATION;
        }
        return TraceLinkKind.UNSUPPORTED;
    }

    private static TraceLinkKind classKind(EObject crmlSource, ClassDefinition definition) {
        if (crmlSource instanceof Model) {
            return TraceLinkKind.MODEL_TO_CLASS;
        }
        if (crmlSource instanceof crml.model.language.Class) {
            return TraceLinkKind.CLASS_TO_MODEL;
        }
        if (crmlSource instanceof CustomOperator) {
            return definition.getKind() == ClassKind.FUNCTION
                ? TraceLinkKind.OPERATOR_TO_FUNCTION
                : TraceLinkKind.OPERATOR_TO_BLOCK;
        }
        return TraceLinkKind.UNSUPPORTED;
    }
}
