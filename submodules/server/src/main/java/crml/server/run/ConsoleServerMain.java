package crml.server.run;

import java.io.*;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import crml.server.mcp.McpServer;

public class ConsoleServerMain {

    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        McpServer server = new McpServer(mapper);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                JsonNode req = mapper.readTree(line);
                JsonNode resp = server.handle(req);
                if (resp != null) out.println(mapper.writeValueAsString(resp));
            } catch (Exception e) {
                System.err.println("MCP error: " + e.getMessage());
            }
        }
    }
}
