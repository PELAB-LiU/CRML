package org.openmodelica.crml.testutil.vql;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.viatra.addon.validation.core.ValidationEngine;
import org.eclipse.viatra.addon.validation.core.api.IConstraint;
import org.eclipse.viatra.addon.validation.core.api.IValidationEngine;
import org.eclipse.viatra.addon.validation.core.api.IViolation;
import org.eclipse.viatra.addon.validation.core.api.Severity;
import org.eclipse.viatra.query.patternlanguage.emf.EMFPatternLanguageStandaloneSetup;
import org.eclipse.viatra.query.patternlanguage.emf.util.PatternParser;
import org.eclipse.viatra.query.patternlanguage.emf.util.PatternParserBuilder;
import org.eclipse.viatra.query.patternlanguage.emf.util.PatternParsingResults;
import org.eclipse.viatra.query.patternlanguage.emf.vql.Annotation;
import org.eclipse.viatra.query.patternlanguage.emf.vql.Pattern;
import org.eclipse.viatra.query.runtime.api.AdvancedViatraQueryEngine;
import org.eclipse.viatra.query.runtime.api.IPatternMatch;
import org.eclipse.viatra.query.runtime.api.IQuerySpecification;
import org.eclipse.viatra.query.runtime.api.ViatraQueryMatcher;
import org.eclipse.viatra.query.runtime.emf.EMFScope;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.validation.Issue;
import org.openmodelica.crml.testutil.vql.ExpectationResult.Status;

/**
 * Checks expectation files against EMF models with VIATRA's validation framework.
 * See EXPECTATIONS.md.
 *
 * <p>An expectation file is an ordinary {@code .vql} file in package
 * {@value EmfLibrary#PACKAGE}. Each pattern annotated {@code @Constraint} is a
 * validation constraint: each of its matches is a violation. A case passes when no
 * constraint of severity {@code error} has a violation; {@code warning} and
 * {@code info} violations are reported only. Patterns without the annotation are
 * helpers. The patterns of {@link EmfLibrary} are always available.
 *
 * <pre>{@code
 * VqlEngine engine = new VqlEngine(CRMLPackage.eINSTANCE);
 * List<ExpectationResult> results = engine.evaluate(caseDir.resolve("expected.vql"), resourceSet);
 * emit(ExpectationTable.of(results), "Expectations");
 * }</pre>
 */
public final class VqlEngine {

    private static boolean setUp;

    private final EmfLibrary library;
    private URI libraryUri;
    private List<IQuerySpecification<?>> librarySpecifications;

    public VqlEngine(EPackage... packages) {
        this(Arrays.asList(packages));
    }

    public VqlEngine(List<? extends EPackage> packages) {
        for (EPackage pkg : packages) {
            EPackage.Registry.INSTANCE.putIfAbsent(pkg.getNsURI(), pkg);
        }
        this.library = new EmfLibrary(packages);
    }

    /** The generated library, for the report or for writing new expectation files. */
    public EmfLibrary library() {
        return library;
    }

    /** One result per constraint of the file, and one per error, in line order. */
    public List<ExpectationResult> evaluate(Path vqlFile, ResourceSet models) throws IOException {
        String text = new String(Files.readAllBytes(vqlFile), StandardCharsets.UTF_8);
        return evaluate(text, URI.createFileURI(vqlFile.toAbsolutePath().toString()), models);
    }

