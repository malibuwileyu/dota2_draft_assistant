package com.dota2assistant.data.service;

import com.dota2assistant.data.model.PlayerMatch;
import com.dota2assistant.data.model.PlayerHeroStat;
import com.dota2assistant.data.repository.UserMatchRepository;
import com.dota2assistant.util.PropertyLoader;
import com.dota2assistant.data.db.DatabaseManager;
import com.dota2assistant.data.api.DotaApiClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.springframework.stereotype.Service;

/**
 * Service for retrieving and managing player match history.
 * Handles API calls, data processing, and synchronization.
 */
@Service
public class MatchHistoryService {
    private static final Logger LOGGER = Logger.getLogger(MatchHistoryService.class.getName());
    
    /**
     * Gets the current sync status for a player.
     * 
     * @param accountId The player's account ID
     * @return Map containing sync status information
     */
    public Map<String, Object> getMatchHistorySyncStatus(long accountId) {
        return userMatchRepository != null ? 
               userMatchRepository.getMatchHistorySyncStatus(accountId) : 
               new HashMap<>();
    }
    
    private final UserMatchRepository userMatchRepository;
    private final DatabaseManager databaseManager;
    private final String steamApiKey;
    private final String openDotaApiKey;
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final ScheduledExecutorService schedulerService;
    private final Map<Long, CompletableFuture<Boolean>> activeSyncs;
    private final MatchEnrichmentService matchEnrichmentService;
    
    // Constants for API and processing
    private static final int MATCHES_PER_REQUEST = 100;
    private static final int MAX_MATCHES_TO_RETRIEVE = 500;
    private static final String OPENDOTA_API_BASE = "https://api.opendota.com/api";
    private static final String STEAM_API_BASE_URL = "https://api.steampowered.com/IDOTA2Match_570";
    private static final int MAX_CONCURRENT_REQUESTS = 5;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int RATE_LIMIT_DELAY_MS = 1100; // Slightly over 1 second for Steam API rate limits
    
    public MatchHistoryService(
            UserMatchRepository userMatchRepository, 
            DatabaseManager databaseManager, 
            PropertyLoader propertyLoader,
            MatchEnrichmentService matchEnrichmentService) {
        this.userMatchRepository = userMatchRepository;
        this.databaseManager = databaseManager;
        this.steamApiKey = propertyLoader.getProperty("steam.api.key", "");
        this.openDotaApiKey = propertyLoader.getProperty("opendota.api.key", "");
        this.matchEnrichmentService = matchEnrichmentService;
        
        if (openDotaApiKey != null && !openDotaApiKey.isEmpty()) {
            LOGGER.info("MatchHistoryService initialized with OpenDota API key (primary)");
        } else {
            LOGGER.warning("No OpenDota API key configured - will use free tier (60 calls/min)");
        }
        
        if (steamApiKey == null || steamApiKey.isEmpty()) {
            LOGGER.warning("No Steam API key configured - Steam API fallback disabled");
        }
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        this.executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
        this.schedulerService = Executors.newScheduledThreadPool(1);
        this.activeSyncs = new ConcurrentHashMap<>();
        
        // Start a scheduled task to check for matches that need to be synchronized
        schedulerService.scheduleAtFixedRate(this::checkScheduledSyncs, 1, 15, TimeUnit.MINUTES);
    }
    
    /**
     * Alternative constructor without the enrichment service, for backward compatibility.
     */
    public MatchHistoryService(UserMatchRepository userMatchRepository, DatabaseManager databaseManager, PropertyLoader propertyLoader) {
        this(userMatchRepository, databaseManager, propertyLoader, null);
    }
    
    /**
     * Starts synchronizing match history for a player.
     * 
     * @param accountId The player's Steam account ID
     * @param fullSync Whether to perform a full sync (all matches) or incremental
     * @return CompletableFuture that completes when sync is done
     */
    public CompletableFuture<Boolean> syncPlayerMatchHistory(long accountId, boolean fullSync) {
        // Check if sync is already in progress for this player
        if (activeSyncs.containsKey(accountId) && !activeSyncs.get(accountId).isDone()) {
            LOGGER.info("Sync already in progress for account " + accountId);
            return activeSyncs.get(accountId);
        }
        
        // Mark sync as started in database
        userMatchRepository.startMatchHistorySync(accountId);
        
        // Create and store the future
        CompletableFuture<Boolean> syncFuture = CompletableFuture.supplyAsync(
            () -> retrieveAndProcessMatches(accountId, fullSync),
            executorService
        );
        
        activeSyncs.put(accountId, syncFuture);
        
        // Add a callback to clean up after completion
        syncFuture.whenComplete((result, ex) -> {
            activeSyncs.remove(accountId);
            if (ex != null) {
                LOGGER.log(Level.SEVERE, "Error during match history sync for account " + accountId, ex);
            }
        });
        
        return syncFuture;
    }
    
