package crml.server.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import crml.server.mcp.services.McpTool;

import java.util.ArrayList;
import java.util.List;

public class McpCapabilityReporter {

    private final ObjectMapper mapper;
    private final List<McpTool> tools = new ArrayList<>();

    public McpCapabilityReporter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void register(McpTool tool) {
        tools.add(tool);
    }

    public JsonNode buildInitializeResult() {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        ObjectNode caps = result.putObject("capabilities");
        if (!tools.isEmpty()) caps.putObject("tools");
        ObjectNode info = result.putObject("serverInfo");
        info.put("name", "crml-syntax");
        info.put("version", "1.0.0");
        return result;
    }

    public JsonNode buildToolsList() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode toolsArray = result.putArray("tools");
        for (McpTool tool : tools) {
            ObjectNode t = toolsArray.addObject();
            t.put("name", tool.name());
            t.put("description", tool.description());
            t.set("inputSchema", tool.inputSchema(mapper));
        }
        return result;
    }

    public JsonNode callTool(String name, JsonNode arguments) {
        for (McpTool tool : tools) {
            if (tool.name().equals(name)) {
                return tool.call(arguments);
            }
        }
        return null;
    }
}