    /** Like {@link #evaluate(Path, ResourceSet)}, for VQL text; {@code uri} names it in messages. */
    public List<ExpectationResult> evaluate(String vqlText, URI uri, ResourceSet models) {
        PatternParser parser = new PatternParserBuilder()
            .withLibrary(libraryUri(), librarySpecifications)
            .build();
        PatternParsingResults parsed = parser.parse(vqlText, uri);

        List<ExpectationResult> results = new ArrayList<>();
        Map<Pattern, List<Issue>> errors = errorsByPattern(parsed, results);

        // The constraints: patterns with @Constraint and without errors.
        Map<PatternConstraint, Pattern> constraints = new LinkedHashMap<>();
        for (Pattern pattern : parsed.getPatterns()) {
            Annotation annotation = PatternConstraint.annotation(pattern);
            List<Issue> issues = errors.get(pattern);
            if (issues != null) {
                results.add(result(pattern, annotation, Status.ERROR, messages(issues)));
                continue;
            }
            if (annotation == null) {
                continue;
            }
            Optional<IQuerySpecification<? extends ViatraQueryMatcher<? extends IPatternMatch>>> query =
                parsed.getQuerySpecification(fullName(pattern));
            if (!query.isPresent()) {
                results.add(result(pattern, annotation, Status.ERROR, "the pattern was not turned into a query"));
                continue;
            }
            try {
                constraints.put(PatternConstraint.of(pattern, annotation, query.get()), pattern);
            } catch (IllegalArgumentException e) {
                results.add(result(pattern, annotation, Status.ERROR, e.getMessage()));
            }
        }

        ResourceSet index = FileIndex.of(models);
        Set<ResourceSet> scope = new HashSet<>();
        scope.add(models);
        scope.add(index);
        AdvancedViatraQueryEngine engine = AdvancedViatraQueryEngine.createUnmanagedEngine(new EMFScope(scope));
        IValidationEngine validation = ValidationEngine.builder().setEngine(engine).build();
        try {
            validation.initialize();
            for (Map.Entry<PatternConstraint, Pattern> entry : constraints.entrySet()) {
                Pattern pattern = entry.getValue();
                Annotation annotation = PatternConstraint.annotation(pattern);
                try {
                    IConstraint constraint = validation.addConstraintSpecification(entry.getKey());
                    Collection<IViolation> violations = constraint.listViolations();
                    if (violations.isEmpty()) {
                        results.add(result(pattern, annotation, Status.PASS, ""));
                    } else {
                        Status status = entry.getKey().getSeverity() == Severity.ERROR ? Status.FAIL : Status.WARN;
                        results.add(result(pattern, annotation, status, Describer.violations(violations)));
                    }
                } catch (RuntimeException e) {
                    results.add(result(pattern, annotation, Status.ERROR, "evaluation failed: " + e));
                }
            }
        } finally {
            validation.dispose();
            engine.dispose();
        }
        results.sort(Comparator.comparingInt(r -> r.line));
        return results;
    }

    private static ExpectationResult result(Pattern pattern, Annotation annotation, Status status, String detail) {
        String severity = "(helper)";
        if (annotation != null) {
            severity = "error";
            for (org.eclipse.viatra.query.patternlanguage.emf.vql.AnnotationParameter p : annotation.getParameters()) {
                if ("severity".equals(p.getName())
                        && p.getValue() instanceof org.eclipse.viatra.query.patternlanguage.emf.vql.StringValue) {
                    severity = ((org.eclipse.viatra.query.patternlanguage.emf.vql.StringValue) p.getValue()).getValue();
                }
            }
        }
        return new ExpectationResult(line(pattern), pattern.getName(), severity, source(pattern), status, detail);
    }

    /**
     * The errors of each pattern. Errors outside every pattern (package, imports)
     * become results of their own.
     */
    private static Map<Pattern, List<Issue>> errorsByPattern(PatternParsingResults parsed,
            List<ExpectationResult> fileErrors) {
        Map<Pattern, int[]> ranges = new LinkedHashMap<>();
        for (Pattern pattern : parsed.getPatterns()) {
            ICompositeNode node = NodeModelUtils.getNode(pattern);
            ranges.put(pattern, new int[] { node.getStartLine(), node.getEndLine() });
        }
        Map<Pattern, List<Issue>> errors = new LinkedHashMap<>();
        for (Issue issue : parsed.getErrors()) {
            int line = issue.getLineNumber() == null ? 0 : issue.getLineNumber();
            Pattern owner = null;
            for (Map.Entry<Pattern, int[]> range : ranges.entrySet()) {
                if (line >= range.getValue()[0] && line <= range.getValue()[1]) {
                    owner = range.getKey();
                    break;
                }
            }
            if (owner == null) {
                fileErrors.add(new ExpectationResult(line, "(file)", "", "", Status.ERROR, issue.getMessage()));
            } else {
                errors.computeIfAbsent(owner, p -> new ArrayList<>()).add(issue);
            }
        }
        return errors;
    }

