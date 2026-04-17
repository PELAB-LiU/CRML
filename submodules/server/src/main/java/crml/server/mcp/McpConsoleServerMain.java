package crml.server.mcp;

import java.io.*;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import crml.server.mcp.services.McpSyntaxCheckService;

public class McpConsoleServerMain {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        McpCapabilityReporter reporter = new McpCapabilityReporter(mapper);
        reporter.register(new McpSyntaxCheckService(mapper));

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            try {
                JsonNode req = mapper.readTree(line);
                String method = req.path("method").asText();
                JsonNode id = req.path("id");
                boolean hasId = !id.isMissingNode() && !id.isNull();

                switch (method) {
                    case "initialize" -> sendResult(out, id, reporter.buildInitializeResult());
                    case "notifications/initialized" -> { /* no response for notifications */ }
                    case "ping" -> { if (hasId) sendResult(out, id, mapper.createObjectNode()); }
                    case "tools/list" -> sendResult(out, id, reporter.buildToolsList());
                    case "tools/call" -> {
                        String name = req.path("params").path("name").asText();
                        JsonNode arguments = req.path("params").path("arguments");
                        JsonNode result = reporter.callTool(name, arguments);
                        if (result != null) {
                            sendResult(out, id, result);
                        } else {
                            sendError(out, id, -32601, "Unknown tool: " + name);
                        }
                    }
                    default -> { if (hasId) sendError(out, id, -32601, "Method not found: " + method); }
                }
            } catch (Exception e) {
                System.err.println("MCP error: " + e.getMessage());
            }
        }
    }

    private static void sendResult(PrintWriter out, JsonNode id, JsonNode result) throws IOException {
        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("id", id);
        resp.set("result", result);
        out.println(mapper.writeValueAsString(resp));
    }

    private static void sendError(PrintWriter out, JsonNode id, int code, String message) throws IOException {
        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("id", id);
        ObjectNode err = resp.putObject("error");
        err.put("code", code);
        err.put("message", message);
        out.println(mapper.writeValueAsString(resp));
    }
}
