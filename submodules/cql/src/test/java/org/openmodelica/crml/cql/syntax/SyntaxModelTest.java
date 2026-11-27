package org.openmodelica.crml.cql.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openmodelica.crml.cql.syntax.Trees.and;
import static org.openmodelica.crml.cql.syntax.Trees.assertion;
import static org.openmodelica.crml.cql.syntax.Trees.compare;
import static org.openmodelica.crml.cql.syntax.Trees.count;
import static org.openmodelica.crml.cql.syntax.Trees.file;
import static org.openmodelica.crml.cql.syntax.Trees.filter;
import static org.openmodelica.crml.cql.syntax.Trees.integer;
import static org.openmodelica.crml.cql.syntax.Trees.it;
import static org.openmodelica.crml.cql.syntax.Trees.let;
import static org.openmodelica.crml.cql.syntax.Trees.name;
import static org.openmodelica.crml.cql.syntax.Trees.nav;
import static org.openmodelica.crml.cql.syntax.Trees.or;
import static org.openmodelica.crml.cql.syntax.Trees.path;
import static org.openmodelica.crml.cql.syntax.Trees.relative;
import static org.openmodelica.crml.cql.syntax.Trees.resource;
import static org.openmodelica.crml.cql.syntax.Trees.string;
import static org.openmodelica.crml.cql.syntax.Trees.type;
import static org.openmodelica.crml.cql.syntax.Trees.typed;
import static org.openmodelica.crml.cql.syntax.Trees.validate;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

/** The generated syntax metamodel (plan M1): its shape, and that it needs only EMF. */
class SyntaxModelTest {

    private static final SyntaxPackage P = SyntaxPackage.eINSTANCE;

    /** The concrete classes of the metamodel. */
    private static List<EClass> concreteClasses() {
        List<EClass> classes = new ArrayList<>();
        for (EClassifier classifier : P.getEClassifiers()) {
            if (classifier instanceof EClass && !((EClass) classifier).isAbstract()) {
                classes.add((EClass) classifier);
            }
        }
        return classes;
    }

    /**
     * The generated code runs without xcore.lib and xbase.lib, which the build keeps
     * off the runtime classpath (plan F8). The test classpath does not have them
     * either, so this test and all the others run the generated code as a backend
     * would.
     */
    @Test
    void runsWithoutXcoreAndXbaseLibraries() {
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.eclipse.emf.ecore.xcore.lib.XcoreEListExtensions"));
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.eclipse.xtext.xbase.lib.IterableExtensions"));
    }

