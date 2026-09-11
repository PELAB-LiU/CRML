package crml.modelica.print;

import static crml.modelica.build.Modelica.assign;
import static crml.modelica.build.Modelica.call;
import static crml.modelica.build.Modelica.component;
import static crml.modelica.build.Modelica.function;
import static crml.modelica.build.Modelica.ifExpr;
import static crml.modelica.build.Modelica.input;
import static crml.modelica.build.Modelica.output;
import static crml.modelica.build.Modelica.record;
import static crml.modelica.build.Modelica.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import crml.model.modelica.AlgorithmSection;
import crml.model.modelica.ClassDefinition;
import crml.model.modelica.ModelicaFactory;

/**
 * The M1 gate: hand-build two classes that exist verbatim in
 * {@code CRMLtoModelica.mo} and check the printer reproduces them byte-exactly.
 * These are the shapes the transformation has to be able to emit.
 */
public class LibraryRoundTripTest {

    private static final PrinterOptions OPTIONS = PrinterOptions.DEFAULT.withLineSeparator("\n");

    /** CRMLtoModelica.Types.Event */
    @Test
    public void reproducesEventRecord() {
        ClassDefinition event = record("Event");
        event.getComponents().add(component("Boolean4", "b"));
        event.getComponents().add(component("Real", "t"));

        assertEquals(
            "record Event\n"
          + "    Boolean4 b;\n"
          + "    Real t;\n"
          + "end Event;\n",
            ModelicaPrinter.print(event, OPTIONS));
    }

    /** CRMLtoModelica.Functions.and4, in its nested-conditional form. */
    @Test
    public void reproducesAnd4Function() {
        ClassDefinition and4 = function("and4");
        and4.getComponents().add(input("Boolean4", "r1"));
        and4.getComponents().add(input("Boolean4", "r2"));
        and4.getComponents().add(output("Boolean4", "out"));

        AlgorithmSection algorithm = ModelicaFactory.eINSTANCE.createAlgorithmSection();
        algorithm.getStatements().add(assign(ref("out"),
            ifExpr(call("isUndefined", ref("r1")),
                ref("Boolean4.undefined"),
                ref("Boolean4.true4"))));
        and4.setAlgorithm(algorithm);

        assertEquals(
            "function and4\n"
          + "    input Boolean4 r1;\n"
          + "    input Boolean4 r2;\n"
          + "    output Boolean4 out;\n"
          + "algorithm\n"
          + "    out := if isUndefined(r1) then Boolean4.undefined else Boolean4.true4;\n"
          + "end and4;\n",
            ModelicaPrinter.print(and4, OPTIONS));
    }
}
