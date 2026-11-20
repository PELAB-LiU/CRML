package org.openmodelica.crml.testutil.vql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openmodelica.crml.testutil.vql.ExpectationResult.Status;

/**
 * The expectation engine, on Ecore models used as data, spread over three resources:
 *
 * <pre>
 * base.ecore  package base:  Machine (abstract), Sensor { reading: EInt },
 *                            Lamp
 * app.ecore   package app:   Pump extends Machine;  Valve extends Machine, Sensor
 * data.xmi    one Lamp instance
 * </pre>
 */
class VqlEngineTest {

    private static final String HEADER = "package expectations;\n"
        + "import \"http://www.eclipse.org/emf/2002/Ecore\";\n"
        + "import \"http://example/base.ecore\";\n";
    /** Lines in {@link #HEADER}. */
    private static final int HEADER_LINES = 3;

    private static ResourceSet resourceSet;
    private static VqlEngine engine;

    @BeforeAll
    static void fixture() {
        EcoreFactory f = EcoreFactory.eINSTANCE;

        EPackage base = f.createEPackage();
        base.setName("base");
        base.setNsPrefix("base");
        // The resource URI of a metamodel must be its nsURI, so that the pattern parser can
        // resolve references to its classes; the last segment is still "base.ecore".
        base.setNsURI("http://example/base.ecore");
        EClass machine = eClass(base, "Machine", true);
        EClass sensor = eClass(base, "Sensor", false);
        EAttribute reading = f.createEAttribute();
        reading.setName("reading");
        reading.setEType(EcorePackage.Literals.EINT);
        sensor.getEStructuralFeatures().add(reading);
        // No enum: VIATRA types attributes by their Java class, which a dynamic EEnum
        // does not have. Enums of generated metamodels work.
        eClass(base, "Lamp", false);

        EPackage app = f.createEPackage();
        app.setName("app");
        app.setNsURI("http://example/app");
        eClass(app, "Pump", false).getESuperTypes().add(machine);
        EClass valve = eClass(app, "Valve", false);
        valve.getESuperTypes().add(machine);
        valve.getESuperTypes().add(sensor);

        EObject lampInstance = EcoreUtil.create((EClass) base.getEClassifier("Lamp"));

        resourceSet = new ResourceSetImpl();
        add(base, "http://example/base.ecore");
        add(app, "app.ecore");
        add(lampInstance, "data.xmi");

        engine = new VqlEngine(EcorePackage.eINSTANCE, base);
    }

    private static EClass eClass(EPackage pkg, String name, boolean isAbstract) {
        EClass c = EcoreFactory.eINSTANCE.createEClass();
        c.setName(name);
        c.setAbstract(isAbstract);
        pkg.getEClassifiers().add(c);
        return c;
    }

    private static void add(EObject root, String file) {
        Resource resource = new ResourceImpl(URI.createURI(file));
        resourceSet.getResources().add(resource);
        resource.getContents().add(root);
    }

    private static List<ExpectationResult> run(String patterns) {
        return engine.evaluate(HEADER + patterns, URI.createURI("test.vql"), resourceSet);
    }

    private static ExpectationResult single(String patterns) {
        List<ExpectationResult> results = run(patterns);
        assertEquals(1, results.size(), results.toString());
        return results.get(0);
    }

    private static void passes(String patterns) {
        ExpectationResult result = single(patterns);
        assertEquals(Status.PASS, result.status, result.toString());
    }

    private static ExpectationResult fails(String patterns) {
        ExpectationResult result = single(patterns);
        assertEquals(Status.FAIL, result.status, result.toString());
        return result;
    }

    private static ExpectationResult error(String patterns) {
        ExpectationResult result = single(patterns);
        assertEquals(Status.ERROR, result.status, result.toString());
        return result;
    }

    /** A constraint that there are {@code n} classes extending Machine. */
    private static String machines(String check) {
        return "@Constraint(key = {n}, severity = \"error\", message = \"$n$ machines\")\n"
            + "pattern machineCount(n : java Integer) {\n"
            + "    n == count find machine(_);\n"
            + "    check(" + check + ");\n}\n"
            + "pattern machine(c : EClass) {\n"
            + "    EClass.eSuperTypes(c, m);\n    EClass.name(m, \"Machine\");\n}\n";
    }

    @Test
    void countAndCheck() {
        passes(machines("n != 2"));
        passes(machines("n < 1"));
        ExpectationResult result = fails(machines("n != 1"));
        assertEquals("2 machines (n = 2)", result.detail);
        assertEquals(HEADER_LINES + 2, result.line);
        assertEquals("error", result.severity);
        assertTrue(result.source.startsWith("pattern machineCount"), result.source);
    }

