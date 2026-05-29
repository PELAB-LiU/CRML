package crml.server.mcp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import crml.language.util.ErrorListener.CRMLSyntaxError;
import crml.language.util.ErrorListener.CRMLSyntaxResults;
import crml.language.util.Parser;
import crml.language.util.Parser.ParserResult;

public class McpSyntaxCheckService implements McpTool {

    private final ObjectMapper mapper;

    public McpSyntaxCheckService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "check_syntax";
    }

    @Override
    public String description() {
        return "Check CRML model syntax. Returns syntax errors with line/column numbers, or confirms the model is valid.";
    }

    @Override
    public JsonNode inputSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode modelProp = props.putObject("model");
        modelProp.put("type", "string");
        modelProp.put("description", "The CRML model source code to check");
        schema.putArray("required").add("model");
        return schema;
    }

    @Override
    public JsonNode call(JsonNode arguments) {
        ParserResult result = new Parser().parse(arguments.path("model").asText());
        CRMLSyntaxResults syntax = result.syntax();

        String text;
        if (!syntax.hasErrors()) {
            text = "No syntax errors found. The model is valid.";
        } else {
            StringBuilder sb = new StringBuilder("Syntax errors:\n");
            for (CRMLSyntaxError err : syntax.errors()) {
                sb.append("  ").append(err).append("\n");
            }
            text = sb.toString();
        }

        ObjectNode callResult = mapper.createObjectNode();
        callResult.putArray("content").addObject()
                .put("type", "text")
                .put("text", text);
        return callResult;
    }
}
