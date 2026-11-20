package org.openmodelica.crml.testutil.vql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;

/**
 * The VQL library every expectation file can call, generated from the metamodels.
 * It provides the facts about objects that are not features of the metamodels:
 *
 * <ul>
 * <li>{@code containerOf(o, container)}: the direct container (inverse of every
 *     containment reference);</li>
 * <li>{@code rootOf(o, root)}: the top object of the containment tree, an object
 *     without container being its own root;</li>
 * <li>{@code fileOf(o, name)}: the file name of the resource that holds {@code o}'s
 *     root (through {@link FileIndex});</li>
 * <li>{@code referrerOf(o, referrer)}: every object holding {@code o} in a
 *     non-containment, non-derived reference, whatever its name.</li>
 * </ul>
 *
 * The library is in package {@value #PACKAGE}; a file in the same package calls the
 * patterns by their simple names.
 */
public final class EmfLibrary {

    /** The package of the library, and of the expectation files that use it. */
    public static final String PACKAGE = "expectations";

    private final List<EPackage> packages;
    /** Import alias per package, for class names that more than one package declares. */
    private final Map<EPackage, String> aliases = new LinkedHashMap<>();
    private final Map<String, Integer> nameCount = new HashMap<>();

    public EmfLibrary(List<? extends EPackage> packages) {
        this.packages = new ArrayList<>(packages);
        for (EPackage pkg : this.packages) {
            countNames(pkg);
        }
        for (EPackage pkg : this.packages) {
            aliases.put(pkg, alias(pkg, this.packages));
        }
    }

    private void countNames(EPackage pkg) {
        for (EClassifier classifier : pkg.getEClassifiers()) {
            nameCount.merge(classifier.getName(), 1, Integer::sum);
        }
        for (EPackage sub : pkg.getESubpackages()) {
            countNames(sub);
        }
    }

    /**
     * The last segment of the nsURI that no other package's nsURI has in the same
     * place from the end: {@code model}, {@code modelica} and {@code trace} for
     * {@code org.openmodelica.crml.model.metamodel},
     * {@code org.openmodelica.crml.modelica.metamodel} and
     * {@code org.openmodelica.crml.modelica.trace.metamodel}.
     */
    private static String alias(EPackage pkg, List<EPackage> all) {
        String[] own = segments(pkg);
        for (int back = 1; back <= own.length; back++) {
            String candidate = own[own.length - back];
            boolean unique = true;
            for (EPackage other : all) {
                String[] theirs = segments(other);
                if (other != pkg && theirs.length >= back && theirs[theirs.length - back].equals(candidate)) {
                    unique = false;
                    break;
                }
            }
            if (unique && Character.isLetter(candidate.charAt(0))) {
                return candidate;
            }
        }
        return "p" + all.indexOf(pkg);
    }

    private static String[] segments(EPackage pkg) {
        String[] parts = pkg.getNsURI().split("[^A-Za-z0-9_]+");
        List<String> nonEmpty = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                nonEmpty.add(part);
            }
        }
        return nonEmpty.toArray(new String[0]);
    }

    /** The import lines for the packages, with an alias where a class name needs one. */
    public String imports() {
        StringBuilder text = new StringBuilder();
        if (!aliases.containsKey(EcorePackage.eINSTANCE)) {
            // for EObject
            text.append("import \"").append(EcorePackage.eNS_URI).append("\";\n");
        }
        for (Map.Entry<EPackage, String> entry : aliases.entrySet()) {
            text.append("import \"").append(entry.getKey().getNsURI()).append("\" as ").append(entry.getValue())
                .append(";\n");
        }
        return text.toString();
    }

    /**
     * The name to write for {@code classifier} in a pattern: its simple name, or
     * {@code alias::Name} if another of the packages declares the same name.
     */
    public String typeName(EClassifier classifier) {
        String name = classifier.getName();
        if (nameCount.getOrDefault(name, 0) > 1) {
            EPackage pkg = classifier.getEPackage();
            while (pkg.getESuperPackage() != null) {
                pkg = pkg.getESuperPackage();
            }
            String alias = aliases.get(pkg);
            if (alias != null) {
                return alias + "::" + name;
            }
        }
        return name;
    }

    /** The library source. */
    public String source() {
        List<String> containment = new ArrayList<>();
        List<String> references = new ArrayList<>();
        for (EPackage pkg : packages) {
            collect(pkg, containment, references);
        }
        StringBuilder text = new StringBuilder();
        text.append("package ").append(PACKAGE).append(";\n\n");
        text.append(imports());
        text.append("import \"").append(FileIndex.NS_URI).append("\";\n\n");

        text.append("// The direct container of o.\n");
        text.append("pattern containerOf(o : EObject, container : EObject)");
        bodies(text, containment, "container", "o");

        text.append("// The top object of o's containment tree; an object without container is its own root.\n");
        text.append("pattern rootOf(o : EObject, root : EObject) {\n");
        text.append("    find containerOf+(o, root);\n");
        text.append("    neg find containerOf(root, _);\n");
        text.append("} or {\n");
        text.append("    EObject(o);\n");
        text.append("    root == o;\n");
        text.append("    neg find containerOf(o, _);\n");
        text.append("}\n\n");

        text.append("// The file name of the resource that holds o.\n");
        text.append("pattern fileOf(o : EObject, name : java String) {\n");
        text.append("    find rootOf(o, root);\n");
        text.append("    File.roots(file, root);\n");
        text.append("    File.name(file, name);\n");
        text.append("}\n\n");

        text.append("// Every object that holds o in a non-containment, non-derived reference.\n");
        text.append("pattern referrerOf(o : EObject, referrer : EObject)");
        bodies(text, references, "referrer", "o");
        return text.toString();
    }

    private void collect(EPackage pkg, List<String> containment, List<String> references) {
        for (EClassifier classifier : pkg.getEClassifiers()) {
            if (!(classifier instanceof EClass)) {
                continue;
            }
            EClass eClass = (EClass) classifier;
            for (EReference reference : eClass.getEReferences()) {
                if (reference.isDerived() || reference.isContainer()) {
                    continue;
                }
                String navigation = typeName(eClass) + "." + reference.getName();
                (reference.isContainment() ? containment : references).add(navigation);
            }
        }
        for (EPackage sub : pkg.getESubpackages()) {
            collect(sub, containment, references);
        }
    }

    /** {@code { A.x(from, to); } or { B.y(from, to); } ...}, or a body that never matches. */
    private static void bodies(StringBuilder text, List<String> navigations, String from, String to) {
        if (navigations.isEmpty()) {
            text.append(" {\n    EObject(").append(to).append(");\n    EObject(").append(from).append(");\n    ")
                .append(to).append(" == ").append(from).append(";\n    ").append(to).append(" != ").append(from)
                .append(";\n}\n\n");
            return;
        }
        for (int i = 0; i < navigations.size(); i++) {
            text.append(i == 0 ? " {\n" : " or {\n");
            text.append("    ").append(navigations.get(i)).append('(').append(from).append(", ").append(to)
                .append(");\n}");
        }
        text.append("\n\n");
    }
}
