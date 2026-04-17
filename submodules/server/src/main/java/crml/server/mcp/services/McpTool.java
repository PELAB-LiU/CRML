package crml.server.mcp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface McpTool {
    String name();
    String description();
    JsonNode inputSchema(ObjectMapper mapper);
    JsonNode call(JsonNode arguments);
}
