package crml.server.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import crml.server.mcp.services.McpSyntaxCheckService;
import crml.server.mcp.services.hints.McpListResourceFilesService;
import crml.server.mcp.services.hints.McpReadResourceFileService;
import crml.server.mcp.services.hints.McpSearchHintsService;
import crml.server.mcp.services.coding.McpGetCodingInstructionsService;

public class McpServer {

    private final ObjectMapper mapper;
    private final McpCapabilityReporter reporter;

    public McpServer(ObjectMapper mapper) {
        this.mapper = mapper;
        this.reporter = new McpCapabilityReporter(mapper);
        reporter.register(new McpGetCodingInstructionsService(mapper));
        reporter.register(new McpSyntaxCheckService(mapper));
        reporter.register(new McpListResourceFilesService(mapper));
        reporter.register(new McpReadResourceFileService(mapper));
        reporter.register(new McpSearchHintsService(mapper));
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    /** Returns the JSON-RPC response node, or null if no response is required (notifications). */
    public JsonNode handle(JsonNode req) {
        JsonNode id = req.path("id");
        boolean hasId = !id.isMissingNode() && !id.isNull();

        if (!hasId) return null;

        String method = req.path("method").asText();
        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("id", id);

        switch (method) {
            case "initialize":
                resp.set("result", reporter.buildInitializeResult());
                break;
            case "ping":
                resp.set("result", mapper.createObjectNode());
                break;
            case "tools/list":
                resp.set("result", reporter.buildToolsList());
                break;
            case "tools/call": {
                String name = req.path("params").path("name").asText();
                JsonNode result = reporter.callTool(name, req.path("params").path("arguments"));
                if (result != null) resp.set("result", result);
                else setError(resp, -32601, "Unknown tool: " + name);
                break;
            }
            default:
                setError(resp, -32601, "Method not found: " + method);
                break;
        }

        return resp;
    }

    private void setError(ObjectNode resp, int code, String message) {
        ObjectNode err = resp.putObject("error");
        err.put("code", code);
        err.put("message", message);
    }
}
