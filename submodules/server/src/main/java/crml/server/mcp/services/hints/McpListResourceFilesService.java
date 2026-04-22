package crml.server.mcp.services.hints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import crml.server.mcp.services.McpTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class McpListResourceFilesService implements McpTool {

    private final ObjectMapper mapper;

    public McpListResourceFilesService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "list_CRML_resource_files";
    }

    @Override
    public String description() {
        return "List all available CRML language resource files.";
    }

    @Override
    public JsonNode inputSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    @Override
    public JsonNode call(JsonNode arguments) {
        ObjectNode result = mapper.createObjectNode();
        try {
            String text;
            try (Stream<Path> stream = Files.list(HintsRoot.ROOT)) {
                String joined = stream
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".md"))
                    .sorted()
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(null);
                text = joined != null ? joined : "No hint files found.";
            }
            result.putArray("content").addObject().put("type", "text").put("text", text);
        } catch (Exception e) {
            result.putArray("content").addObject().put("type", "text").put("text", "Error: " + e.getMessage());
        }
        return result;
    }
}
