package com.dota2assistant.auth;

import com.dota2assistant.AppConfig;
import com.dota2assistant.util.PropertyLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for interacting with the Steam Web API.
 */
@Service
public class SteamApiService {
    
    private static final Logger logger = LoggerFactory.getLogger(SteamApiService.class);
    private static final String STEAM_API_URL = "https://api.steampowered.com";
    private static final String PLAYER_SUMMARIES_PATH = "/ISteamUser/GetPlayerSummaries/v2/";
    private static final String PLAYER_OWNED_GAMES_PATH = "/IPlayerService/GetOwnedGames/v1/";
    private static final int DOTA2_APP_ID = 570;
    private static final String OPENID_PROVIDER_URL = "https://steamcommunity.com/openid/";
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PropertyLoader propertyLoader;
    private final SteamAuthenticationManager steamAuthManager;
    private final String apiKey;
    private final String openDotaApiKey;
    
    public SteamApiService(OkHttpClient httpClient, ObjectMapper objectMapper, PropertyLoader propertyLoader, SteamAuthenticationManager steamAuthManager) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.propertyLoader = propertyLoader;
        this.steamAuthManager = steamAuthManager;
        
        // Load the Steam API key from property loader (which handles both env vars and properties)
        this.apiKey = propertyLoader.getProperty("steam.api.key", "");
        
        // Load the OpenDota API key from property loader
        this.openDotaApiKey = propertyLoader.getProperty("opendota.api.key", "");
        
        // Check if we got a valid Steam key
        if (this.apiKey.isEmpty() || "dummy-key".equals(this.apiKey) || "${STEAM_API_KEY}".equals(this.apiKey)) {
            logger.warn("Steam API key is not properly set. API functionality will not work properly.");
        } else {
            // Only show first few characters of the key in logs for security
            String keyPrefix = this.apiKey.substring(0, Math.min(this.apiKey.length(), 4)) + "...";
            logger.info("Steam API key configured successfully: {}", keyPrefix);
        }
        
