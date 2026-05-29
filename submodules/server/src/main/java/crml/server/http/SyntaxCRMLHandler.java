package crml.server.http;

import java.io.IOException;
import java.io.OutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpHandler;

import crml.language.util.Parser;
import crml.language.util.Parser.ParserResult;

import com.sun.net.httpserver.HttpExchange;

public class SyntaxCRMLHandler implements HttpHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class SyntaxCheckRequest {
        private String model;

        public SyntaxCheckRequest() {}

        public SyntaxCheckRequest(String model) {
            this.model = model;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try{
            SyntaxCheckRequest request = objectMapper.readValue(exchange.getRequestBody(), SyntaxCheckRequest.class);

            System.out.println("Hello CRML handler.");

            Parser parser = new Parser();
            ParserResult result = parser.parse(request.getModel());

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
