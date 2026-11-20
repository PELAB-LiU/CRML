package org.openmodelica.crml.testutil.vql;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.viatra.addon.validation.core.api.IViolation;
import org.eclipse.viatra.query.runtime.api.IPatternMatch;

/**
 * Short texts for violations in the report: {@code message (c = Class Machine (Equipment.crml))}.
 * An object is shown by its class, its {@code name} attribute if it has one, and
 * the file of its resource.
 */
final class Describer {

    /** How many matches a list shows before "… and n more". */
    static final int LIMIT = 5;

    private Describer() {
    }

    /** {@code (a = x, b = y), …}, at most {@link #LIMIT} matches. */
    static String matches(Collection<? extends IPatternMatch> matches) {
        StringBuilder text = new StringBuilder();
        Iterator<? extends IPatternMatch> it = matches.iterator();
        for (int i = 0; it.hasNext() && i < LIMIT; i++) {
            text.append(i == 0 ? "" : ", ").append(match(it.next()));
        }
        if (matches.size() > LIMIT) {
            text.append(", … and ").append(matches.size() - LIMIT).append(" more");
        }
        return text.toString();
    }

    /** {@code message (key = x), …}, at most {@link #LIMIT} violations. */
    static String violations(Collection<IViolation> violations) {
        StringBuilder text = new StringBuilder();
        Iterator<IViolation> it = violations.iterator();
        for (int i = 0; it.hasNext() && i < LIMIT; i++) {
            IViolation violation = it.next();
            text.append(i == 0 ? "" : "; ").append(violation.getMessage()).append(" (");
            int k = 0;
            for (java.util.Map.Entry<String, Object> key : violation.getKeyObjects().entrySet()) {
                text.append(k++ == 0 ? "" : ", ").append(key.getKey()).append(" = ").append(describe(key.getValue()));
            }
            text.append(')');
        }
        if (violations.size() > LIMIT) {
            text.append("; … and ").append(violations.size() - LIMIT).append(" more");
        }
        return text.toString();
    }

    static String match(IPatternMatch match) {
        StringBuilder text = new StringBuilder("(");
        java.util.List<String> names = match.parameterNames();
        for (int i = 0; i < names.size(); i++) {
            text.append(i == 0 ? "" : ", ").append(names.get(i)).append(" = ").append(describe(match.get(i)));
        }
        return text.append(')').toString();
    }

    static String describe(Object value) {
        if (value instanceof EObject) {
            EObject object = (EObject) value;
            StringBuilder text = new StringBuilder(object.eClass().getName());
            EStructuralFeature name = object.eClass().getEStructuralFeature("name");
            if (name != null && !name.isMany()) {
                Object n = object.eGet(name);
                if (n instanceof String) {
                    text.append(' ').append(n);
                }
            }
            String file = file(object);
            if (file != null) {
                text.append(" (").append(file).append(')');
            }
            return text.toString();
        }
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        if (value instanceof Enumerator) {
            return ((Enumerator) value).getLiteral();
        }
        return String.valueOf(value);
    }

    /** The last segment of the object's resource URI, decoded; null outside a resource. */
    static String file(EObject object) {
        Resource resource = object.eResource();
        if (resource == null || resource.getURI() == null) {
            return null;
        }
        String last = resource.getURI().lastSegment();
        return last == null ? null : URI.decode(last);
    }
}
