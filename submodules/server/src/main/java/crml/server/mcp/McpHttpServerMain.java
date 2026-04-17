package crml.server.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import crml.server.mcp.services.McpSyntaxCheckService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class McpHttpServerMain {

    private static final int DEFAULT_PORT = 63029;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        ObjectMapper mapper = new ObjectMapper();
        McpCapabilityReporter reporter = new McpCapabilityReporter(mapper);
        reporter.register(new McpSyntaxCheckService(mapper));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/mcp", new McpHttpHandler(reporter, mapper));
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("MCP HTTP server listening on port " + port);
    }
}