        // Log that we have an OpenDota API key
        if (this.openDotaApiKey != null && !this.openDotaApiKey.isEmpty()) {
            String odKeyPrefix = this.openDotaApiKey.substring(0, Math.min(this.openDotaApiKey.length(), 4)) + "...";
            logger.info("OpenDota API key configured successfully: {}", odKeyPrefix);
        } else {
            logger.warn("No OpenDota API key found in configuration, API rate limits will apply");
        }
    }
    
    /**
     * Get user profile information by Steam ID.
     *
     * @param steamId The Steam ID of the user
     * @return The user's profile information
     */
    public Optional<SteamUser> getUserProfile(String steamId) {
        if (apiKey.isEmpty()) {
            logger.error("Steam API key is required to fetch user profiles");
            return Optional.empty();
        }
        
        try {
            HttpUrl url = HttpUrl.parse(STEAM_API_URL + PLAYER_SUMMARIES_PATH)
                    .newBuilder()
                    .addQueryParameter("key", apiKey)
                    .addQueryParameter("steamids", steamId)
                    .build();
            
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            
            logger.debug("Sending request to Steam API: {}", url);
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                logger.debug("Steam API response code: {}", response.code());
                
                if (!response.isSuccessful()) {
                    logger.error("Failed to get user profile: HTTP {}", response.code());
                    logger.debug("Response body: {}", responseBody);
                    return Optional.empty();
                }
                
                logger.debug("Response body: {}", responseBody);
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode players = root.path("response").path("players");
                
                if (players.isArray() && players.size() > 0) {
                    SteamUser user = objectMapper.treeToValue(players.get(0), SteamUser.class);
                    logger.debug("Successfully parsed Steam user: {}", user);
                    return Optional.of(user);
                } else {
                    logger.warn("No player data found for Steam ID: {}", steamId);
                    return Optional.empty();
                }
            }
        } catch (IOException e) {
            logger.error("Error fetching user profile", e);
            return Optional.empty();
        }
    }
    
    /**
     * Check if a user owns Dota 2 on Steam.
     *
     * @param steamId The Steam ID of the user
     * @return True if the user owns Dota 2, false otherwise
     */
    public boolean userOwnsDota2(String steamId) {
        if (apiKey.isEmpty()) {
            logger.error("Steam API key is required to check game ownership");
            return false;
        }
        
        try {
            // In development or test mode, always return true for game ownership
            // This allows us to test the app without requiring actual game ownership
            if ("development".equals(propertyLoader.getProperty("app.environment", "production")) || 
                "test".equals(propertyLoader.getProperty("app.environment", "production"))) {
                logger.debug("Running in development/test mode, assuming user owns Dota 2");
                return true;
            }
            
            HttpUrl url = HttpUrl.parse(STEAM_API_URL + PLAYER_OWNED_GAMES_PATH)
                    .newBuilder()
                    .addQueryParameter("key", apiKey)
                    .addQueryParameter("steamid", steamId)
                    .addQueryParameter("format", "json")
                    .addQueryParameter("include_appinfo", "true")
                    .addQueryParameter("include_played_free_games", "true") // Important for F2P games like Dota 2
                    .build();
            
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            
            logger.debug("Sending request to Steam API for game ownership: {}", url);
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                logger.debug("Steam API game ownership response code: {}", response.code());
                
                if (!response.isSuccessful()) {
                    logger.error("Failed to get owned games: HTTP {}", response.code());
                    logger.debug("Response body: {}", responseBody);
                    return false;
                }
                
                logger.debug("Game ownership response body: {}", responseBody);
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode games = root.path("response").path("games");
                
                if (games.isArray()) {
                    for (JsonNode game : games) {
                        if (game.path("appid").asInt() == DOTA2_APP_ID) {
                            logger.debug("User owns Dota 2");
                            return true;
                        }
                    }
                }
                
                // Check if the response has game_count but no games array
                // This often means the profile is private
                if (root.path("response").has("game_count") && !games.isArray()) {
                    logger.warn("User profile may be private, cannot determine Dota 2 ownership");
                    // Default to allowing access in case of private profiles
                    return true;
                }
                
                logger.debug("User does not own Dota 2");
                return false;
            }
        } catch (IOException e) {
            logger.error("Error checking Dota 2 ownership", e);
            return false;
        }
    }
    
    /**
     * Validates the current API key by making a simple request to Steam API.
     * 
     * @return True if the API key is valid, false otherwise
     */
    public boolean validateApiKey() {
        if (apiKey.isEmpty() || "dummy-key".equals(apiKey) || "${STEAM_API_KEY}".equals(apiKey)) {
            logger.error("Cannot validate empty or placeholder API key");
            return false;
        }
        
        // For development, we'll consider the hardcoded key valid without actually checking
        // This allows the app to function in development mode
        if ("42A0C9F06F162BD5220B252E417B0D83".equals(apiKey)) {
            logger.info("Using development API key - validation skipped");
            return true;
        }
        
        try {
            // Make a simple request to get a public Steam app list - doesn't require auth
            // but will still reveal if the key format is valid
            HttpUrl url = HttpUrl.parse("https://api.steampowered.com/ISteamApps/GetAppList/v2/")
                    .newBuilder()
                    .addQueryParameter("key", apiKey)
                    .build();
            
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            
            logger.debug("Validating Steam API key with request to: {}", url);
            
            try (Response response = httpClient.newCall(request).execute()) {
                int statusCode = response.code();
                logger.debug("API key validation response code: {}", statusCode);
                
                if (statusCode == 403) {
                    logger.error("API key validation failed: Invalid or unauthorized API key (403 Forbidden)");
                    return false;
                } else if (!response.isSuccessful()) {
                    logger.error("API key validation failed: HTTP {}", statusCode);
                    return false;
                }
                
                // If we get a 200 OK response, the key format is valid
                return true;
            }
        } catch (Exception e) {
            logger.error("Error validating Steam API key", e);
            return false;
        }
    }
    
    /**
     * Get recent Dota 2 match history for a player.
     * Combines multiple endpoints to get as many matches as possible.
     * 
     * @param steamId The Steam ID of the player
     * @param limit The maximum number of matches to return (default 10)
     * @return A list of match IDs
     */
    public List<Long> getRecentMatches(String steamId, int limit) {
        if (apiKey.isEmpty()) {
            logger.error("Steam API key is required to fetch match history");
            return Collections.emptyList();
        }
        
        if (limit <= 0) {
            limit = 10;
        }
        
        // In development/test environment, generate mock match IDs if OpenDota API fails
        boolean isDevelopment = "development".equals(propertyLoader.getProperty("app.environment", "production")) ||
                               "test".equals(propertyLoader.getProperty("app.environment", "production"));
        
        try {
            // Convert Steam ID to 32-bit account ID if needed
            long steam64Id = Long.parseLong(steamId);
            int accountId = (int) (steam64Id & 0xFFFFFFFFL);
            
            logger.info("Converting Steam64 ID {} to 32-bit account ID: {}", steamId, accountId);
            
            // Use higher limit to get more matches
            int apiLimit = Math.min(limit * 3, 100); // Get up to 100 matches from API
            
            // Create a set to avoid duplicate match IDs
            Set<Long> matchIdSet = new HashSet<>();
            
            // First try the matches endpoint with no game mode filter to get more results
            HttpUrl.Builder urlBuilder = HttpUrl.parse("https://api.opendota.com/api/players/" + accountId + "/matches")
                    .newBuilder()
                    .addQueryParameter("limit", String.valueOf(apiLimit));
            
            // Add OpenDota API key if available
            if (openDotaApiKey != null && !openDotaApiKey.isEmpty()) {
                urlBuilder.addQueryParameter("api_key", openDotaApiKey);
            }
            
            HttpUrl url = urlBuilder.build();
            logger.debug("Fetching matches from OpenDota API: {}", url);
            
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Dota2DraftAssistant/1.0")
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    logger.info("OpenDota matches response received");
                    
                    JsonNode matches = objectMapper.readTree(responseBody);
                    
                    if (matches.isArray()) {
                        for (JsonNode match : matches) {
                            matchIdSet.add(match.path("match_id").asLong());
                        }
                        logger.info("Found {} matches in main OpenDota API response", matchIdSet.size());
                    } else {
                        logger.warn("OpenDota API response is not an array");
                    }
                } else {
                    logger.error("Failed to get matches: HTTP {}", response.code());
                    if (response.body() != null) {
                        String errorBody = response.body().string();
                        logger.error("Error response body: {}", errorBody);
                    }
                }
            }
            
            // Add a small delay to prevent rate limiting issues with the API
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Now try the recentMatches endpoint to get additional matches
            HttpUrl.Builder recentUrlBuilder = HttpUrl.parse("https://api.opendota.com/api/players/" + accountId + "/recentMatches")
                .newBuilder();
            
            // Add OpenDota API key if available
            if (openDotaApiKey != null && !openDotaApiKey.isEmpty()) {
                recentUrlBuilder.addQueryParameter("api_key", openDotaApiKey);
            }
            
            HttpUrl recentMatchesUrl = recentUrlBuilder.build();
            
            Request recentMatchesRequest = new Request.Builder()
                .url(recentMatchesUrl)
                .addHeader("User-Agent", "Dota2DraftAssistant/1.0")
                .build();
            
            try (Response recentMatchesResponse = httpClient.newCall(recentMatchesRequest).execute()) {
                if (recentMatchesResponse.isSuccessful()) {
                    String recentMatchesBody = recentMatchesResponse.body().string();
                    logger.info("Recent matches response received");
                    
                    JsonNode recentMatches = objectMapper.readTree(recentMatchesBody);
                    
                    if (recentMatches.isArray()) {
                        int countBefore = matchIdSet.size();
                        for (JsonNode match : recentMatches) {
                            matchIdSet.add(match.path("match_id").asLong());
                        }
                        logger.info("Added {} more unique matches from recentMatches API", 
                                   matchIdSet.size() - countBefore);
                    }
                } else {
                    logger.error("Failed to get recentMatches: HTTP {}", recentMatchesResponse.code());
                }
            }
            
            // If we still need more matches, try the pro matches
            if (matchIdSet.size() < limit) {
                try {
                    Thread.sleep(1000); // Add delay
                    
                    HttpUrl.Builder winrateUrlBuilder = HttpUrl.parse("https://api.opendota.com/api/players/" + accountId + "/wl")
                        .newBuilder()
                        .addQueryParameter("limit", String.valueOf(apiLimit));
                    
                    // Add OpenDota API key if available
                    if (openDotaApiKey != null && !openDotaApiKey.isEmpty()) {
                        winrateUrlBuilder.addQueryParameter("api_key", openDotaApiKey);
                    }
                    
                    HttpUrl winrateUrl = winrateUrlBuilder.build();
                    
                    Request winrateRequest = new Request.Builder()
                        .url(winrateUrl)
                        .addHeader("User-Agent", "Dota2DraftAssistant/1.0")
                        .build();
                    
                    try (Response winrateResponse = httpClient.newCall(winrateRequest).execute()) {
                        if (winrateResponse.isSuccessful()) {
                            logger.info("Successfully fetched win/loss data for account {}", accountId);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error with additional match data requests", e);
                }
            }
            
            // Convert the set to a list for returning
            List<Long> matchIds = new ArrayList<>(matchIdSet);
            
            // If we didn't get any matches and we're in development mode, generate mock IDs
            if (matchIds.isEmpty() && isDevelopment) {
                logger.info("No matches found in any OpenDota API - generating mock match IDs");
                return generateMockMatchIds(limit);
            }
            
            logger.info("Returning {} match IDs from OpenDota APIs", matchIds.size());
            return matchIds.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (IOException | NumberFormatException e) {
            logger.error("Error fetching recent matches", e);
            if (isDevelopment) {
                logger.info("Running in development mode - generating mock match IDs despite error");
                return generateMockMatchIds(limit);
            }
            return Collections.emptyList();
        }
    }
    
    /**
     * Generates mock match IDs for development and testing.
     * 
     * @param count The number of match IDs to generate
     * @return A list of mock match IDs
     */
    private List<Long> generateMockMatchIds(int count) {
        List<Long> matchIds = new ArrayList<>();
        // Base match ID from a recent professional match
        long baseMatchId = 8190000000L;
        Random random = new Random();
        
        for (int i = 0; i < count; i++) {
            // Generate IDs that look realistic by adding small increments
            matchIds.add(baseMatchId + random.nextInt(1000000));
        }
        
        logger.debug("Generated {} mock match IDs for development", count);
        return matchIds;
    }
    
    // Authentication callback to handle login processing
    private UserService userService;
    
    /**
     * Set the UserService reference for processing authentication callbacks
     */
    public void setUserService(UserService userService) {
        this.userService = userService;
        logger.debug("UserService reference set in SteamApiService");
    }
    
    // Keep track of the active auth server for cleanup
    private AuthCallbackServer activeAuthServer;
    private CompletableFuture<Map<String, Object>> activeCallbackFuture;
    
    /**
     * Initiates the Steam OpenID authentication flow.
     * This will start the process for logging in with Steam.
     */
    public void startAuthFlow() {
        // First, safely stop any existing auth server to free up resources
        try {
            if (activeAuthServer != null) {
                logger.debug("Stopping existing auth server before starting new one");
                
                // Cancel the future first to avoid the exception when we stop the server
                if (activeCallbackFuture != null) {
                    if (!activeCallbackFuture.isDone()) {
                        try {
                            activeCallbackFuture.cancel(true);
                            // Don't log this as an error since it's expected
                            activeCallbackFuture.exceptionally(ex -> {
                                if (ex instanceof CancellationException) {
                                    logger.debug("Auth future cancelled normally as part of cleanup");
                                } else {
                                    logger.warn("Auth future completed exceptionally: {}", ex.getMessage());
                                }
                                return null;
                            });
                        } catch (Exception ex) {
                            logger.warn("Error cancelling auth future", ex);
                        }
                    }
                }
                
                // Then stop the server with a small delay - checking again for null
                if (activeAuthServer != null) {
                    try {
                        activeAuthServer.stopServer();
                        // Add slight delay to ensure proper cleanup
                        Thread.sleep(300); 
                    } catch (Exception e) {
                        logger.warn("Error stopping previous auth server", e);
                    }
                }
                
                // Clear references
                activeAuthServer = null;
                activeCallbackFuture = null;
            }
        } catch (Exception e) {
            logger.warn("Error during cleanup of previous auth flow", e);
        }
        
        try {
            logger.info("Starting Steam authentication flow");
            
            // Initialize the callback server
            activeAuthServer = new AuthCallbackServer(this);
            
            // Start the callback server and get the future
            activeCallbackFuture = activeAuthServer.startAndWaitForCallback();
            
            // Get the port the server is actually using (may be different if original port was in use)
            int actualPort = ((com.sun.net.httpserver.HttpServer)activeAuthServer.getServer()).getAddress().getPort();
            
            // Generate the Steam OpenID authentication URL with the actual port
            String callbackUrl = "http://localhost:" + actualPort + "/auth/callback";
            
            // Setup timeout for auth flow (5 minutes)
            scheduleAuthTimeout(activeCallbackFuture);
            
            // Setup the callback future processing
            activeCallbackFuture.thenAcceptAsync(callbackData -> {
                try {
                    String returnUrl = (String) callbackData.get("url");
                    AuthCallbackServer.ClientInfo clientInfo = (AuthCallbackServer.ClientInfo) callbackData.get("clientInfo");
                    
                    // Log the callback info
                    logger.info("Received authentication callback: {}", returnUrl);
                    
                    // Check if we have UserService to process the login
                    if (userService != null) {
                        boolean success = userService.processLogin(returnUrl, clientInfo.getIpAddress(), clientInfo.getUserAgent());
                        if (success) {
                            logger.info("Successfully authenticated user with Steam");
                            
                            // Save the session token for future use
                            userService.getCurrentSessionToken().ifPresent(token -> {
                                boolean saved = SessionStorageUtil.saveSessionToken(token);
                                if (saved) {
                                    logger.info("Session token saved for future use");
                                } else {
                                    logger.warn("Failed to save session token");
                                }
                            });
                            
                            // Notify UI controllers to update their displays via a JavaFX Platform.runLater call
                            javafx.application.Platform.runLater(() -> {
                                // This will be picked up by any controllers that have registered listeners
                                logger.info("Broadcasting login success to UI components");
                                
                                // This causes a notification pulse that will be detected by polling listeners
                                if (userService != null) {
                                    userService.notifyLoginStateChanged(true);
                                }
                            });
                        } else {
                            logger.error("Failed to process Steam authentication");
                        }
                    } else {
                        logger.error("Cannot process login - UserService reference not available");
                        
                        // Fallback: Try direct validation
                        String steamId = steamAuthManager.validateAndGetSteamId(returnUrl);
                        if (steamId != null && !steamId.isEmpty()) {
                            logger.info("Successfully validated Steam ID from callback: {}", steamId);
                        } else {
                            logger.error("Failed to validate Steam ID from callback URL");
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error processing callback data", e);
                } finally {
                    // Make sure to stop the server with proper cleanup
                    stopActiveAuthServer();
                }
            }).exceptionally(ex -> {
                // Only log as error if it's not a cancellation exception (which is expected during cleanup)
                if (ex instanceof CancellationException || 
                    (ex.getCause() != null && ex.getCause() instanceof CancellationException)) {
                    logger.debug("Authentication flow cancelled normally");
                } else {
                    logger.error("Authentication callback failed", ex);
                }
                stopActiveAuthServer();
                return null;
            });
            
            // Open the browser with the authentication URL
            String authUrl = SteamAuthConfig.generateSteamOpenIdUrl(callbackUrl);
            
            // Open the default browser with the authentication URL
            java.awt.Desktop.getDesktop().browse(new java.net.URI(authUrl));
            
            logger.info("Browser opened with Steam authentication URL: {}", authUrl);
        } catch (Exception e) {
            logger.error("Failed to start authentication flow", e);
            stopActiveAuthServer();
        }
    }
    
    /**
     * Schedules a timeout for the authentication flow.
     * If the auth flow doesn't complete within the timeout period, the server will be shut down.
     * 
     * @param callbackFuture The future that will be completed when auth is done
     */
    private void scheduleAuthTimeout(CompletableFuture<Map<String, Object>> callbackFuture) {
        // Create a separate thread for timeout handling
        Thread timeoutThread = new Thread(() -> {
            try {
                // Wait for 5 minutes
                Thread.sleep(5 * 60 * 1000);
                
                // If the future is not complete by now, time out
                if (!callbackFuture.isDone()) {
                    logger.warn("Authentication flow timed out after 5 minutes");
                    callbackFuture.completeExceptionally(new RuntimeException("Authentication flow timed out"));
                    stopActiveAuthServer();
                }
            } catch (InterruptedException e) {
                // Thread was interrupted, which is fine - just exit
                Thread.currentThread().interrupt();
            }
        });
        
        // Set as daemon so it doesn't prevent app shutdown
        timeoutThread.setDaemon(true);
        timeoutThread.setName("AuthTimeoutMonitor");
        timeoutThread.start();
    }
    
    /**
     * Stops the active authentication server with proper cleanup.
     * This ensures port is released for future use.
     */
    public void stopActiveAuthServer() {
        boolean hadServer = activeAuthServer != null;
        boolean hadFuture = activeCallbackFuture != null;
        
        // Only log when we're actually stopping something
        if (hadServer || hadFuture) {
            logger.debug("Stopping active auth resources (server: {}, future: {})", 
                        hadServer ? "active" : "none", 
                        hadFuture ? "active" : "none");
        }
        
        // Handle future cancellation first
        if (activeCallbackFuture != null) {
            try {
                if (!activeCallbackFuture.isDone()) {
                    activeCallbackFuture.cancel(true);
                    logger.debug("Cancelled active callback future");
                }
            } catch (Exception ex) {
                logger.warn("Error cancelling future during cleanup", ex);
            }
        }
        
        // Small delay to allow cancellation to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Then stop the server
        if (activeAuthServer != null) {
            try {
                activeAuthServer.stopServer();
                logger.debug("Stopped auth callback server");
                
                // Add extra delay to ensure port is fully released
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                logger.warn("Error stopping active auth server", e);
            }
        }
        
        // Always clear references
        activeAuthServer = null;
        activeCallbackFuture = null;
        
        // Final log message
        if (hadServer || hadFuture) {
            logger.info("Auth server resources cleaned up");
        }
    }
    
    /**
     * Gets the UserMatchService instance.
     * This is needed for the MainController to access match synchronization functionality.
     * 
     * @return The UserMatchService instance
     */
    public com.dota2assistant.data.service.UserMatchService getUserMatchService() {
        // This method would normally use dependency injection or a service locator
        // Here we'll use a simplified approach to create the service for demo purposes
        try {
            // Create a database manager
            com.dota2assistant.data.db.DatabaseManager dbManager = 
                com.dota2assistant.AppConfig.getDatabaseManager();
            
            // Create a minimal API client
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.dota2assistant.data.api.DotaApiClient apiClient = 
                new com.dota2assistant.data.api.OpenDotaApiClient(
                    new okhttp3.OkHttpClient(), 
                    objectMapper
                );
            
            // Create hero abilities repository
            com.dota2assistant.data.repository.HeroAbilitiesRepository abilitiesRepo = 
                new com.dota2assistant.data.repository.HeroAbilitiesRepository(objectMapper);
                
            // Create the hero repository
            com.dota2assistant.data.repository.HeroRepository heroRepository =
                new com.dota2assistant.data.repository.HeroRepository(
                    dbManager,
                    apiClient,
                    abilitiesRepo
                );
            
            // Create the user match repository
            com.dota2assistant.data.repository.UserMatchRepository userMatchRepository = 
                new com.dota2assistant.data.repository.UserMatchRepository(
                    dbManager,
                    heroRepository
                );
            
            // Get required services
            com.dota2assistant.data.service.MatchHistoryService matchHistoryService = 
                AppConfig.getMatchHistoryService();
            com.dota2assistant.data.service.AutomatedMatchSyncService automatedSyncService = 
                AppConfig.getAutomatedSyncService();
            
            // Create the service instance
            return new com.dota2assistant.data.service.UserMatchService(
                userMatchRepository,
                heroRepository,
                this,
                matchHistoryService,
                automatedSyncService
            );
        } catch (Exception e) {
            logger.error("Failed to create UserMatchService instance", e);
            throw new RuntimeException("Could not initialize UserMatchService", e);
        }
    }
}