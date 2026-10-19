import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import crml.language.grammar.crmlLexer;

/**
 * Prints the Java lexer's tokens for every .crml file under a directory, one
 * per line: relative path, token display name, start and stop code point
 * index. The TypeScript lexer parity test compares against this output.
 */
public class DumpTokens {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]);
        PrintStream out = new PrintStream(System.out, false, "UTF-8");
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".crml")).sorted().collect(Collectors.toList());
        }
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/');
            crmlLexer lexer = new crmlLexer(CharStreams.fromPath(file, StandardCharsets.UTF_8));
            lexer.removeErrorListeners();
            for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
                out.println(name + "\t" + crmlLexer.VOCABULARY.getDisplayName(token.getType())
                    + "\t" + token.getStartIndex() + "\t" + token.getStopIndex());
            }
        }
        out.flush();
    }
}
