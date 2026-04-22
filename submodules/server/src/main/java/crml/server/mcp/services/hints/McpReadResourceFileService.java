package crml.server.mcp.services.hints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import crml.server.mcp.services.McpTool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class McpReadResourceFileService implements McpTool {

    private final ObjectMapper mapper;

    public McpReadResourceFileService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "read_hint_file";
    }

    @Override
    public String description() {
        return "Get the content of CRML language resource file. Call list_CRML_resource_files first to see which files are available.";
    }

    @Override
    public JsonNode inputSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode filenameProp = props.putObject("filename");
        filenameProp.put("type", "string");
        filenameProp.put("description", "The name of the hint file to read (e.g. boolean_type.md)");
        schema.putArray("required").add("filename");
        return schema;
    }

    @Override
    public JsonNode call(JsonNode arguments) {
        ObjectNode result = mapper.createObjectNode();
        String filename = arguments.path("filename").asText();

        if (!filename.endsWith(".md")) {
            result.putArray("content").addObject().put("type", "text").put("text", "Only .md files are served.");
            return result;
        }

        try {
            Path file = HintsRoot.ROOT.resolve(filename);
            String text = Files.exists(file)
                ? Files.readString(file, StandardCharsets.UTF_8)
                : "File '" + filename + "' not found.";
            result.putArray("content").addObject().put("type", "text").put("text", text);
        } catch (Exception e) {
            result.putArray("content").addObject().put("type", "text").put("text", "Error: " + e.getMessage());
        }
        return result;
    }
}
