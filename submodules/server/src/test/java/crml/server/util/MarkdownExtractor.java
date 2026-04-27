package crml.server.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownExtractor {
    private static final Pattern RX_CRML = Pattern.compile("^```crml(?:[\\n|\\r]+)(.*?)^```", Pattern.MULTILINE | Pattern.DOTALL);

    public static List<String> extractCrmlBlocks(Path markdownFile) throws IOException {
        List<String> examples = new ArrayList<>();
        Matcher matcher = RX_CRML.matcher(Files.readString(markdownFile));
        while (matcher.find()) {
            examples.add(matcher.group(1));
        }
        return examples;
    }
}
