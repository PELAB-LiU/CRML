package crml.server.http;

import java.io.IOException;
import java.io.OutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpHandler;

import crml.language.util.Parser;

import com.sun.net.httpserver.HttpExchange;

public class SyntaxCRMLHandler implements HttpHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static record SyntaxCheckRequest(String model) {};

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try{
            SyntaxCheckRequest request = objectMapper.readValue(exchange.getRequestBody(), SyntaxCheckRequest.class);

            System.out.println("Hello CRML handler.");

            Parser parser = new Parser();
            var result = parser.parse(request.model());

            result.syntax().errors().forEach(System.out::println);

            String response = objectMapper.writeValueAsString(result.syntax());
    
            System.out.println("Response: "+response);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        } catch (Exception e){
            e.printStackTrace(System.out);
            throw e;
        }
    }
}
