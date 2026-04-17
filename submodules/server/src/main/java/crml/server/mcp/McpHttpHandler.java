package crml.server.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;

public class McpHttpHandler implements HttpHandler {

    private final McpCapabilityReporter reporter;
    private final ObjectMapper mapper;

    public McpHttpHandler(McpCapabilityReporter reporter, ObjectMapper mapper) {
        this.reporter = reporter;
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            JsonNode req = mapper.readTree(exchange.getRequestBody().readAllBytes());
            String method = req.path("method").asText();
            JsonNode id = req.path("id");
            boolean hasId = !id.isMissingNode() && !id.isNull();

            if (!hasId) {
                exchange.sendResponseHeaders(202, -1);
                return;
            }

            ObjectNode resp = mapper.createObjectNode();
            resp.put("jsonrpc", "2.0");
            resp.set("id", id);

            switch (method) {
                case "initialize" -> resp.set("result", reporter.buildInitializeResult());
                case "ping" -> resp.set("result", mapper.createObjectNode());
                case "tools/list" -> resp.set("result", reporter.buildToolsList());
                case "tools/call" -> {
                    String name = req.path("params").path("name").asText();
                    JsonNode toolResult = reporter.callTool(name, req.path("params").path("arguments"));
                    if (toolResult != null) {
                        resp.set("result", toolResult);
                    } else {
                        ObjectNode err = resp.putObject("error");
                        err.put("code", -32601);
                        err.put("message", "Unknown tool: " + name);
                    }
                }
                default -> {
                    ObjectNode err = resp.putObject("error");
                    err.put("code", -32601);
                    err.put("message", "Method not found: " + method);
                }
            }

            byte[] responseBytes = mapper.writeValueAsBytes(resp);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            System.err.println("MCP HTTP error: " + e.getMessage());
            try { exchange.sendResponseHeaders(500, -1); } catch (Exception ignored) {}
        }
    }
}