    /**
     * Main method to retrieve and process matches for a player.
     * 
     * @param accountId The player's Steam account ID
     * @param fullSync Whether to do a full sync or incremental
     * @return True if successful
     */
    private boolean retrieveAndProcessMatches(long accountId, boolean fullSync) {
        try {
            LOGGER.info("Starting match history sync for account " + accountId + 
                      (fullSync ? " (full sync)" : " (incremental sync)"));
            
            Long lastMatchId = null;
            if (!fullSync) {
                // Get the last synced match ID for incremental sync
                Map<String, Object> syncStatus = userMatchRepository.getMatchHistorySyncStatus(accountId);
                if (syncStatus != null && !syncStatus.isEmpty()) {
                    lastMatchId = (Long) syncStatus.get("lastMatchId"); 
                }
            }
            
            List<JSONObject> matches = new ArrayList<>();
            int totalMatches = retrieveMatchHistory(accountId, lastMatchId, matches);
            
            if (matches.isEmpty()) {
                LOGGER.info("No new matches found for account " + accountId);
                userMatchRepository.updateMatchHistorySyncStatus(accountId, 
                                                          totalMatches, lastMatchId, false);
                return true;
            }
            
            // Process matches and store in database
            int processedCount = processMatches(accountId, matches);
            
            // Get ID of the most recent match
            long mostRecentMatchId = matches.stream()
                .mapToLong(m -> m.getLong("match_id"))
                .max()
                .orElse(0);
            
            // Update sync status in database
            userMatchRepository.updateMatchHistorySyncStatus(
                accountId, 
                totalMatches,
                mostRecentMatchId > 0 ? mostRecentMatchId : lastMatchId,
                totalMatches < MAX_MATCHES_TO_RETRIEVE // If fewer than limit, assume we have all matches
            );
            
            LOGGER.info("Completed match history sync for account " + accountId + 
                      ". Processed " + processedCount + " matches.");
            
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error syncing match history", e);
            return false;
        }
    }
    
    /**
     * Retrieves match history from the Steam API.
     * 
     * @param accountId The player's Steam account ID
     * @param lastMatchId Last match ID for incremental sync
     * @param matchesResult List to store retrieved matches
     * @return Total number of matches found for the player
     */
    private int retrieveMatchHistory(long accountId, Long lastMatchId, List<JSONObject> matchesResult) {
        // Try OpenDota API first (more reliable and doesn't require API key for basic access)
        try {
            LOGGER.info("Attempting to retrieve match history using OpenDota API");
            int matches = retrieveMatchHistoryFromOpenDota(accountId, lastMatchId, matchesResult);
            if (matches > 0) {
                LOGGER.info("Successfully retrieved " + matches + " matches from OpenDota API");
                return matches;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "OpenDota API failed, falling back to Steam API", e);
        }
        
        // Fall back to Steam API if OpenDota fails
        if (steamApiKey != null && !steamApiKey.isEmpty()) {
            LOGGER.info("Attempting to retrieve match history using Steam API");
            try {
                return retrieveMatchHistoryFromSteam(accountId, lastMatchId, matchesResult);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Steam API also failed", e);
                return 0;
            }
        } else {
            LOGGER.warning("Steam API fallback unavailable (no API key configured)");
            return 0;
        }
    }
    
    /**
     * Retrieves match history from OpenDota API.
     */
    private int retrieveMatchHistoryFromOpenDota(long accountId, Long lastMatchId, List<JSONObject> matchesResult) 
            throws Exception {
        int retrievedMatches = 0;
        int offset = 0;
        int maxIterations = (MAX_MATCHES_TO_RETRIEVE / MATCHES_PER_REQUEST) + 1;
        
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            // Build OpenDota API URL
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(OPENDOTA_API_BASE)
                     .append("/players/")
                     .append(accountId)
                     .append("/matches?limit=")
                     .append(MATCHES_PER_REQUEST)
                     .append("&offset=")
                     .append(offset);
            
            // Add API key if available
            if (openDotaApiKey != null && !openDotaApiKey.isEmpty()) {
                urlBuilder.append("&api_key=").append(openDotaApiKey);
            }
            
            String url = urlBuilder.toString();
            LOGGER.fine("Requesting match history from OpenDota: " + url);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Dota2DraftAssistant/1.0")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("OpenDota API error: " + response.statusCode());
            }
            
            // Parse OpenDota response (returns array of match objects)
            JSONArray matches = new JSONArray(response.body());
            