    private static String messages(List<Issue> issues) {
        StringBuilder text = new StringBuilder();
        for (Issue issue : issues) {
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append("line ").append(issue.getLineNumber()).append(": ").append(issue.getMessage());
        }
        return text.toString();
    }

    /** The line of the pattern's name, which is what a reader looks for. */
    private static int line(Pattern pattern) {
        List<org.eclipse.xtext.nodemodel.INode> nodes = NodeModelUtils.findNodesForFeature(pattern,
            org.eclipse.viatra.query.patternlanguage.emf.vql.PatternLanguagePackage.Literals.PATTERN__NAME);
        if (!nodes.isEmpty()) {
            return nodes.get(0).getStartLine();
        }
        ICompositeNode node = NodeModelUtils.getNode(pattern);
        return node == null ? 0 : node.getStartLine();
    }

    /** The pattern as written, without leading comments and annotations. */
    private static String source(Pattern pattern) {
        ICompositeNode node = NodeModelUtils.findActualNodeFor(pattern);
        if (node == null) {
            return "";
        }
        String text = node.getText().trim();
        int start = text.indexOf("pattern ");
        return start < 0 ? text : text.substring(start);
    }

    private static String fullName(Pattern pattern) {
        org.eclipse.emf.ecore.EObject root = pattern.eContainer();
        if (root instanceof org.eclipse.viatra.query.patternlanguage.emf.vql.PatternModel) {
            String pkg = ((org.eclipse.viatra.query.patternlanguage.emf.vql.PatternModel) root).getPackageName();
            if (pkg != null && !pkg.isEmpty()) {
                return pkg + "." + pattern.getName();
            }
        }
        return pattern.getName();
    }

    /** Parses the library once per engine; it must parse without errors. */
    private synchronized URI libraryUri() {
        if (libraryUri == null) {
            setUp();
            String source = library.source();
            URI uri;
            try {
                // VIATRA wants the file name to be a valid Java type name.
                File dir = Files.createTempDirectory("vql").toFile();
                dir.deleteOnExit();
                File file = new File(dir, "EmfLibrary.vql");
                file.deleteOnExit();
                Files.write(file.toPath(), source.getBytes(StandardCharsets.UTF_8));
                uri = URI.createFileURI(file.getAbsolutePath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            PatternParsingResults parsed = new PatternParserBuilder().build().parse(source, uri);
            if (parsed.hasError()) {
                throw new IllegalStateException("the generated VQL library has errors: " + parsed.getErrors()
                    + "\n" + source);
            }
            List<IQuerySpecification<?>> specifications = new ArrayList<>();
            parsed.getQuerySpecifications().forEach(specifications::add);
            librarySpecifications = specifications;
            libraryUri = uri;
        }
        return libraryUri;
    }

    private static synchronized void setUp() {
        if (!setUp) {
            // VIATRA logs every unresolved Java type of the JVM model it infers per
            // pattern at ERROR level, though parsing succeeds; keep the test output clean.
            Logger.getLogger("org.eclipse.viatra").setLevel(Level.FATAL);
            Logger.getLogger("org.eclipse.xtext").setLevel(Level.FATAL);
            EMFPatternLanguageStandaloneSetup.doSetup();
            // Registers the file index package; NS_URI alone, a constant, would not.
            FileIndex.PACKAGE.getNsURI();
            setUp = true;
        }
    }
}
