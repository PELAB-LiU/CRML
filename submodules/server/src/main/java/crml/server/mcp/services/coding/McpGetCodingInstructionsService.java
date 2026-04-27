package crml.server.mcp.services.coding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import crml.server.mcp.services.McpTool;
import crml.server.mcp.services.hints.HintsRoot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class McpGetCodingInstructionsService implements McpTool {

    private final ObjectMapper mapper;

    public McpGetCodingInstructionsService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "get_coding_instructions";
    }

    @Override
    public String description() {
        return "General use of CRML. Call this before writing CRML code to get high level instructions on how to use the language.";
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
            String text = Files.readString(HintsRoot.ROOT.resolve("../coding/coding_instructions.md"), StandardCharsets.UTF_8);
            result.putArray("content").addObject().put("type", "text").put("text", text);
        } catch (Exception e) {
            result.putArray("content").addObject().put("type", "text").put("text", "Error: " + e.getMessage());
        }
        return result;
    }
}
