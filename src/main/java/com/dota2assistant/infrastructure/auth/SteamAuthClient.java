package com.dota2assistant.infrastructure.auth;

import com.dota2assistant.domain.model.UserSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Steam authentication client that uses the backend for OAuth flow.
 * Opens browser to backend /auth/steam/login, receives JWT via callback.
 */
@Component
public class SteamAuthClient {

    private static final Logger log = LoggerFactory.getLogger(SteamAuthClient.class);
    private static final String BACKEND_URL = "https://d2draftassistantbackend-production.up.railway.app";
    private static final int CALLBACK_PORT = 27015;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int CALLBACK_TIMEOUT_MINUTES = 15; // Extended from 5

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private HttpServer callbackServer;
    private volatile boolean callbackReceived = false;

    public SteamAuthClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    }

    /**
     * Initiates Steam login via backend.
     * Opens browser to backend auth endpoint, waits for JWT callback.
     */
    public void login(Consumer<UserSession> onSuccess, Consumer<String> onError) {
        try {
            // Start local callback server to receive JWT
            startCallbackServer(onSuccess, onError);

            // Open browser to backend Steam login
            String loginUrl = BACKEND_URL + "/api/v1/auth/steam/login";
            log.info("Opening Steam login via backend: {}", loginUrl);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(loginUrl));
            } else {
                Runtime.getRuntime().exec(new String[]{"open", loginUrl});
            }

        } catch (Exception e) {
            log.error("Failed to initiate Steam login: {}", e.getMessage());
            onError.accept("Failed to open Steam login: " + e.getMessage());
            stopCallbackServer();
        }
    }

    /**
     * Validates a JWT token with the backend.
     */
    public CompletableFuture<Optional<UserSession>> validateToken(String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/api/v1/auth/validate"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return Optional.of(parseUserFromResponse(response.body(), token));
                }

                log.warn("Token validation failed: {}", response.statusCode());
                return Optional.empty();

            } catch (Exception e) {
                log.error("Error validating token: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    /**
     * Cancels any ongoing login attempt.
     */
    public void cancelLogin() {
        stopCallbackServer();
    }

    private void startCallbackServer(Consumer<UserSession> onSuccess, Consumer<String> onError) throws IOException {
        stopCallbackServer();
        callbackReceived = false;

        callbackServer = HttpServer.create(new InetSocketAddress("0.0.0.0", CALLBACK_PORT), 0);
        callbackServer.createContext("/callback", exchange -> {
            callbackReceived = true;
            try {
                String query = exchange.getRequestURI().getQuery();
                log.info("Callback received with query: {}", query != null ? "present" : "null");
                
                Map<String, String> params = parseQuery(query);
                String token = params.get("token");

                if (token == null || token.equals("error")) {
                    sendErrorResponse(exchange, "Authentication failed");
                    onError.accept("Steam authentication failed");
                    return;
                }

                log.info("Received JWT token from backend, validating...");

                // Send success response immediately so browser can close
                sendSuccessResponse(exchange);

                // Then validate token and notify
                validateToken(token).thenAccept(userOpt -> {
                    if (userOpt.isPresent()) {
                        log.info("Token validated successfully for user: {}", userOpt.get().personaName());
                        onSuccess.accept(userOpt.get());
                    } else {
                        log.warn("Token validation failed");
                        onError.accept("Failed to validate authentication token");
                    }
                });

            } catch (Exception e) {
                log.error("Error handling callback: {}", e.getMessage(), e);
                try {
                    sendErrorResponse(exchange, "Error: " + e.getMessage());
                } catch (IOException ignored) {}
                onError.accept("Error processing authentication: " + e.getMessage());
            } finally {
                // Delay stopping to ensure response is sent
                CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(this::stopCallbackServer);
            }
        });
        callbackServer.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
        callbackServer.start();

        log.info("Callback server started on port {} (timeout: {} minutes)", CALLBACK_PORT, CALLBACK_TIMEOUT_MINUTES);

        // Auto-shutdown after extended timeout
        CompletableFuture.delayedExecutor(CALLBACK_TIMEOUT_MINUTES, TimeUnit.MINUTES).execute(() -> {
            if (!callbackReceived) {
                log.warn("Callback server timeout - no callback received");
                onError.accept("Login timed out. Please try again.");
            }
            stopCallbackServer();
        });
    }

    private void stopCallbackServer() {
        if (callbackServer != null) {
            callbackServer.stop(0);
            callbackServer = null;
            log.debug("Callback server stopped");
        }
    }

    private UserSession parseUserFromResponse(String json, String token) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        
        String steamId = root.path("steamId").asText();
        String personaName = root.path("personaName").asText("Steam User");
        String avatarUrl = root.path("avatarUrl").asText(null);
        String profileUrl = root.path("profileUrl").asText(null);
        Integer mmr = root.has("mmr") && !root.path("mmr").isNull() ? root.path("mmr").asInt() : null;

        List<Integer> favoriteHeroIds = new ArrayList<>();
        if (root.has("favoriteHeroIds") && root.path("favoriteHeroIds").isArray()) {
            for (JsonNode id : root.path("favoriteHeroIds")) {
                favoriteHeroIds.add(id.asInt());
            }
        }

        List<String> preferredRoles = new ArrayList<>();
        if (root.has("preferredRoles") && root.path("preferredRoles").isArray()) {
            for (JsonNode role : root.path("preferredRoles")) {
                preferredRoles.add(role.asText());
            }
        }

        Instant now = Instant.now();
        return new UserSession(
            steamId,
            personaName,
            avatarUrl,
            profileUrl,
            mmr,
            favoriteHeroIds,
            preferredRoles,
            now,
            now,
            now.plusSeconds(30 * 24 * 60 * 60), // 30 days
            token // Store token for future API calls
        );
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;

        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                params.put(
                    URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                );
            }
        }
        return params;
    }

    private void sendSuccessResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String html = """
            <!DOCTYPE html>
            <html>
            <head><title>Login Successful</title></head>
            <body style="background: #1a1a2e; color: #fff; font-family: sans-serif; text-align: center; padding-top: 50px;">
                <h1>✅ Steam Login Successful!</h1>
                <p>This window will close automatically...</p>
                <script>
                    // Try to close immediately, then after short delay
                    window.close();
                    setTimeout(() => { window.close(); }, 500);
                    setTimeout(() => { 
                        document.body.innerHTML = '<h1>✅ Done!</h1><p>You can close this tab manually if it did not close.</p>';
                    }, 1000);
                </script>
            </body>
            </html>
            """;
        sendHtmlResponse(exchange, 200, html);
    }

    private void sendErrorResponse(com.sun.net.httpserver.HttpExchange exchange, String message) throws IOException {
        String html = """
            <!DOCTYPE html>
            <html>
            <head><title>Login Failed</title></head>
            <body style="background: #1a1a2e; color: #fff; font-family: sans-serif; text-align: center; padding-top: 50px;">
                <h1>❌ Login Failed</h1>
                <p>%s</p>
                <p>Please close this window and try again.</p>
            </body>
            </html>
            """.formatted(message);
        sendHtmlResponse(exchange, 401, html);
    }

    private void sendHtmlResponse(com.sun.net.httpserver.HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
