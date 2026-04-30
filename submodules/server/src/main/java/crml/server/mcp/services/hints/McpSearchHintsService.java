package crml.server.mcp.services.hints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import crml.server.mcp.services.McpTool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class McpSearchHintsService implements McpTool {

    private final ObjectMapper mapper;

    public McpSearchHintsService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "search_hints";
    }

    @Override
    public String description() {
        return "Search for a keyword (langauge features) across all CRML language resource files. Returns matching file names with the lines that contain the query.";
    }

    @Override
    public JsonNode inputSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode queryProp = props.putObject("query");
        queryProp.put("type", "string");
        queryProp.put("description", "The keyword to search for across all hint files");
        schema.putArray("required").add("query");
        return schema;
    }

    @Override
    public JsonNode call(JsonNode arguments) {
        ObjectNode result = mapper.createObjectNode();
        String query = arguments.path("query").asText();

        try {
            List<Path> files;
            try (Stream<Path> stream = Files.list(HintsRoot.ROOT)) {
                files = stream.filter(p -> p.getFileName().toString().endsWith(".md"))
                              .sorted()
                              .collect(Collectors.toList());
            }

            List<String> results = new ArrayList<>();
            for (Path file : files) {
                String[] lines = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\n");
                List<String> matches = new ArrayList<>();
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].toLowerCase().contains(query.toLowerCase())) {
                        matches.add("  line " + (i + 1) + ": " + lines[i].replaceAll("\\s+$", ""));
                    }
                }
                if (!matches.isEmpty()) {
                    results.add(file.getFileName() + ":\n" + String.join("\n", matches));
                }
            }

            String text = results.isEmpty()
                ? "No matches found for '" + query + "'."
                : String.join("\n\n", results);
            result.putArray("content").addObject().put("type", "text").put("text", text);
        } catch (Exception e) {
            result.putArray("content").addObject().put("type", "text").put("text", "Error: " + e.getMessage());
        }
        return result;
    }
}
