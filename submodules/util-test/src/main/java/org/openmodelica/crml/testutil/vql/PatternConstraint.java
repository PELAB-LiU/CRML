package org.openmodelica.crml.testutil.vql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.viatra.addon.validation.core.api.IConstraintSpecification;
import org.eclipse.viatra.addon.validation.core.api.Severity;
import org.eclipse.viatra.query.patternlanguage.emf.vql.Annotation;
import org.eclipse.viatra.query.patternlanguage.emf.vql.AnnotationParameter;
import org.eclipse.viatra.query.patternlanguage.emf.vql.ListValue;
import org.eclipse.viatra.query.patternlanguage.emf.vql.Pattern;
import org.eclipse.viatra.query.patternlanguage.emf.vql.StringValue;
import org.eclipse.viatra.query.patternlanguage.emf.vql.ValueReference;
import org.eclipse.viatra.query.patternlanguage.emf.vql.VariableReference;
import org.eclipse.viatra.query.patternlanguage.emf.vql.Variable;
import org.eclipse.viatra.query.runtime.api.IPatternMatch;
import org.eclipse.viatra.query.runtime.api.IQuerySpecification;
import org.eclipse.viatra.query.runtime.api.ViatraQueryMatcher;

/**
 * A {@code @Constraint} annotation of a pattern parsed at run time, as the validation
 * framework's specification. VIATRA's code generator writes one such class per
 * constraint; this one reads the annotation instead:
 *
 * <pre>
 * {@literal @}Constraint(key = {n}, severity = "error", message = "$n$ matches, expected 3")
 * </pre>
 *
 * {@code key} names the parameters that identify a violation, {@code message} may refer
 * to parameters as {@code $name$}, {@code severity} is {@code error} (the default),
 * {@code warning} or {@code info}.
 */
final class PatternConstraint implements IConstraintSpecification {

    static final String ANNOTATION = "Constraint";

    private final IQuerySpecification<? extends ViatraQueryMatcher<? extends IPatternMatch>> query;
    private final List<String> keys;
    private final List<String> properties;
    private final String message;
    private final Severity severity;

    private PatternConstraint(IQuerySpecification<? extends ViatraQueryMatcher<? extends IPatternMatch>> query,
            List<String> keys, List<String> properties, String message, Severity severity) {
        this.query = query;
        this.keys = keys;
        this.properties = properties;
        this.message = message;
        this.severity = severity;
    }

    /** The {@code @Constraint} annotation of the pattern, or null. */
    static Annotation annotation(Pattern pattern) {
        for (Annotation annotation : pattern.getAnnotations()) {
            if (ANNOTATION.equals(annotation.getName())) {
                return annotation;
            }
        }
        return null;
    }

    /**
     * The specification for {@code annotation} of {@code pattern}.
     *
     * @throws IllegalArgumentException if the annotation is malformed
     */
    static PatternConstraint of(Pattern pattern, Annotation annotation,
            IQuerySpecification<? extends ViatraQueryMatcher<? extends IPatternMatch>> query) {
        List<String> keys = null;
        String message = null;
        Severity severity = Severity.ERROR;
        for (AnnotationParameter parameter : annotation.getParameters()) {
            ValueReference value = parameter.getValue();
            switch (parameter.getName()) {
            case "key":
                keys = new ArrayList<>();
                List<ValueReference> values = value instanceof ListValue
                    ? ((ListValue) value).getValues()
                    : Collections.singletonList(value);
                for (ValueReference v : values) {
                    if (!(v instanceof VariableReference)) {
                        throw new IllegalArgumentException("key must list parameters, e.g. key = {c}");
                    }
                    keys.add(((VariableReference) v).getVar());
                }
                break;
            case "message":
                message = string(parameter);
                break;
            case "severity":
                String text = string(parameter);
                try {
                    severity = Severity.valueOf(text.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("severity must be error, warning or info, not " + text);
                }
                break;
            default:
                throw new IllegalArgumentException("unknown parameter " + parameter.getName()
                    + " of @" + ANNOTATION + "; use key, message and severity");
            }
        }
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("@" + ANNOTATION + " needs key = {...}, the parameters that "
                + "identify a violation");
        }
        if (message == null) {
            throw new IllegalArgumentException("@" + ANNOTATION + " needs a message");
        }
        List<String> parameters = new ArrayList<>();
        for (Variable variable : pattern.getParameters()) {
            parameters.add(variable.getName());
        }
        for (String key : keys) {
            if (!parameters.contains(key)) {
                throw new IllegalArgumentException("key " + key + " is not a parameter of the pattern");
            }
        }
        List<String> properties = new ArrayList<>(parameters);
        properties.removeAll(keys);
        return new PatternConstraint(query, keys, properties, message, severity);
    }

    private static String string(AnnotationParameter parameter) {
        if (!(parameter.getValue() instanceof StringValue)) {
            throw new IllegalArgumentException(parameter.getName() + " must be a string");
        }
        return ((StringValue) parameter.getValue()).getValue();
    }

    @Override
    public String getMessageFormat() {
        return message;
    }

    @Override
    public Map<String, Object> getKeyObjects(IPatternMatch match) {
        Map<String, Object> objects = new LinkedHashMap<>();
        for (String key : keys) {
            objects.put(key, match.get(key));
        }
        return objects;
    }

    @Override
    public List<String> getKeyNames() {
        return keys;
    }

    @Override
    public List<String> getPropertyNames() {
        return properties;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    @Override
    public Set<List<String>> getSymmetricPropertyNames() {
        return Collections.emptySet();
    }

    @Override
    public Set<List<String>> getSymmetricKeyNames() {
        return Collections.emptySet();
    }

    @Override
    public IQuerySpecification<? extends ViatraQueryMatcher<? extends IPatternMatch>> getQuerySpecification() {
        return query;
    }
}
