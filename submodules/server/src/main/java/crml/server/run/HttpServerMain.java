package crml.server.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import crml.server.http.SyntaxCRMLHandler;
import crml.server.mcp.McpServer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class HttpServerMain {

    private static final int DEFAULT_PORT = 63029;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        McpServer server = new McpServer(new ObjectMapper());

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        
        httpServer.createContext("/mcp", new McpHttpHandler(server));
        httpServer.createContext("/crml/syntax", new SyntaxCRMLHandler());
        
        httpServer.setExecutor(Executors.newFixedThreadPool(16));
        httpServer.start();

        System.out.println("HTTP server listening on port " + port);
    }

    public static class McpHttpHandler implements HttpHandler {
        private final McpServer server;

        public McpHttpHandler(McpServer server) {
            this.server = server;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                JsonNode req = server.getMapper().readTree(exchange.getRequestBody().readAllBytes());
                JsonNode resp = server.handle(req);

                if (resp == null) {
                    exchange.sendResponseHeaders(202, -1);
                    return;
                }

                byte[] responseBytes = server.getMapper().writeValueAsBytes(resp);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            } catch (Exception e) {
                System.err.println("MCP HTTP error: " + e.getMessage());
                try {
                    exchange.sendResponseHeaders(500, -1);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
