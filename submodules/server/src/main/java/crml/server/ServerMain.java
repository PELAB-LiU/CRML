package crml.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

public class ServerMain {
    public static void main(String[] args) throws IOException {
        
        // Create an HTTP server on port 8000
        HttpServer server = HttpServer.create(new InetSocketAddress(63028), 0);
        server.setExecutor(Executors.newFixedThreadPool(16));
        
        // Define a context (URL path) and handler
        server.createContext("/crml/syntax", new SyntaxCRMLHandler());
        
        // Start the server
        server.setExecutor(null); // creates a default executor
        System.out.println("Server started at http://localhost:63028");
        server.start();
    }
}
