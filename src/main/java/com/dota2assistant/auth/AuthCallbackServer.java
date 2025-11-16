package com.dota2assistant.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * A simple HTTP server to handle OAuth callback from Steam.
 * This creates a temporary local web server to receive the authentication callback.
 */
@Component
public class AuthCallbackServer {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthCallbackServer.class);
    private static final int PORT = 8099;  // Updated to match the port in SteamApiService
    private static final String CALLBACK_PATH = "/auth/callback"; // Updated to match the path in SteamApiService
    
    private HttpServer server;
    private CompletableFuture<Map<String, Object>> callbackFuture;
    private SteamApiService steamApiService;
    
    public AuthCallbackServer() {
        // Default constructor for Spring component
    }
    
    public AuthCallbackServer(SteamApiService steamApiService) {
        this.steamApiService = steamApiService;
    }
    
    /**
     * Represents client information captured during authentication.
     */
    public static class ClientInfo {
        private String ipAddress;
        private String userAgent;
        private String callbackUrl;
        private final String timestamp;
        private String referer;
        private String hostName;
        private final Map<String, String> additionalInfo;
        
        public ClientInfo(String ipAddress, String userAgent, String callbackUrl) {
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
            this.callbackUrl = callbackUrl;
            this.timestamp = java.time.LocalDateTime.now().toString();
            this.additionalInfo = new HashMap<>();
        }
        
        /**
         * Create a more detailed client info object with additional fields
         */
        public ClientInfo(String ipAddress, String userAgent, String callbackUrl, 
                          String referer, String hostName) {
            this(ipAddress, userAgent, callbackUrl);
            this.referer = referer;
            this.hostName = hostName;
        }
        
        public String getIpAddress() {
            return ipAddress;
        }
        
        public String getUserAgent() {
            return userAgent;
        }
        
        public String getCallbackUrl() {
            return callbackUrl;
        }
        
        public String getTimestamp() {
            return timestamp;
        }
        
        public String getReferer() {
            return referer;
        }
        
        public void setReferer(String referer) {
            this.referer = referer;
        }
        
        public String getHostName() {
            return hostName;
        }
        
        public void setHostName(String hostName) {
            this.hostName = hostName;
        }
        
        /**
         * Add additional information to be stored with client info
         */
        public void addAdditionalInfo(String key, String value) {
            if (key != null && value != null) {
                additionalInfo.put(key, value);
            }
        }
        
        /**
         * Get all additional information as a map
         */
        public Map<String, String> getAdditionalInfo() {
            return new HashMap<>(additionalInfo);
        }
        
        @Override
        public String toString() {
            return "ClientInfo{" +
                    "ipAddress='" + ipAddress + '\'' +
                    ", userAgent='" + (userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 50)) + "..." : "null") + '\'' +
                    ", timestamp='" + timestamp + '\'' +
                    ", referer='" + referer + '\'' +
                    ", hostName='" + hostName + '\'' +
                    ", additionalInfo.size=" + (additionalInfo != null ? additionalInfo.size() : 0) +
                    '}';
        }
    }
    
    /**
     * Starts the server and returns a future that will be completed with auth information.
     * Includes retry logic for port conflicts.
     *
     * @return A future that will be completed with auth data including callback URL and client info
     * @throws IOException If the server cannot be started after retries
     */
    public CompletableFuture<Map<String, Object>> startAndWaitForCallback() throws IOException {
        if (server != null) {
            logger.info("Shutting down existing server before starting new one");
            stopServer();
            
            // Add a small delay to ensure the port is released
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Try to find an available port starting from PORT
        int attemptedPort = PORT;
        int maxRetries = 5;
        IOException lastException = null;
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // Ensure old server is stopped
                if (server != null) {
                    server.stop(0);
                    server = null;
                }
                
                callbackFuture = new CompletableFuture<>();
                server = HttpServer.create(new InetSocketAddress(attemptedPort), 0);
                server.createContext(CALLBACK_PATH, new CallbackHandler(this::handleCallback, callbackFuture));
                server.setExecutor(null); // Use the default executor
                server.start();
                logger.info("Auth callback server started on port {} and listening on path {}", attemptedPort, CALLBACK_PATH);
                
                return callbackFuture;
            } catch (IOException e) {
                lastException = e;
                
                // If it's an address binding issue, try the next port
                if (e instanceof java.net.BindException) {
                    logger.warn("Port {} already in use, trying next port", attemptedPort);
                    attemptedPort++;
                } else {
                    // For other IO exceptions, rethrow
                    throw e;
                }
                
                // Add a small delay between attempts
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // If we get here, we've exhausted all retry attempts
        logger.error("Failed to start callback server after {} attempts", maxRetries);
        throw new IOException("Failed to find available port after " + maxRetries + " attempts", lastException);
    }
    
    /**
     * Stops the server.
     */
    public void stopServer() {
        if (server != null) {
            try {
                server.stop(0);
                logger.info("Auth callback server stopped");
            } catch (Exception e) {
                logger.warn("Error stopping auth callback server", e);
            } finally {
                server = null;
            }
        }
        
        // Handle the future cleanup - don't complete exceptionally if it's already cancelled
        if (callbackFuture != null && !callbackFuture.isDone()) {
            try {
                if (!callbackFuture.isCancelled()) {
                    // Only complete exceptionally if not already cancelled
                    callbackFuture.completeExceptionally(new InterruptedException("Server was stopped"));
                    logger.debug("Auth future completed exceptionally during server stop");
                } else {
                    logger.debug("Auth future was already cancelled - no action needed during server stop");
                }
            } catch (Exception e) {
                logger.warn("Error during future cleanup", e);
            }
        }
    }
    
    /**
     * Alias for stopServer() to match the method being called in SteamApiService
     */
    public void stop() {
        stopServer();
    }
    
    /**
     * Gets the internal HTTP server instance.
     * This is needed to access port information when the server uses a dynamic port.
     * 
     * @return The HttpServer instance, or null if not started
     */
    public HttpServer getServer() {
        return server;
    }
    
    /**
     * Handler for the callback.
     */
    private void handleCallback(String callbackUrl, ClientInfo clientInfo) {
        logger.info("Received auth callback: {}", callbackUrl);
        logger.debug("Client info - IP: {}, User-Agent: {}", 
                clientInfo.getIpAddress(), clientInfo.getUserAgent());
        // The future will be completed by the handler
    }
    
    /**
     * HTTP handler for the callback endpoint.
     */
    private static class CallbackHandler implements HttpHandler {
        
        private final BiConsumer<String, ClientInfo> callbackHandler;
        private final CompletableFuture<Map<String, Object>> future;
        
        public CallbackHandler(BiConsumer<String, ClientInfo> callbackHandler, CompletableFuture<Map<String, Object>> future) {
            this.callbackHandler = callbackHandler;
            this.future = future;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Create a copy of the exchange URI to avoid thread safety issues
            String requestUri = exchange.getRequestURI().toString();
            String queryString = exchange.getRequestURI().getQuery();
            String ipAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            String requestMethod = exchange.getRequestMethod();
            
            // Log detailed request information for debugging
            logger.info("Received {} request on {}", requestMethod, requestUri);
            logger.debug("Client info - IP: {}, User-Agent: {}", ipAddress, userAgent != null ? userAgent : "Unknown");
            logger.debug("Query string: {}", queryString != null ? queryString : "No query parameters");
            
            try {
                // Method validation
                if (!requestMethod.equalsIgnoreCase("GET")) {
                    logger.warn("Rejected {} request. Only GET is allowed.", requestMethod);
                    sendResponse(exchange, 405, createErrorPage("Method Not Allowed", "Only GET requests are supported."));
                    return;
                }
                
                // No query parameters is a potential error
                if (queryString == null || queryString.isEmpty()) {
                    logger.warn("Received callback without query parameters");
                    // We still proceed as this might be a specific Steam return pattern
                }
                
                // Get the full URL including query parameters
                // Ensure we're using the proper scheme, host, and port for callback URL construction
                String callbackUrl = "http://localhost:" + PORT + requestUri;
                logger.debug("Constructed callback URL: {}", callbackUrl);
                
                // Set default user agent if not provided
                if (userAgent == null) {
                    userAgent = "Unknown";
                }
                
                // Collect additional client information
                Map<String, String> headers = new HashMap<>();
                exchange.getRequestHeaders().forEach((key, values) -> {
                    if (!values.isEmpty()) {
                        headers.put(key, String.join(", ", values));
                    }
                });
                
                // Log headers for debugging
                if (logger.isDebugEnabled()) {
                    headers.forEach((key, value) -> logger.debug("Header: {}: {}", key, value));
                }
                
                // Create enhanced client info object with more details
                String referer = headers.get("Referer");
                String host = headers.get("Host");
                
                ClientInfo clientInfo = new ClientInfo(ipAddress, userAgent, callbackUrl, referer, host);
                
                // Add additional useful headers as info
                for (String headerKey : Arrays.asList("Accept-Language", "Origin", "X-Forwarded-For", "X-Real-IP")) {
                    if (headers.containsKey(headerKey)) {
                        clientInfo.addAdditionalInfo(headerKey, headers.get(headerKey));
                    }
                }
                
                // Add the timestamp of request
                clientInfo.addAdditionalInfo("request_time", java.time.LocalDateTime.now().toString());
                
                // Process the callback in a separate thread to avoid blocking the HTTP handler
                CompletableFuture.runAsync(() -> {
                    try {
                        // Process the callback through the handler
                        callbackHandler.accept(callbackUrl, clientInfo);
                        
                        // Complete the future with the callback data
                        Map<String, Object> callbackData = new HashMap<>();
                        callbackData.put("url", callbackUrl);
                        callbackData.put("clientInfo", clientInfo);
                        callbackData.put("headers", headers); // Include headers for additional analysis if needed
                        future.complete(callbackData);
                    } catch (Exception e) {
                        logger.error("Error in async callback processing", e);
                        future.completeExceptionally(e);
                    }
                });
                
                // Send a simple HTML response
                String response = createSuccessPage();
                sendResponse(exchange, 200, response);
            } catch (Exception e) {
                logger.error("Error handling callback request", e);
                
                // Complete the future exceptionally if not already done
                if (!future.isDone()) {
                    future.completeExceptionally(e);
                }
                
                // Try to send an error response
                try {
                    sendResponse(exchange, 500, createErrorPage("Internal Server Error", 
                            "An error occurred while processing your authentication."));
                } catch (IOException responseError) {
                    logger.error("Failed to send error response", responseError);
                }
            }
        }
        
        /**
         * Creates a success page with auto-close functionality and manual close button
         */
        private String createSuccessPage() {
            return "<html><head><title>Authentication Successful</title>"
                    + "<style>"
                    + "body { font-family: Arial, sans-serif; text-align: center; margin-top: 50px; background-color: #f0f0f0; }"
                    + "h1 { color: #4CAF50; }"
                    + ".container { background-color: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); "
                    + "max-width: 500px; margin: 0 auto; padding: 20px; }"
                    + ".close-btn { background-color: #4CAF50; color: white; border: none; padding: 10px 20px; "
                    + "text-align: center; text-decoration: none; display: inline-block; font-size: 16px; "
                    + "margin: 10px 2px; cursor: pointer; border-radius: 4px; }"
                    + ".countdown { font-weight: bold; }"
                    + "@keyframes fadeOut { from { opacity: 1; } to { opacity: 0.5; } }"
                    + ".container.fading { animation: fadeOut 2s forwards; }"
                    + "</style></head>"
                    + "<body><div id='main-container' class='container'>"
                    + "<h1>Authentication Successful</h1>"
                    + "<p>You have successfully authenticated with Steam.</p>"
                    + "<p>The application has received your login information.</p>"
                    + "<p>This window will close automatically in <span class='countdown' id='countdown'>7</span> seconds...</p>"
                    + "<button class='close-btn' onclick='attemptClose();'>Close this window</button>"
                    + "<script>"
                    + "// Try multiple window close methods in sequence"
                    + "function attemptClose() {"
                    + "  document.getElementById('main-container').classList.add('fading');"
                    + "  setTimeout(function() {"
                    + "    tryCloseWindow();"
                    + "  }, 500);"
                    + "}"
                    + ""
                    + "function tryCloseWindow() {"
                    + "  // Try multiple closing techniques in sequence"
                    + "  try { window.close(); } catch(e) { console.error('Method 1 failed:', e); }"
                    + "  try { window.open('', '_self').close(); } catch(e) { console.error('Method 2 failed:', e); }"
                    + "  try { window.open('about:blank', '_self').close(); } catch(e) { console.error('Method 3 failed:', e); }"
                    + "  try {"
                    + "    var customEvent = new CustomEvent('browserclose', {detail: 'close_browser'});"
                    + "    window.dispatchEvent(customEvent);"
                    + "  } catch(e) { console.error('Custom event method failed:', e); }"
                    + "}"
                    + ""
                    + "// Set up the countdown"
                    + "var seconds = 7;" // Increased from 5 to 7 seconds for more time
                    + "var countdownElement = document.getElementById('countdown');"
                    + "function updateCountdown() {"
                    + "  countdownElement.textContent = seconds;"
                    + "  seconds--;"
                    + "  if (seconds <= 4 && seconds >= 0) {"
                    + "    document.getElementById('main-container').classList.add('fading');"
                    + "  }"
                    + "  if (seconds < 0) {"
                    + "    clearInterval(countdownInterval);"
                    + "    tryCloseWindow();"
                    + "  }"
                    + "}"
                    + "var countdownInterval = setInterval(updateCountdown, 1000);"
                    + ""
                    + "// Try to close automatically after timeout"
                    + "window.onload = function() {"
                    + "  setTimeout(function() { tryCloseWindow(); }, 1000);" // Try once right away
                    + "  setTimeout(function() { tryCloseWindow(); }, 4000);" // Try again later
                    + "  setTimeout(function() { tryCloseWindow(); }, 7000);" // Final attempt
                    + "};"
                    + "</script>"
                    + "</div></body></html>";
        }
        
        /**
         * Creates an error page with given title and message
         */
        private String createErrorPage(String title, String message) {
            return "<html><head><title>Authentication Error</title>"
                    + "<style>"
                    + "body { font-family: Arial, sans-serif; text-align: center; margin-top: 50px; background-color: #f0f0f0; }"
                    + "h1 { color: #f44336; }"
                    + ".container { background-color: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); "
                    + "max-width: 500px; margin: 0 auto; padding: 20px; }"
                    + "</style></head>"
                    + "<body><div class='container'>"
                    + "<h1>" + title + "</h1>"
                    + "<p>" + message + "</p>"
                    + "<p>Please return to the application and try again.</p>"
                    + "</div></body></html>";
        }
        
        /**
         * Send an HTTP response with proper error handling
         */
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            
            try {
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                    os.flush(); // Ensure all bytes are written
                }
            } catch (IOException e) {
                logger.error("Failed to send response", e);
                throw e; // Rethrow to allow caller to handle
            } finally {
                exchange.close(); // Ensure the exchange is always closed
            }
        }
    }
}