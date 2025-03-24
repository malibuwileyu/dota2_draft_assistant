package com.dota2assistant.gsi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * HTTP server that receives Game State Integration updates from Dota 2.
 */
@Component
public class GsiServer {
    private static final Logger logger = LoggerFactory.getLogger(GsiServer.class);
    
    private HttpServer server;
    private final GsiConfig gsiConfig;
    private final GsiStateManager stateManager;
    
    @Value("${gsi.server.port:29455}")
    private int serverPort;
    
    @Value("${gsi.auth.token:dota2-draft-assistant-gsi-token}")
    private String authToken;
    
    @Value("${gsi.server.autostart:true}")
    private boolean autoStart;
    
    @Autowired
    public GsiServer(GsiConfig gsiConfig, GsiStateManager stateManager) {
        this.gsiConfig = gsiConfig;
        this.stateManager = stateManager;
    }
    
    @PostConstruct
    public void init() {
        if (autoStart) {
            start();
        }
    }
    
    /**
     * Starts the GSI server.
     */
    public void start() {
        try {
            serverPort = gsiConfig.getGsiServerPort();
            server = HttpServer.create(new InetSocketAddress(serverPort), 0);
            server.createContext("/gsi", new GsiHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            logger.info("GSI server started on port {}", serverPort);
        } catch (IOException e) {
            logger.error("Failed to start GSI server", e);
        }
    }
    
    /**
     * Stops the GSI server.
     */
    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("GSI server stopped");
        }
    }
    
    /**
     * Handles incoming GSI HTTP requests.
     */
    private class GsiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Read the request body
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
                String requestBody = reader.lines().collect(Collectors.joining());
                
                // Validate the auth token
                if (isValidRequest(exchange, requestBody)) {
                    // Process the GSI data
                    stateManager.processGsiUpdate(requestBody);
                    
                    // Send a 200 OK response
                    String response = "OK";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                } else {
                    // Send a 403 Forbidden response
                    String response = "Invalid auth token";
                    exchange.sendResponseHeaders(403, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) {
                logger.error("Error handling GSI request", e);
                String response = "Internal server error";
                exchange.sendResponseHeaders(500, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
            }
        }
        
        /**
         * Validates the incoming GSI request.
         * 
         * @param exchange The HTTP exchange
         * @param body The request body
         * @return true if the request is valid, false otherwise
         */
        private boolean isValidRequest(HttpExchange exchange, String body) {
            // Basic validation - check if the body contains the auth token
            // In production, you'd want to extract the token properly from the JSON
            if (body == null || body.isEmpty()) {
                return false;
            }
            
            return body.contains("\"token\":\"" + authToken + "\"");
        }
    }
}