package crml.compiler.omc;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import crml.compiler.translation.Value;
import crml.compiler.translation.crmlVisitorImpl;
import crml.language.util.Parser.ParserResult;

public class OMGenerator {
    private static final Logger logger = LogManager.getLogger();

    private final String genericModelCode;
    private final List<String> externals;

    public OMGenerator(ParserResult model, Boolean causal) throws Exception {
        if (model.ast() == null)
            throw new OMGeneratorException("No ast found in model.");

        externals = new ArrayList<String>();
        crmlVisitorImpl visitor = new crmlVisitorImpl(model.parser(), externals, causal);

        Value result = visitor.visit(model.ast());
        genericModelCode = result.toModelica();
    }

    public String getModelicaCode(String within) {
        StringBuilder builder = new StringBuilder();
        if (within == null) {
            builder.append("within ;");
        } else {
            builder.append("within " + within + ";");
        }
        builder.append(System.lineSeparator());
        builder.append(genericModelCode);
        return builder.toString();
    }
    public Boolean hasExternals(){
        return !externals.isEmpty();
    }
    public String externals() {
        StringBuilder builder = new StringBuilder();
        for (String line : externals) {
            builder.append(line);
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    public String filename(){
        return Stream.<String>of(genericModelCode.split("\n"))
                .filter(l -> l.startsWith("model "))
                .findFirst()
                .map(l -> l.split("\\s+")[1]+".mo")
                .orElseThrow(() -> new IllegalStateException("No model declaration found."));
    }

    public static class OMGeneratorException extends RuntimeException {
        public OMGeneratorException(String message) {
            super(message);
        }
    }

    

}