            if (matches.length() == 0) {
                break; // No more matches
            }
            
            // Process each match
            boolean reachedLastMatch = false;
            for (int i = 0; i < matches.length(); i++) {
                JSONObject match = matches.getJSONObject(i);
                long matchId = match.getLong("match_id");
                
                // If we've reached the last synced match, stop
                if (lastMatchId != null && matchId <= lastMatchId) {
                    reachedLastMatch = true;
                    break;
                }
                
                matchesResult.add(match);
                retrievedMatches++;
            }
            
            if (reachedLastMatch || retrievedMatches >= MAX_MATCHES_TO_RETRIEVE) {
                break;
            }
            
            offset += MATCHES_PER_REQUEST;
            
            // Respect rate limits (60 calls/min for free tier)
            Thread.sleep(RATE_LIMIT_DELAY_MS);
        }
        
        return retrievedMatches;
    }
    
    /**
     * Retrieves match history from Steam API (fallback).
     */
    private int retrieveMatchHistoryFromSteam(long accountId, Long lastMatchId, List<JSONObject> matchesResult) 
            throws Exception {
        int matchesFound = 0;
        Long startAtMatchId = null;
        int retrievedMatches = 0;
        int requests = 0;
        
        while (retrievedMatches < MAX_MATCHES_TO_RETRIEVE && requests < 10) {
            String url = STEAM_API_BASE_URL + "/GetMatchHistory/V001/" +
                       "?key=" + steamApiKey +
                       "&account_id=" + accountId +
                       "&matches_requested=" + MATCHES_PER_REQUEST;
            
            if (startAtMatchId != null) {
                url += "&start_at_match_id=" + startAtMatchId;
            }
            
            LOGGER.fine("Requesting match history from Steam: " + url);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Steam API error: " + response.statusCode());
            }
            
            // Parse response
            JSONObject responseJson = new JSONObject(response.body());
            JSONObject result = responseJson.getJSONObject("result");
            
            if (result.getInt("status") != 1) {
                throw new RuntimeException("Steam API returned error status: " + result.getInt("status"));
            }
            
            matchesFound = result.getInt("total_results");
            JSONArray matches = result.getJSONArray("matches");
            
            if (matches.length() == 0) {
                break;
            }
            
            // Process each match
            boolean reachedLastMatch = false;
            for (int i = 0; i < matches.length(); i++) {
                JSONObject match = matches.getJSONObject(i);
                long matchId = match.getLong("match_id");
                
                if (lastMatchId != null && matchId <= lastMatchId) {
                    reachedLastMatch = true;
                    break;
                }
                
                matchesResult.add(match);
                startAtMatchId = matchId - 1;
            }
            
            retrievedMatches += matches.length();
            requests++;
            
            if (reachedLastMatch) {
                break;
            }
            
            Thread.sleep(RATE_LIMIT_DELAY_MS);
        }
        
        return matchesFound;
    }
    
    /**
     * Processes match data and stores it in the database.
     * 
     * @param accountId The player's account ID
     * @param matches List of match JSON objects
     * @return Number of matches processed
     */
    private int processMatches(long accountId, List<JSONObject> matches) {
        int processedCount = 0;
        List<Long> matchesNeedingEnrichment = new ArrayList<>();
        
        LOGGER.info("Processing " + matches.size() + " matches for account ID " + accountId);
                  
        // Process match basic data separately from hero performance updates to isolate transaction failures
        processBasicMatchData(accountId, matches, matchesNeedingEnrichment);
        
        try {
            // Try to update player hero performance in a separate transaction
            updatePlayerHeroPerformanceWithRetry(accountId);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating player hero statistics", e);
            // Continue anyway since the basic match data was already processed
        }
        
        // Queue matches for enrichment if we have the enrichment service
        if (!matchesNeedingEnrichment.isEmpty()) {
            LOGGER.info("Queueing " + matchesNeedingEnrichment.size() + " matches for enrichment");
            if (matchEnrichmentService != null) {
                try {
                    int queued = matchEnrichmentService.enqueueMatchesForEnrichment(matchesNeedingEnrichment, false);
                    LOGGER.info("Successfully queued " + queued + " matches for enrichment");
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error queuing matches for enrichment", e);
                    LOGGER.warning("Failed to queue matches for enrichment - service may be unavailable");
                }
            } else {
                LOGGER.warning("Match enrichment service not available, " + matchesNeedingEnrichment.size() + 
                             " matches will not be enriched");
            }
        }
        
        return processedCount;
    }
    
    /**
     * Process basic match data into the database
     * 
     * @param accountId The player's account ID
     * @param matches List of matches to process
     * @param matchesNeedingEnrichment Output list to collect matches that need enrichment
     * @return Number of matches processed successfully
     */
    private int processBasicMatchData(long accountId, List<JSONObject> matches, List<Long> matchesNeedingEnrichment) {
        int processedCount = 0;
        
        // Process each match in its own transaction for better isolation and error recovery
        for (JSONObject match : matches) {
            Connection conn = null;
            try {
                // Get a fresh connection for each match to avoid transaction issues
                conn = databaseManager.getConnection();
                if (conn == null) {
                    LOGGER.warning("Failed to obtain database connection for match processing");
                    continue;
                }
                
                conn.setAutoCommit(false);
                
                // Prepare statements for this match
                String insertMatchSql = "INSERT INTO matches (id, start_time, duration, radiant_win, game_mode, has_details) " +
                                       "VALUES (?, ?, ?, ?, ?, ?) " +
                                       "ON CONFLICT (id) DO NOTHING";
                
                // Don't try to dynamically create constraint anymore - we do this through migrations now
                // Just check if the constraint exists for reporting purposes
                boolean constraintExists = false;
                try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT 1 FROM pg_constraint WHERE conname = 'match_players_match_account_unique'")) {
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        constraintExists = rs.next();
                    }
                } catch (SQLException e) {
                    LOGGER.warning("Could not check constraint existence: " + e.getMessage());
                }
                
                String insertMatchPlayerSql;
                if (constraintExists) {
                    insertMatchPlayerSql = "INSERT INTO match_players " +
                                           "(match_id, account_id, hero_id, is_radiant, kills, deaths, assists, won) " +
                                           "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                                           "ON CONFLICT (match_id, account_id) DO NOTHING";
                } else {
                    // Fallback to regular INSERT if the constraint doesn't exist
                    // First check if the player is already in this match to avoid duplicates
                    insertMatchPlayerSql = "INSERT INTO match_players " +
                                           "(match_id, account_id, hero_id, is_radiant, kills, deaths, assists, won) " +
                                           "SELECT ?, ?, ?, ?, ?, ?, ?, ? WHERE NOT EXISTS " +
                                           "(SELECT 1 FROM match_players WHERE match_id = ? AND account_id = ?)";
                }
                
                long matchId = match.getLong("match_id");
                boolean needsEnrichment = false;
                
                // Extract match data - directly from match object instead of match_detail
                long startTime = match.has("start_time") ? match.getLong("start_time") : 0;
                int duration = match.has("duration") ? match.getInt("duration") : 0;
                boolean radiantWin = match.has("radiant_win") ? match.getBoolean("radiant_win") : false;
                int gameMode = match.has("game_mode") ? match.getInt("game_mode") : 0;
                
                // If important fields aren't available, mark for enrichment
                if (startTime == 0 || duration == 0 || !match.has("radiant_win")) {
                    StringBuilder missingFields = new StringBuilder();
                    if (startTime == 0) missingFields.append("start_time ");
                    if (duration == 0) missingFields.append("duration ");
                    if (!match.has("radiant_win")) missingFields.append("radiant_win ");
                    if (!match.has("game_mode")) missingFields.append("game_mode ");
                    if (!match.has("players") && !match.has("all_players")) missingFields.append("players ");
                    
                    LOGGER.info("Match " + matchId + " missing fields: " + missingFields.toString() + 
                                " - Adding to enrichment queue");
                    
                    // Mark this match as needing enrichment
                    needsEnrichment = true;
                    matchesNeedingEnrichment.add(matchId);
                    
                    // Log available fields for debugging at FINE level
                    if (LOGGER.isLoggable(Level.FINE)) {
                        List<String> availableKeys = new ArrayList<>();
                        for (String key : match.keySet()) {
                            availableKeys.add(key);
                        }
                        LOGGER.fine("Match " + matchId + " available fields: " + String.join(", ", availableKeys));
                    }
                }
                
                // Insert match - mark has_details as false if it needs enrichment
                try (PreparedStatement matchStmt = conn.prepareStatement(insertMatchSql)) {
                    matchStmt.setLong(1, matchId);
                    matchStmt.setTimestamp(2, new java.sql.Timestamp(startTime * 1000));
                    matchStmt.setInt(3, duration);
                    matchStmt.setBoolean(4, radiantWin);
                    matchStmt.setInt(5, gameMode);
                    matchStmt.setBoolean(6, !needsEnrichment); // has_details is false for matches needing enrichment
                    int matchInserted = matchStmt.executeUpdate();
                    if (matchInserted > 0) {
                        LOGGER.info("Inserted basic match data for match " + matchId);
                    } else {
                        LOGGER.info("Match " + matchId + " already exists in database, skipping insert");
                    }
                }
                
                // Extract player data
                JSONArray players = match.has("players") ? match.getJSONArray("players") : new JSONArray();
                
                if (players.length() == 0 && match.has("all_players")) {
                    players = match.getJSONArray("all_players");
                }
                
                boolean playerFound = false;
                try (PreparedStatement playerStmt = conn.prepareStatement(insertMatchPlayerSql)) {
                    for (int i = 0; i < players.length(); i++) {
                        JSONObject player = players.getJSONObject(i);
                    
                        // Skip if not the player we're looking for
                        if (player.has("account_id") && player.getLong("account_id") == accountId) {
                            int heroId = player.has("hero_id") ? player.getInt("hero_id") : 0;
                            boolean isRadiant = player.has("player_slot") ? player.getInt("player_slot") < 128 : true;
                            int kills = player.has("kills") ? player.getInt("kills") : 0;
                            int deaths = player.has("deaths") ? player.getInt("deaths") : 0;
                            int assists = player.has("assists") ? player.getInt("assists") : 0;
                            boolean won = (isRadiant && radiantWin) || (!isRadiant && !radiantWin);
                            
                            // Insert player match data
                            playerStmt.setLong(1, matchId);
                            playerStmt.setLong(2, accountId);
                            playerStmt.setInt(3, heroId);
                            playerStmt.setBoolean(4, isRadiant);
                            playerStmt.setInt(5, kills);
                            playerStmt.setInt(6, deaths);
                            playerStmt.setInt(7, assists);
                            playerStmt.setBoolean(8, won);
                            
                            // If we're using the NOT EXISTS query, we need to add matchId and accountId again at the end
                            if (!constraintExists) {
                                playerStmt.setLong(9, matchId);
                                playerStmt.setLong(10, accountId);
                            }
                            
                            int insertedRows = playerStmt.executeUpdate();
                            if (insertedRows > 0) {
                                playerFound = true;
                                processedCount++;
                                LOGGER.info("Successfully inserted player data for match " + matchId + ", hero ID: " + heroId);
                            } else {
                                LOGGER.info("No new player data inserted for match " + matchId + " (likely already exists)");
                            }
                        }
                    }
                }
                
                // If we didn't find the player in the match data, also mark for enrichment
                if (!playerFound && !needsEnrichment) {
                    LOGGER.info("Match " + matchId + " does not contain player data for account " + 
                              accountId + " - Adding to enrichment queue");
                    matchesNeedingEnrichment.add(matchId);
                }
                
                // If we got here, commit the transaction
                conn.commit();
                
            } catch (JSONException jsonEx) {
                // Log error for this specific match but continue processing others
                LOGGER.warning("Error processing match: " + jsonEx.getMessage() + 
                             ". Match data structure may have changed.");
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        LOGGER.log(Level.SEVERE, "Error during rollback", rollbackEx);
                    }
                }
            } catch (Exception ex) {
                LOGGER.warning("Error processing match: " + ex.getMessage());
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        LOGGER.log(Level.SEVERE, "Error during rollback", rollbackEx);
                    }
                }
            } finally {
                // Always reset connection state and close it
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException closeEx) {
                        LOGGER.warning("Failed to close connection: " + closeEx.getMessage());
                    }
                }
            }
        }
        
        LOGGER.info("Successfully inserted basic match data for " + processedCount + " matches");
        return processedCount;
    }
    
    /**
     * Updates player hero performance metrics with retry logic
     * 
     * @param accountId The player's account ID
     * @throws SQLException If all attempts fail
     */
    private void updatePlayerHeroPerformanceWithRetry(long accountId) throws SQLException {
        final int MAX_RETRIES = 3;
        int retries = 0;
        SQLException lastException = null;
        
        while (retries < MAX_RETRIES) {
            try (Connection conn = databaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Check if player_hero_performance table exists and has the right structure
                    if (!verifyHeroPerformanceTable(conn)) {
                        LOGGER.warning("Hero performance table not found or missing columns, skipping hero stats update");
                        return;
                    }
                    
                    // Update player hero performance data
                    updatePlayerHeroPerformance(accountId, conn);
                    
                    conn.commit();
                    LOGGER.info("Successfully updated player hero performance stats");
                    return; // Success
                } catch (SQLException e) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        LOGGER.log(Level.SEVERE, "Error during rollback", rollbackEx);
                    }
                    lastException = e;
                    
                    // Log the error but continue to retry
                    LOGGER.warning("Error updating hero performance (attempt " + (retries + 1) + 
                                 " of " + MAX_RETRIES + "): " + e.getMessage());
                    retries++;
                    
                    // Wait a bit before retrying
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrupted while waiting to retry", ie);
                    }
                }
            }
        }
        
        // If we get here, all retries failed
        if (lastException != null) {
            throw lastException;
        }
    }
    
    /**
     * Verifies the hero performance table exists and has the required structure
     * 
     * @param conn The database connection
     * @return true if the table exists and appears valid
     */
    private boolean verifyHeroPerformanceTable(Connection conn) throws SQLException {
        // First check if the table exists
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM information_schema.tables " +
                "WHERE table_name = 'player_hero_performance'")) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    LOGGER.warning("Table player_hero_performance does not exist");
                    return false;
                }
            }
        }
        
        // Check for the primary key to verify ON CONFLICT will work
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM information_schema.table_constraints " +
                "WHERE table_name = 'player_hero_performance' " +
                "AND constraint_type = 'PRIMARY KEY'")) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    LOGGER.warning("Table player_hero_performance does not have a primary key");
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Updates player hero performance metrics based on match history.
     * 
     * @param accountId The player's account ID
     * @param conn The database connection
     * @throws SQLException If a database error occurs
     */
    private void updatePlayerHeroPerformance(long accountId, Connection conn) throws SQLException {
        // Calculate aggregated statistics from match_players table
        String sql = "INSERT INTO player_hero_performance " +
                    "(account_id, hero_id, matches_count, wins_count, " +
                    "total_kills, total_deaths, total_assists, last_played, " +
                    "performance_score, comfort_score, pick_rate, is_comfort_pick) " +
                    "SELECT mp.account_id, mp.hero_id, " +
                    "COUNT(*) as matches_count, " +
                    "SUM(CASE WHEN (mp.is_radiant = m.radiant_win) THEN 1 ELSE 0 END) as wins_count, " +
                    "SUM(mp.kills) as total_kills, " +
                    "SUM(mp.deaths) as total_deaths, " +
                    "SUM(mp.assists) as total_assists, " +
                    "MAX(m.start_time) as last_played, " +
                    // Performance score calculation
                    "CASE WHEN COUNT(*) > 0 THEN " +
                    "  (SUM(CASE WHEN (mp.is_radiant = m.radiant_win) THEN 1 ELSE 0 END)::REAL / COUNT(*)) * 5 + " +
                    "  CASE WHEN SUM(mp.deaths) > 0 THEN " +
                    "    ((SUM(mp.kills) + SUM(mp.assists))::REAL / SUM(mp.deaths)) " +
                    "  ELSE SUM(mp.kills) + SUM(mp.assists) END * 0.2 " +
                    "ELSE 0 END as performance_score, " +
                    // Comfort score - based on matches played and success
                    "CASE WHEN COUNT(*) > 0 THEN " +
                    "  LN(GREATEST(COUNT(*), 1) + 1) * 0.7 + " +
                    "  (SUM(CASE WHEN (mp.is_radiant = m.radiant_win) THEN 1 ELSE 0 END)::REAL / COUNT(*)) * 0.3 " +
                    "ELSE 0 END as comfort_score, " +
                    // Pick rate calculation
                    "COUNT(*)::REAL / (SELECT COUNT(*) FROM match_players WHERE account_id = ?) as pick_rate, " +
                    // Comfort pick determination
                    "CASE WHEN COUNT(*) >= 5 AND (SUM(CASE WHEN (mp.is_radiant = m.radiant_win) THEN 1 ELSE 0 END)::REAL / COUNT(*)) >= 0.55 " +
                    "THEN true ELSE false END as is_comfort_pick " +
                    "FROM match_players mp " +
                    "JOIN matches m ON mp.match_id = m.id " +
                    "WHERE mp.account_id = ? " +
                    "GROUP BY mp.account_id, mp.hero_id " +
                    "ON CONFLICT (account_id, hero_id) DO UPDATE SET " +
                    "matches_count = EXCLUDED.matches_count, " +
                    "wins_count = EXCLUDED.wins_count, " +
                    "total_kills = EXCLUDED.total_kills, " +
                    "total_deaths = EXCLUDED.total_deaths, " +
                    "total_assists = EXCLUDED.total_assists, " +
                    "last_played = EXCLUDED.last_played, " +
                    "performance_score = EXCLUDED.performance_score, " +
                    "comfort_score = EXCLUDED.comfort_score, " +
                    "pick_rate = EXCLUDED.pick_rate, " +
                    "is_comfort_pick = EXCLUDED.is_comfort_pick, " +
                    "calculated_date = CURRENT_TIMESTAMP";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, accountId);
            stmt.setLong(2, accountId);
            stmt.executeUpdate();
        }
        
        // Calculate recent statistics for the player
        updateRecentStats(accountId, conn);
    }
    
    /**
     * Updates player's recent matches statistics.
     * 
     * @param accountId The player's account ID
     * @param conn The database connection
     * @throws SQLException If a database error occurs
     */
    private void updateRecentStats(long accountId, Connection conn) throws SQLException {
        String sql = "INSERT INTO player_recent_stats " +
                    "(account_id, recent_matches, recent_wins, favorite_hero_id, " +
                    "most_successful_hero_id, avg_kda) " +
                    "SELECT " +
                    "?, " + // accountId
                    "COUNT(*) as recent_matches, " +
                    "SUM(CASE WHEN (mp.is_radiant = m.radiant_win) THEN 1 ELSE 0 END) as recent_wins, " +
                    // Most played hero (favorite)
                    "(SELECT hero_id FROM match_players " +
                    " WHERE account_id = ? " +
                    " GROUP BY hero_id " +
                    " ORDER BY COUNT(*) DESC " +
                    " LIMIT 1) as favorite_hero_id, " +
                    // Most successful hero (min 3 games)
                    "(SELECT mp_inner.hero_id FROM match_players mp_inner " +
                    " JOIN matches m_inner ON mp_inner.match_id = m_inner.id " +
                    " WHERE mp_inner.account_id = ? " +
                    " GROUP BY mp_inner.hero_id " +
                    " HAVING COUNT(*) >= 3 " +
                    " ORDER BY SUM(CASE WHEN (mp_inner.is_radiant = m_inner.radiant_win) THEN 1 ELSE 0 END)::REAL / COUNT(*) DESC " +
                    " LIMIT 1) as most_successful_hero_id, " +
                    // Average KDA
                    "CASE WHEN SUM(mp.deaths) > 0 THEN " +
                    "  ((SUM(mp.kills) + SUM(mp.assists))::REAL / SUM(mp.deaths)) " +
                    "ELSE SUM(mp.kills) + SUM(mp.assists) END as avg_kda " +
                    "FROM match_players mp " +
                    "JOIN matches m ON mp.match_id = m.id " +
                    "WHERE mp.account_id = ? " +
                    "AND m.start_time >= NOW() - INTERVAL '90 days' " +
                    "ON CONFLICT (account_id) DO UPDATE SET " +
                    "recent_matches = EXCLUDED.recent_matches, " +
                    "recent_wins = EXCLUDED.recent_wins, " +
                    "favorite_hero_id = EXCLUDED.favorite_hero_id, " +
                    "most_successful_hero_id = EXCLUDED.most_successful_hero_id, " +
                    "avg_kda = EXCLUDED.avg_kda, " +
                    "calculated_date = CURRENT_TIMESTAMP";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, accountId);
            stmt.setLong(2, accountId);
            stmt.setLong(3, accountId);
            stmt.setLong(4, accountId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Gets match history for a player.
     * 
     * @param accountId The player's account ID
     * @param limit Maximum number of matches to retrieve
     * @param offset Start position for pagination
     * @return List of player matches
     */
    public List<PlayerMatch> getPlayerMatchHistory(long accountId, int limit, int offset) {
        return userMatchRepository.getPlayerMatchHistory(accountId, limit, offset);
    }
    
    /**
     * Gets hero statistics for a player.
     * 
     * @param accountId The player's account ID
     * @param limit Maximum number of heroes to retrieve
     * @return List of player hero stats
     */
    public List<PlayerHeroStat> getPlayerHeroStats(long accountId, int limit) {
        return userMatchRepository.getPlayerHeroStats((int)accountId, limit, "games");
    }
    
    /**
     * Updates details for a specific match (favorite, hidden, notes).
     * 
     * @param accountId Player account ID
     * @param matchId Match ID
     * @param isFavorite Whether the match is favorited
     * @param isHidden Whether the match is hidden
     * @param notes User notes about the match
     * @return True if successful
     */
    public boolean updateMatchDetails(long accountId, long matchId, 
                                    boolean isFavorite, boolean isHidden, String notes) {
        return userMatchRepository.updateUserMatchDetails(accountId, matchId, isFavorite, isHidden, notes);
    }
    
    /**
     * Checks for players whose match history needs to be synchronized.
     * Called periodically by the scheduler.
     */
    private void checkScheduledSyncs() {
        LOGGER.info("Checking for scheduled match history syncs...");
        
        String sql = "SELECT p.account_id, p.sync_frequency " +
                    "FROM player_match_history_sync p " +
                    "LEFT JOIN user_preferences up ON p.account_id = up.account_id " +
                    "AND up.preference_type_id = (SELECT id FROM user_preference_types " +
                    "                            WHERE name = 'match_history_refresh_frequency') " +
                    "WHERE p.sync_in_progress = 0 " + // Use 0 instead of false since the column is INTEGER
                    "AND p.next_sync_date <= CURRENT_TIMESTAMP " +
                    "AND (up.value IS NULL OR up.value != 'NEVER')";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            int syncCount = 0;
            while (rs.next()) {
                long accountId = rs.getLong("account_id");
                String frequency = rs.getString("sync_frequency");
                
                // Start a sync for this player
                LOGGER.info("Starting scheduled sync for account " + accountId);
                syncPlayerMatchHistory(accountId, false)
                    .thenAccept(success -> {
                        if (success) {
                            // Schedule next sync based on frequency
                            scheduleNextSync(accountId, frequency);
                        } else {
                            // If sync failed, schedule a retry after an hour
                            LOGGER.warning("Scheduled sync failed for account " + accountId + ". Will retry later.");
                            scheduleRetrySync(accountId);
                        }
                    });
                syncCount++;
                
                // Avoid overloading the system - limit to 5 syncs at a time
                if (syncCount >= 5) {
                    break;
                }
            }
            
            if (syncCount > 0) {
                LOGGER.info("Started " + syncCount + " scheduled syncs");
            } else {
                LOGGER.fine("No scheduled syncs were due");
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking for scheduled syncs", e);
        }
    }
    
    /**
     * Schedules the next synchronization for a player.
     * 
     * @param accountId The player's account ID
     * @param frequency How often to sync (DAILY, WEEKLY, etc.)
     */
    public void scheduleNextSync(long accountId, String frequency) {
        if (frequency == null || frequency.isEmpty()) {
            frequency = "DAILY"; // Default to daily if not specified
        }
        
        // Convert frequency string to duration
        Duration interval;
        switch (frequency.toUpperCase()) {
            case "REAL_TIME":
                interval = Duration.ofMinutes(15);
                break;
            case "HOURLY":
                interval = Duration.ofHours(1);
                break;
            case "WEEKLY":
                interval = Duration.ofDays(7);
                break;
            case "MONTHLY":
                interval = Duration.ofDays(30);
                break;
            case "NEVER":
                LOGGER.info("Sync frequency set to NEVER for account " + accountId + ", not scheduling next sync");
                return; // Don't schedule if set to never
            case "DAILY":
            default:
                interval = Duration.ofDays(1);
                break;
        }
        
        // Calculate next sync time
        LocalDateTime nextSync = LocalDateTime.now().plus(interval);
        
        // Update the database
        String sql = "UPDATE player_match_history_sync " +
                    "SET next_sync_date = ?, sync_frequency = ? " +
                    "WHERE account_id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, java.sql.Timestamp.valueOf(nextSync));
            stmt.setString(2, frequency.toUpperCase());
            stmt.setLong(3, accountId);
            stmt.executeUpdate();
            
            LOGGER.info("Scheduled next sync for account " + accountId + " at " + 
                      nextSync + " (" + frequency.toUpperCase() + ")");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error scheduling next sync", e);
        }
    }
    
    /**
     * Schedules a retry for a failed sync operation.
     * 
     * @param accountId The player's account ID
     */
    private void scheduleRetrySync(long accountId) {
        // Retry after 1 hour
        LocalDateTime retryTime = LocalDateTime.now().plusHours(1);
        
        String sql = "UPDATE player_match_history_sync " +
                    "SET next_sync_date = ?, sync_in_progress = ? " +
                    "WHERE account_id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, java.sql.Timestamp.valueOf(retryTime));
            stmt.setInt(2, 0); // Use 0 for false since the column is INTEGER
            stmt.setLong(3, accountId);
            stmt.executeUpdate();
            
            LOGGER.info("Scheduled retry sync for account " + accountId + " at " + retryTime);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error scheduling retry sync", e);
        }
    }
    
    /**
     * Stops all ongoing synchronization processes and shuts down executors.
     * Should be called when the application is shutting down.
     */
    public void shutdown() {
        LOGGER.info("Shutting down MatchHistoryService");
        
        // Shutdown our own executor services
        schedulerService.shutdown();
        executorService.shutdown();
        
        try {
            // Wait for tasks to complete
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!schedulerService.awaitTermination(5, TimeUnit.SECONDS)) {
                schedulerService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            schedulerService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Shutdown the match enrichment service if available
        if (matchEnrichmentService != null) {
            matchEnrichmentService.shutdown();
        }
        
        LOGGER.info("MatchHistoryService shutdown complete");
    }
    
    /**
     * Gets statistics about match enrichment.
     * 
     * @return Map of statistics or empty map if enrichment service is not available
     */
    public Map<String, Object> getEnrichmentStatistics() {
        if (matchEnrichmentService != null) {
            return matchEnrichmentService.getStatistics();
        }
        return new HashMap<>();
    }
}