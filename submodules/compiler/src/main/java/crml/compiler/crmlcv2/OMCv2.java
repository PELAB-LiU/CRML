package crml.compiler.crmlcv2;

import crml.compiler.crmlcv2.templates.ModelTransformer;
import crml.model.language.Model;

/**
 * Entry point of the CRML object model to Modelica translation.
 */
public class OMCv2 {

    /**
     * Translates one CRML model, returning the Modelica object model, the trace
     * back to the CRML elements, and everything that could not be mapped.
     *
     * <p>This never throws for an untranslatable construct: the construct is
     * reached, a {@link Diagnostic} is recorded and a placeholder comment marks
     * the gap in the generated text.
     */
    public TranslationResult translateModel(Model model) {
        TransformationContext ctx = ModelTransformer.transform(model);
        return new TranslationResult(ctx.target(), ctx.trace(), ctx.diagnostics());
    }

    /** The generated .mo text for one CRML model. */
    public String translate(Model model) {
        return translateModel(model).text();
    }
}