    @Test
    void everyMatchIsAViolation() {
        passes("@Constraint(key = {c}, severity = \"error\", message = \"no class may be called Nothing\")\n"
            + "pattern nothing(c : EClass) {\n    EClass.name(c, \"Nothing\");\n}\n");
        ExpectationResult result = fails("@Constraint(key = {c}, message = \"$c.name$ is concrete\")\n"
            + "pattern concrete(c : EClass) {\n    EClass.abstract(c, false);\n}\n");
        assertTrue(result.detail.contains("Pump is concrete (c = EClass Pump (app.ecore))"), result.detail);
    }

    @Test
    void warningsDoNotFail() {
        ExpectationResult result = single("@Constraint(key = {c}, severity = \"warning\", message = \"concrete\")\n"
            + "pattern concrete(c : EClass) {\n    EClass.abstract(c, false);\n}\n");
        assertEquals(Status.WARN, result.status, result.toString());
        assertTrue(!result.failed());
    }

    @Test
    void helpersAreNotReported() {
        passes(machines("n != 2") + "pattern unused(c : EClass) {\n    EClass(c);\n}\n");
    }

    @Test
    void library() {
        passes(constraintThatHolds("EClass.name(c, \"Pump\"); find containerOf(c, p); EPackage.name(p, \"app\");"));
        passes(constraintThatHolds("EClass.name(c, \"Sensor\"); find rootOf(c, p); EPackage.name(p, \"base\");"));
        passes(constraintThatHolds("EClass.name(c, \"Valve\"); find fileOf(c, \"app.ecore\");"));
        passes(constraintThatHolds("Lamp(c); find fileOf(c, \"data.xmi\");"));
        // Pump and Valve have Machine as a supertype.
        passes(constraintThatHolds("EClass.name(m, \"Machine\"); find referrerOf(m, c); EClass(c);", 2));
    }

    /** A constraint violated unless {@code body} has exactly {@code expected} matches of c. */
    private static String constraintThatHolds(String body, int expected) {
        return "@Constraint(key = {n}, message = \"$n$\")\n"
            + "pattern holds(n : java Integer) {\n    n == count find body(_);\n    check(n != " + expected + ");\n}\n"
            + "pattern body(c) {\n    " + body + "\n}\n";
    }

    private static String constraintThatHolds(String body) {
        return constraintThatHolds(body, 1);
    }

    @Test
    void typoIsAnErrorAtItsPattern() {
        ExpectationResult result = error("@Constraint(key = {c}, message = \"x\")\npattern typo(c) {\n"
            + "    EClass.nmae(c, \"x\");\n}\n");
        assertTrue(result.detail.contains("nmae"), result.detail);
        assertEquals(HEADER_LINES + 2, result.line);
    }

    @Test
    void badAnnotationIsAnError() {
        String pattern = "pattern p(c : EClass) {\n    EClass(c);\n}\n";
        assertTrue(error("@Constraint(message = \"x\")\n" + pattern).detail.contains("key"));
        assertTrue(error("@Constraint(key = {c})\n" + pattern).detail.contains("message"));
        assertTrue(error("@Constraint(key = {d}, message = \"x\")\n" + pattern).detail.contains("not a parameter"));
        assertTrue(error("@Constraint(key = {c}, message = \"x\", severity = \"fatal\")\n" + pattern)
            .detail.contains("severity"));
        assertTrue(error("@Constraint(key = {c}, message = \"x\", sevrity = \"error\")\n" + pattern)
            .detail.contains("sevrity"));
    }

    @Test
    void oneBadPatternLeavesTheOthers() {
        List<ExpectationResult> results = run("@Constraint(key = {c}, message = \"x\")\npattern typo(c) {\n"
            + "    EClass.nmae(c, \"x\");\n}\n" + machines("n != 2"));
        assertEquals(2, results.size(), results.toString());
        assertEquals(Status.ERROR, results.get(0).status);
        assertEquals(Status.PASS, results.get(1).status, results.get(1).toString());
    }

    @Test
    void generatedLibraryParses() {
        String source = engine.library().source();
        assertTrue(source.contains("pattern containerOf"), source);
        assertTrue(source.contains("EClass.eStructuralFeatures(container, o)"), source);
        // derived references (EClass.eAllSuperTypes) are not referrers
        assertTrue(!source.contains("eAllSuperTypes"), source);
    }
}
