package crml.language;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import crml.language.util.Parser;

public class SimpleParseTest {
    String model = """
model BasicOperators is {
    class ApplicationType is {
        String exeFileLocation;
        String exeType;
        String zipFileUrl;
        ApplicationInstance {} instances;
    };
    class ApplicationInstance is {
        Real foo is 0;
        ApplicationType appType;
    };
    class HostType is {
        String nodeIP;
        Integer availableCpu is 10;
        Integer availableRam is 128;
        Integer availableHdd is 
    };
};
""";

    @Test
    public void test(){
        Parser parser = new Parser();
        var result = parser.parse(model);

        result.syntax().errors().forEach(System.out::println);

        assertFalse(result.syntax().hasErrors());
    }    
}