    /** Every concrete class can be created, and each of its features set and read back. */
    @Test
    void everyClassCanBeCreatedAndFilled() {
        for (EClass eClass : concreteClasses()) {
            EObject object = P.getEFactoryInstance().create(eClass);
            for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
                if (feature instanceof EAttribute) {
                    Object value = sample((EAttribute) feature);
                    object.eSet(feature, value);
                    assertEquals(value, object.eGet(feature), eClass.getName() + "." + feature.getName());
                }
            }
        }
    }

    private static Object sample(EAttribute attribute) {
        Class<?> type = attribute.getEAttributeType().getInstanceClass();
        if (type == String.class) {
            return "x";
        } else if (type == int.class) {
            return 7;
        } else if (type == long.class) {
            return 7L;
        } else if (type == double.class) {
            return 0.5;
        } else if (type == boolean.class) {
            return true;
        }
        // An enum: its last literal, so that the value differs from the default.
        List<?> literals = ((org.eclipse.emf.ecore.EEnum) attribute.getEAttributeType()).getELiterals();
        return ((org.eclipse.emf.ecore.EEnumLiteral) literals.get(literals.size() - 1)).getInstance();
    }

    /**
     * Positions are not part of the tree; the parser returns them beside it. No class
     * has an attribute for one.
     */
    @Test
    void noPositionsInTheTree() {
        List<String> positionNames = java.util.Arrays.asList("offset", "length", "line", "column", "span");
        for (EClassifier classifier : P.getEClassifiers()) {
            if (classifier instanceof EClass) {
                for (EAttribute attribute : ((EClass) classifier).getEAttributes()) {
                    assertFalse(positionNames.contains(attribute.getName()),
                        classifier.getName() + "." + attribute.getName());
                }
            }
        }
    }

    /** All references are containments: the tree has no cross-references (names are strings). */
    @Test
    void allReferencesAreContainments() {
        for (EClass eClass : concreteClasses()) {
            for (EReference reference : eClass.getEAllReferences()) {
                assertTrue(reference.isContainment(), eClass.getName() + "." + reference.getName());
            }
        }
    }

    /** The multiplicities of spec section 4.4 that EMF's validation checks. */
    @Test
    void multiplicities() {
        assertBounds(P.getOr_Operands(), 2, -1);
        assertBounds(P.getAnd_Operands(), 2, -1);
        assertBounds(P.getPath_Steps(), 1, -1);
        assertBounds(P.getPath_Head(), 0, 1);
        assertBounds(P.getLiteralSet_Values(), 1, -1);
        assertBounds(P.getStatement_Companion(), 0, 1);
        assertBounds(P.getTypedClause_Condition(), 0, 1);
        assertBounds(P.getLinkConstraint_SourceRange(), 0, 1);
        assertBounds(P.getLinkConstraint_TargetRange(), 1, 1);
        assertBounds(P.getPairs_Source(), 1, 1);
        assertBounds(P.getPairs_Filters(), 0, -1);
        assertBounds(P.getCqlFile_Statements(), 0, -1);
    }

    private static void assertBounds(EStructuralFeature feature, int lower, int upper) {
        String name = feature.getEContainingClass().getName() + "." + feature.getName();
        assertEquals(lower, feature.getLowerBound(), name + " lower bound");
        assertEquals(upper, feature.getUpperBound(), name + " upper bound");
    }

    @Test
    void defaults() {
        SyntaxFactory f = SyntaxFactory.eINSTANCE;
        assertEquals(Severity.ERROR, f.createCompanion().getSeverity());
        assertNull(f.createCompanion().getMessage());
        assertNull(f.createPairComponent().getName());
        assertNull(f.createLinkConstraint().getRelation());
        assertNull(f.createTypeRef().getPackageName());
        assertFalse(f.createRelationDecl().isDefault());
    }

    @Test
    void validationChecksMultiplicities() {
        Or single = or(Trees.holds(name("a")));
        assertEquals(Diagnostic.ERROR, validate(single).getSeverity());
        assertEquals(Diagnostic.ERROR, validate(Trees.assertion(null)).getSeverity());
        assertEquals(Diagnostic.ERROR, validate(path(name("a"))).getSeverity());
        assertEquals(Diagnostic.OK,
            validate(or(Trees.holds(name("a")), and(Trees.holds(name("b")), Trees.holds(name("c"))))).getSeverity());
    }

    /** Trees built alike are equal; any difference in an attribute or a child is seen. */
    @Test
    void treesCompareByStructure() {
        Comparison a = compare(relative(nav("name")), ComparisonOp.EQ, string("Pump"));
        assertTrue(EcoreUtil.equals(a, compare(relative(nav("name")), ComparisonOp.EQ, string("Pump"))));
        assertFalse(EcoreUtil.equals(a, compare(relative(nav("name")), ComparisonOp.NE, string("Pump"))));
        assertFalse(EcoreUtil.equals(a, compare(relative(nav("nam")), ComparisonOp.EQ, string("Pump"))));
        assertFalse(EcoreUtil.equals(a, compare(path(it(), nav("name")), ComparisonOp.EQ, string("Pump"))));
    }

    /**
     * The example of spec section 3.1, built by hand: the metamodel covers it, and the
     * tree is valid. The parser's tests (plan M3) compare against trees like this one.
     */
    @Test
    void specExample31() {
        CqlFile file = file(
            resource("ExtendClass.crml", "M"),
            resource("Equipment.crml", "EQ"),
            let("pump", path(name("M"),
                filter(typed(type("Class"), compare(relative(nav("name")), ComparisonOp.EQ, string("Pump")))))),
            let("machine", path(name("EQ"),
                filter(typed(type("Class"), compare(relative(nav("name")), ComparisonOp.EQ, string("Machine")))))),
            assertion(compare(count(name("pump")), ComparisonOp.EQ, integer(1))),
            assertion(compare(count(name("machine")), ComparisonOp.EQ, integer(1))),
            assertion(compare(path(name("pump"), nav("superClasses")), ComparisonOp.CONTAINS, name("machine"))),
            assertion(compare(path(name("pump"), nav("superClasses")), ComparisonOp.IN, name("EQ"))));

        assertEquals(Diagnostic.OK, validate(file).getSeverity(), () -> validate(file).toString());
        assertEquals(8, file.getStatements().size());
        LetDecl pump = (LetDecl) file.getStatements().get(2);
        TypedClause clause = (TypedClause) ((FilterStep) ((Path) pump.getValue()).getSteps().get(0)).getCondition();
        assertEquals("Class", clause.getType().getName());
        assertNotNull(clause.getCondition());
    }
}
