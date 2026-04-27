package crml.server.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import crml.server.mcp.services.McpSyntaxCheckService;
import crml.server.mcp.services.hints.McpGetCodingInstructionsService;
import crml.server.mcp.services.hints.McpListDocumentationFilesService;
import crml.server.mcp.services.hints.McpReadDocumentationFileService;
import crml.server.mcp.services.hints.McpSearchHintsService;

public class McpServer {

    private final ObjectMapper mapper;
    private final McpCapabilityReporter reporter;

    public McpServer(ObjectMapper mapper) {
        this.mapper = mapper;
        this.reporter = new McpCapabilityReporter(mapper);
        reporter.register(new McpSyntaxCheckService(mapper));
        reporter.registerDocumentation(new McpGetCodingInstructionsService(mapper));
        reporter.registerDocumentation(new McpListDocumentationFilesService(mapper));
        reporter.registerDocumentation(new McpReadDocumentationFileService(mapper));
        reporter.registerDocumentation(new McpSearchHintsService(mapper));
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
            case "initialize"         -> resp.set("result", reporter.buildInitializeResult());
            case "ping"               -> resp.set("result", mapper.createObjectNode());
            case "tools/list"         -> resp.set("result", reporter.buildToolsList());
            case "tools/call"         -> {
                String name = req.path("params").path("name").asText();
                JsonNode result = reporter.callTool(name, req.path("params").path("arguments"));
                if (result != null) resp.set("result", result);
                else setError(resp, -32601, "Unknown tool: " + name);
            }
            case "documentation/list" -> resp.set("result", reporter.buildDocumentationList());
            case "documentation/call" -> {
                String name = req.path("params").path("name").asText();
                JsonNode result = reporter.callDocumentation(name, req.path("params").path("arguments"));
                if (result != null) resp.set("result", result);
                else setError(resp, -32601, "Unknown documentation tool: " + name);
            }
            default -> setError(resp, -32601, "Method not found: " + method);
        }

        return resp;
    }

    private void setError(ObjectNode resp, int code, String message) {
        ObjectNode err = resp.putObject("error");
        err.put("code", code);
        err.put("message", message);
    }
}
