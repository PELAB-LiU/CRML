package crml.server.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import crml.server.mcp.services.hints.HintsRoot;
import crml.server.util.MarkdownExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class McpServerTest {

    private McpServer server;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        server = new McpServer(mapper);
    }

    // === JSON-RPC protocol handling ===

    @Test
    void notification_withoutId_returnsNull() {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("method", "ping");
        assertNull(server.handle(req));
    }

    @Test
    void notification_withNullId_returnsNull() {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("method", "ping");
        req.putNull("id");
        assertNull(server.handle(req));
    }

    @Test
    void response_hasJsonrpc20AndMatchingId() {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", 42);
        req.put("method", "ping");
        JsonNode resp = server.handle(req);
        assertNotNull(resp);
        assertEquals("2.0", resp.path("jsonrpc").asText());
        assertEquals(42, resp.path("id").asInt());
    }

    @Test
    void unknownMethod_returnsMethodNotFoundError() {
        JsonNode resp = handle("nonexistent/method", null);
        assertHasError(resp, -32601);
        assertTrue(resp.path("error").path("message").asText().contains("Method not found"));
    }

    // === initialize ===

    @Test
    void initialize_returnsProtocolVersionAndServerInfo() {
        JsonNode resp = handle("initialize", null);
        assertNoError(resp);
        JsonNode result = resp.path("result");
        assertEquals("2024-11-05", result.path("protocolVersion").asText());
        assertEquals("crml-syntax", result.path("serverInfo").path("name").asText());
        assertEquals("1.0.1", result.path("serverInfo").path("version").asText());
    }

    @Test
    void initialize_capabilitiesIncludesTools() {
        JsonNode resp = handle("initialize", null);
        assertNoError(resp);
        assertTrue(resp.path("result").path("capabilities").has("tools"));
    }

    // === ping ===

    @Test
    void ping_returnsEmptyResultObject() {
        JsonNode resp = handle("ping", null);
        assertNoError(resp);
        JsonNode result = resp.path("result");
        assertTrue(result.isObject());
        assertEquals(0, result.size());
    }

    // === tools/list ===

    @Test
    void toolsList_returnsFourTools() {
        JsonNode resp = handle("tools/list", null);
        assertNoError(resp);
        JsonNode tools = resp.path("result").path("tools");
        assertTrue(tools.isArray());
        assertEquals(5, tools.size());
    }

    @Test
    void toolsList_containsAllExpectedToolNames() {
        JsonNode tools = handle("tools/list", null).path("result").path("tools");
        List<String> names = new java.util.ArrayList<>();
        tools.forEach(t -> names.add(t.path("name").asText()));
        assertTrue(names.contains("get_coding_instructions"));
        assertTrue(names.contains("check_syntax"));
        assertTrue(names.contains("list_CRML_resource_files"));
        assertTrue(names.contains("read_hint_file"));
        assertTrue(names.contains("search_hints"));
    }

    @Test
    void toolsList_eachToolHasNameDescriptionAndInputSchema() {
        JsonNode tools = handle("tools/list", null).path("result").path("tools");
        for (JsonNode tool : tools) {
            String name = tool.path("name").asText();
            assertFalse(tool.path("name").asText().trim().isEmpty(), name + ": missing name");
            assertFalse(tool.path("description").asText().trim().isEmpty(), name + ": missing description");
            assertTrue(tool.path("inputSchema").isObject(), name + ": missing inputSchema");
        }
    }

    // === tools/call – dispatch ===

    @Test
    void toolsCall_unknownTool_returnsError() {
        ObjectNode params = toolCallParams("no_such_tool");
        params.putObject("arguments");
        JsonNode resp = handle("tools/call", params);
        assertHasError(resp, -32601);
        assertTrue(resp.path("error").path("message").asText().contains("Unknown tool"));
    }

    // === check_syntax tool ===

    @Test
    void checkSyntax_validModel_returnsSuccessMessage() throws IOException {
        List<String> blocks = MarkdownExtractor.extractCrmlBlocks(HintsRoot.ROOT.resolve("boolean_type.md"));
        assumeTrue(!blocks.isEmpty(), "No CRML blocks found in boolean_type.md");

        ObjectNode params = toolCallParams("check_syntax");
        params.putObject("arguments").put("model", blocks.get(0));
        JsonNode resp = handle("tools/call", params);
        assertNoError(resp);
        assertEquals("No syntax errors found. The model is valid.", getToolResultText(resp));
    }

    @Test
    void checkSyntax_invalidModel_reportsSyntaxErrors() {
        ObjectNode params = toolCallParams("check_syntax");
        params.putObject("arguments").put("model", "this @@@is not valid crml!!!");
        JsonNode resp = handle("tools/call", params);
        assertNoError(resp);
        String text = getToolResultText(resp);
        assertTrue(text.startsWith("Syntax errors:"), "Expected syntax errors, got: " + text);
    }

    @Test
    void checkSyntax_result_hasTextContentArray() {
        ObjectNode params = toolCallParams("check_syntax");
        params.putObject("arguments").put("model", "garbage @@@");
        JsonNode resp = handle("tools/call", params);
        assertNoError(resp);
        JsonNode content = resp.path("result").path("content");
        assertTrue(content.isArray());
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).path("type").asText());
        assertFalse(content.get(0).path("text").asText().trim().isEmpty());
    }

    // === list_CRML_resource_files tool ===

    @Test
    void listResourceFiles_returnsMarkdownFileNames() {
        ObjectNode params = toolCallParams("list_CRML_resource_files");
        params.putObject("arguments");
        JsonNode resp = handle("tools/call", params);
        assertNoError(resp);
        String text = getToolResultText(resp);
        assertTrue(text.contains(".md"), "Expected .md filenames, got: " + text);
    }

    @Test
    void listResourceFiles_includesKnownHintFiles() {
        ObjectNode params = toolCallParams("list_CRML_resource_files");
        params.putObject("arguments");
        String text = getToolResultText(handle("tools/call", params));
        assertTrue(text.contains("boolean_type.md"));
        assertTrue(text.contains("string_type.md"));
    }

    // === read_hint_file tool ===

    @Test
    void readHintFile_validFile_returnsContent() {
        ObjectNode params = toolCallParams("read_hint_file");
        params.putObject("arguments").put("filename", "boolean_type.md");
        JsonNode resp = handle("tools/call", params);
        assertNoError(resp);
        String text = getToolResultText(resp);
        assertFalse(text.trim().isEmpty());
        assertFalse(text.startsWith("Error:"));
    }

    @Test
    void readHintFile_nonMdFile_isRejected() {
        ObjectNode params = toolCallParams("read_hint_file");
        params.putObject("arguments").put("filename", "secret.txt");
        String text = getToolResultText(handle("tools/call", params));
        assertEquals("Only .md files are served.", text);
    }

    @Test
    void readHintFile_missingFile_returnsNotFoundMessage() {
        ObjectNode params = toolCallParams("read_hint_file");
        params.putObject("arguments").put("filename", "does_not_exist.md");
        String text = getToolResultText(handle("tools/call", params));
        assertTrue(text.contains("not found"), "Expected not-found message, got: " + text);
    }

    // === search_hints tool ===

    @Test
    void searchHints_matchingQuery_returnsResultsWithFileName() {
        ObjectNode params = toolCallParams("search_hints");
        params.putObject("arguments").put("query", "boolean");
        String text = getToolResultText(handle("tools/call", params));
        assertFalse(text.startsWith("No matches found"), "Expected matches for 'boolean'");
        assertTrue(text.contains("boolean_type.md"));
    }

    @Test
    void searchHints_nonMatchingQuery_reportsNoMatches() {
        ObjectNode params = toolCallParams("search_hints");
        params.putObject("arguments").put("query", "zzz_will_never_match_xyz_9999");
        String text = getToolResultText(handle("tools/call", params));
        assertTrue(text.startsWith("No matches found"), "Expected no-match message, got: " + text);
    }

    @Test
    void searchHints_isCaseInsensitive() {
        ObjectNode params = toolCallParams("search_hints");
        params.putObject("arguments").put("query", "BOOLEAN");
        String text = getToolResultText(handle("tools/call", params));
        assertFalse(text.startsWith("No matches found"), "Case-insensitive search for 'BOOLEAN' should find results");
    }

    @Test
    void searchHints_resultsIncludeLineNumbers() {
        ObjectNode params = toolCallParams("search_hints");
        params.putObject("arguments").put("query", "boolean");
        String text = getToolResultText(handle("tools/call", params));
        assertTrue(text.contains("line "), "Results should include line numbers");
    }

    // === Helpers ===

    private JsonNode handle(String method, ObjectNode params) {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", 1);
        req.put("method", method);
        if (params != null) req.set("params", params);
        return server.handle(req);
    }

    private ObjectNode toolCallParams(String toolName) {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", toolName);
        return params;
    }

    private String getToolResultText(JsonNode resp) {
        return resp.path("result").path("content").get(0).path("text").asText();
    }

    private void assertNoError(JsonNode resp) {
        assertNotNull(resp);
        assertFalse(resp.has("error"), "Unexpected error in response: " + resp.path("error"));
        assertTrue(resp.has("result"), "Missing 'result' field in response");
    }

    private void assertHasError(JsonNode resp, int expectedCode) {
        assertNotNull(resp);
        assertTrue(resp.has("error"), "Expected 'error' field in response");
        assertEquals(expectedCode, resp.path("error").path("code").asInt());
    }
}
