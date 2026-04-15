package crml.language.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownExtractor {
    // Regex is tested with https://regexr.com/
    private static final Pattern RX_CRML = Pattern.compile("^```crml(?:[\\n|\\r]*)(.*?)^```", Pattern.MULTILINE | Pattern.DOTALL);

    /**
     * Extracts the content of all ```crml ... ``` code blocks from a markdown file.
     *
     * @param markdownFile path to the markdown file
     * @return list of code block contents, one entry per block, without the fence lines
     * @throws IOException if the file cannot be read
     */
    public static List<String> extractCrmlBlocks(Path markdownFile) throws IOException {
        List<String> examples = new ArrayList<>();
        Matcher matcher = RX_CRML.matcher(Files.readString(markdownFile));
        while (matcher.find()) {
            examples.add(matcher.group(1));
        }
        return examples;
    }
}
