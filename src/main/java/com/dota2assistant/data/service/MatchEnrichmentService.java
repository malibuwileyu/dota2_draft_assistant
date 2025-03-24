package com.dota2assistant.data.service;

import com.dota2assistant.data.api.DotaApiClient;
import com.dota2assistant.data.db.DatabaseManager;
import com.dota2assistant.util.PropertyLoader;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for enriching match data with detailed information from the API.
 * Manages a queue of matches that need enrichment and processes them asynchronously
 * while respecting API rate limits.
 */
@Service
public class MatchEnrichmentService {

    private static final Logger logger = LoggerFactory.getLogger(MatchEnrichmentService.class);
    
    private final DotaApiClient apiClient;
    private final DatabaseManager databaseManager;
    private final ExecutorService executorService;
    private final ScheduledExecutorService schedulerService;
    private final BlockingQueue<Long> enrichmentQueue;
    // Track processed matches with metadata for intelligent retrying
    private final Map<Long, MatchProcessingMetadata> processedMatches; 
    private final Set<Long> currentlyProcessing;
    private static final long RETRY_BACKOFF_MS = 3600000; // 1 hour between retries
    private static final int MAX_RETRY_COUNT = 5; // After this many retries, use maximum backoff
    
    // Simple class to track processing metadata
    private static class MatchProcessingMetadata {
        final long lastProcessedTimestamp;
        final int retryCount;
        final boolean success;
        
        MatchProcessingMetadata(long timestamp, int retryCount, boolean success) {
            this.lastProcessedTimestamp = timestamp;
            this.retryCount = retryCount;
            this.success = success;
        }
    }
    
    // Interface for match processing event listeners
    public interface MatchProcessingListener {
        void onMatchProcessed(long matchId, boolean success, int retryCount, String message);
    }
    
    private final List<MatchProcessingListener> listeners = Collections.synchronizedList(new ArrayList<>());
    
    public void addMatchProcessingListener(MatchProcessingListener listener) {
        listeners.add(listener);
    }
    
    public void removeMatchProcessingListener(MatchProcessingListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyListeners(long matchId, boolean success, int retryCount, String message) {
        for (MatchProcessingListener listener : listeners) {
            try {
                listener.onMatchProcessed(matchId, success, retryCount, message);
            } catch (Exception e) {
                logger.warn("Error notifying match processing listener: {}", e.getMessage());
            }
        }
    }
    private final AtomicInteger requestsInLastMinute = new AtomicInteger(0);
    
    // Configuration values
    private static final int QUEUE_CAPACITY = 1000;
    private static final int CONCURRENT_REQUESTS = 2;
    private static final int REQUESTS_PER_MINUTE = 60; // API rate limit
    private static final int MAX_RETRIES = 3;
    private static final int BATCH_SIZE = 10; // Process in batches for better performance
    private static final long RATE_LIMIT_RESET_MS = 60_000; // 1 minute
    
    // Statistics
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalSuccessful = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    
    /**
     * Creates a new MatchEnrichmentService.
     * 
     * @param apiClient the API client to use for fetching match details
     * @param databaseManager the database manager for storing enriched matches
     * @param propertyLoader property loader for configuration
     */
    public MatchEnrichmentService(DotaApiClient apiClient, DatabaseManager databaseManager, PropertyLoader propertyLoader) {
        this.apiClient = apiClient;
        this.databaseManager = databaseManager;
        this.enrichmentQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.processedMatches = Collections.synchronizedMap(new HashMap<>());
        this.currentlyProcessing = Collections.synchronizedSet(new HashSet<>());
        
        // Create thread pools for processing
        this.executorService = Executors.newFixedThreadPool(CONCURRENT_REQUESTS, r -> {
            Thread t = new Thread(r, "match-enrichment-worker");
            t.setDaemon(true);
            return t;
        });
        
        this.schedulerService = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "match-enrichment-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule rate limit reset
        schedulerService.scheduleAtFixedRate(() -> {
            requestsInLastMinute.set(0);
        }, RATE_LIMIT_RESET_MS, RATE_LIMIT_RESET_MS, TimeUnit.MILLISECONDS);
        
        // Schedule statistics logging task
        schedulerService.scheduleAtFixedRate(() -> {
            logEnrichmentStatistics();
        }, 5, 5, TimeUnit.MINUTES);
        
        // Start the queue processor
        startQueueProcessor();
        
        // Schedule periodic enrichment of matches that need details
        schedulePeriodicEnrichment();
        
        logger.info("MatchEnrichmentService initialized and ready to process match details");
    }
    
    /**
     * Starts the queue processor to handle match enrichment requests.
     */
    private void startQueueProcessor() {
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executorService.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // Process matches in batches for better performance
                        List<Long> batch = new ArrayList<>(BATCH_SIZE);
                        Long matchId = enrichmentQueue.poll(5, TimeUnit.SECONDS);
                        
                        if (matchId != null) {
                            batch.add(matchId);
                            // Try to get more matches for the batch
                            enrichmentQueue.drainTo(batch, BATCH_SIZE - 1);
                            
                            // Process each match in the batch
                            for (Long id : batch) {
                                if (processedMatches.contains(id) || currentlyProcessing.contains(id)) {
                                    continue;
                                }
                                
                                currentlyProcessing.add(id);
                                try {
                                    // Check rate limit before making the API call
                                    while (requestsInLastMinute.get() >= REQUESTS_PER_MINUTE) {
                                        logger.debug("Rate limit reached, waiting before processing match {}", id);
                                        Thread.sleep(1000);
                                    }
                                    
                                    // Fetch and process the match
                                    requestsInLastMinute.incrementAndGet();
                                    
                                    // Process with retry logic
                                    boolean success = false;
                                    int retries = 0;
                                    while (!success && retries < MAX_RETRIES) {
                                        try {
                                            success = processMatch(id);
                                            if (success) {
                                                totalSuccessful.incrementAndGet();
                                                logger.debug("Match {} enriched successfully", id);
                                            } else {
                                                retries++;
                                                logger.debug("Failed to enrich match {}, retry {}/{}", id, retries, MAX_RETRIES);
                                                Thread.sleep(1000 * retries); // Exponential backoff
                                            }
                                        } catch (Exception e) {
                                            retries++;
                                            logger.warn("Error enriching match {}, retry {}/{}: {}", id, retries, MAX_RETRIES, e.getMessage());
                                            if (retries >= MAX_RETRIES) {
                                                totalFailed.incrementAndGet();
                                            }
                                            Thread.sleep(1000 * retries); // Exponential backoff
                                        }
                                    }
                                    
                                    // Update processing metadata based on outcome
                                    int currentRetryCount = 0;
                                    MatchProcessingMetadata existingData = processedMatches.get(id);
                                    if (existingData != null) {
                                        currentRetryCount = existingData.retryCount;
                                    }
                                    
                                    // Record result and retry count
                                    MatchProcessingMetadata metadata = new MatchProcessingMetadata(
                                        System.currentTimeMillis(),
                                        currentRetryCount + 1, 
                                        success
                                    );
                                    
                                    processedMatches.put(id, metadata);
                                    
                                    // Log if we're hitting high retry counts
                                    String message = null;
                                    if (metadata.retryCount >= MAX_RETRY_COUNT && !metadata.success) {
                                        message = "Match has failed multiple times - continuing with maximum backoff";
                                        logger.warn("Match {} has failed {} times - will continue retrying with maximum backoff", 
                                                  id, metadata.retryCount);
                                    }
                                    
                                    // Notify listeners about the processing outcome
                                    notifyListeners(id, success, metadata.retryCount, message);
                                    
                                    totalProcessed.incrementAndGet();
                                    
                                    // Respect API rate limits
                                    Thread.sleep(1000);
                                } finally {
                                    currentlyProcessing.remove(id);
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.error("Error in match enrichment processor", e);
                    }
                }
                logger.info("Match enrichment processor stopped");
            });
        }
        logger.info("Started {} match enrichment worker threads", CONCURRENT_REQUESTS);
    }
    
    /**
     * Schedules periodic enrichment of matches that need details.
     * This will periodically check the database for matches that need enrichment.
     */
    private void schedulePeriodicEnrichment() {
        schedulerService.scheduleWithFixedDelay(() -> {
            try {
                // Skip if queue is more than half full
                if (enrichmentQueue.size() > QUEUE_CAPACITY / 2) {
                    logger.debug("Skipping periodic enrichment, queue is more than half full: {}/{}", 
                            enrichmentQueue.size(), QUEUE_CAPACITY);
                    return;
                }
                
                // Find matches that need enrichment - focusing on recent matches first
                int maxMatchesToEnqueue = QUEUE_CAPACITY - enrichmentQueue.size();
                List<Long> matchesToEnrich = findMatchesNeedingEnrichment(maxMatchesToEnqueue);
                
                if (!matchesToEnrich.isEmpty()) {
                    int added = 0;
                    for (Long matchId : matchesToEnrich) {
                        // Skip already processed or currently processing
                        if (processedMatches.contains(matchId) || currentlyProcessing.contains(matchId)) {
                            continue;
                        }
                        
                        boolean success = enrichmentQueue.offer(matchId);
                        if (success) {
                            added++;
                        } else {
                            // Queue is full, stop adding
                            break;
                        }
                    }
                    
                    if (added > 0) {
                        logger.info("Added {} matches to enrichment queue from periodic check", added);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in periodic enrichment", e);
            }
        }, 60, 300, TimeUnit.SECONDS); // Start after 1 minute, run every 5 minutes
        
        logger.info("Scheduled periodic match enrichment");
    }
    
    /**
     * Processes a single match, fetching details from the API and storing in the database.
     * 
     * @param matchId The match ID to process
     * @return true if successful, false otherwise
     */
    private boolean processMatch(Long matchId) {
        final int MAX_RETRIES = 3;
        int attempts = 0;
        
        while (attempts < MAX_RETRIES) {
            try {
                logger.debug("Fetching details for match {} (attempt {}/{})", matchId, attempts + 1, MAX_RETRIES);
                
                // Fetch match details from the API
                Map<String, Object> matchDetails = apiClient.fetchMatch(matchId);
                
                // Convert to JSONObject for consistent handling
                JSONObject matchJson = new JSONObject(matchDetails);
                
                // Save to database
                boolean success = updateMatchWithDetails(matchId, matchJson);
                
                if (success) {
                    return true;
                } else {
                    // If update failed but didn't throw an exception, try again
                    attempts++;
                    // Add exponential backoff
                    Thread.sleep(1000 * attempts);
                }
            } catch (IOException e) {
                logger.warn("Error fetching details for match {}: {}", matchId, e.getMessage());
                
                // If we get a rate limit error (429), back off for longer
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    attempts++;
                    try {
                        int backoffSeconds = 5 * attempts;
                        logger.info("Rate limited, backing off for {} seconds before retry", backoffSeconds);
                        Thread.sleep(backoffSeconds * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    // For other IO errors, don't retry
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                // Handle database connection errors with retry
                if (e.getMessage() != null && 
                    (e.getMessage().contains("connection has been closed") ||
                     e.getMessage().contains("I/O error") ||
                     e.getMessage().contains("Socket closed"))) {
                    
                    attempts++;
                    logger.warn("Database connection error on attempt {}/{}: {}", 
                              attempts, MAX_RETRIES, e.getMessage());
                    
                    try {
                        // Longer backoff for connection issues
                        Thread.sleep(2000 * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    // For other errors, don't retry
                    logger.error("Error processing match {}: {}", matchId, e.getMessage(), e);
                    return false;
                }
            }
        }
        
        logger.warn("Failed to process match {} after {} attempts", matchId, MAX_RETRIES);
        return false;
    }
    
    /**
     * Updates a match in the database with detailed information.
     * 
     * @param matchId The match ID
     * @param matchDetails The match details as a JSONObject
     * @return true if successful, false otherwise
     */
    private boolean updateMatchWithDetails(long matchId, JSONObject matchDetails) {
        final int MAX_RETRIES = 3;
        int attempts = 0;
        
        while (attempts < MAX_RETRIES) {
            Connection conn = null;
            
            try {
                // Get a fresh connection for this operation
                conn = databaseManager.getConnection();
                if (conn == null) {
                    logger.error("Failed to get database connection for match {} (attempt {}/{})", 
                              matchId, attempts + 1, MAX_RETRIES);
                    attempts++;
                    continue;  // Try again if we have retries left
                }
                
                // Test the connection before using it
                if (!testConnection(conn)) {
                    logger.warn("Connection test failed for match {} (attempt {}/{})", 
                             matchId, attempts + 1, MAX_RETRIES);
                    closeConnection(conn);
                    attempts++;
                    Thread.sleep(1000 * attempts);  // Backoff before retry
                    continue;
                }
                
                // Use a separate transaction for each part of the update
                // First, update the basic match data
                boolean basicUpdateSuccess = updateBasicMatchData(conn, matchId, matchDetails);
                if (!basicUpdateSuccess) {
                    closeConnection(conn);
                    attempts++;
                    if (attempts < MAX_RETRIES) {
                        Thread.sleep(1000 * attempts);
                        continue;
                    }
                    return false;
                }
                
                // Then update player data in a separate transaction
                boolean playerUpdateSuccess = updatePlayerData(conn, matchId, matchDetails);
                
                // Finally store the raw match details in a separate transaction
                boolean detailsStoreSuccess = storeRawMatchDetails(conn, matchId, matchDetails);
                
                // If all operations succeeded or at least the basic update did, consider it a success
                closeConnection(conn);
                return basicUpdateSuccess;
                
            } catch (Exception e) {
                logger.error("Error processing match {}: {}", matchId, e.getMessage(), e);
                closeConnection(conn);
                
                // Only retry on connection issues
                if (isConnectionError(e)) {
                    attempts++;
                    if (attempts < MAX_RETRIES) {
                        try {
                            Thread.sleep(2000 * attempts);  // Exponential backoff
                            continue;  // Try again
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                }
                return false;
            }
        }
        
        // If we get here, all retries failed
        logger.warn("Failed to update match {} after {} attempts", matchId, MAX_RETRIES);
        return false;
    }
    
    /**
     * Updates the basic match data in the database
     */
    private boolean updateBasicMatchData(Connection conn, long matchId, JSONObject matchDetails) {
        try {
            // First check if match exists in the database
            boolean matchExists = checkMatchExists(matchId, conn);
            
            if (!matchExists) {
                logger.debug("Match {} not found in database, cannot enrich", matchId);
                return false;
            }
            
            conn.setAutoCommit(false);
            
            // Update match data
            String updateMatchSql = "UPDATE matches SET " +
                                   "start_time = ?, " +
                                   "duration = ?, " +
                                   "radiant_win = ?, " +
                                   "game_mode = ?, " +
                                   "lobby_type = ?, " +
                                   "patch = ?, " +
                                   "region = ?, " +
                                   "has_details = true " +
                                   "WHERE id = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(updateMatchSql)) {
                // Extract basic match data
                long startTime = matchDetails.has("start_time") ? matchDetails.getLong("start_time") : 0;
                int duration = matchDetails.has("duration") ? matchDetails.getInt("duration") : 0;
                boolean radiantWin = matchDetails.has("radiant_win") ? matchDetails.getBoolean("radiant_win") : false;
                int gameMode = matchDetails.has("game_mode") ? matchDetails.getInt("game_mode") : 0;
                int lobbyType = matchDetails.has("lobby_type") ? matchDetails.getInt("lobby_type") : 0;
                int patch = matchDetails.has("patch") ? matchDetails.getInt("patch") : 0;
                int region = matchDetails.has("region") ? matchDetails.getInt("region") : 0;
                
                stmt.setTimestamp(1, new java.sql.Timestamp(startTime * 1000));
                stmt.setInt(2, duration);
                stmt.setBoolean(3, radiantWin);
                stmt.setInt(4, gameMode);
                stmt.setInt(5, lobbyType);
                stmt.setInt(6, patch);
                stmt.setInt(7, region);
                stmt.setLong(8, matchId);
                
                int updated = stmt.executeUpdate();
                if (updated == 0) {
                    logger.warn("Failed to update match {}, no rows affected", matchId);
                    conn.rollback();
                    return false;
                }
                
                conn.commit();
                return true;
            } catch (SQLException e) {
                // Check for specific schema errors
                if (e.getMessage() != null && 
                    (e.getMessage().contains("has_details") || 
                     e.getMessage().contains("lobby_type"))) {
                    
                    // Try to fix the schema
                    logger.warn("Detected missing schema elements for match enrichment. Attempting to fix...");
                    safeRollback(conn);
                    
                    try {
                        com.dota2assistant.util.DatabaseTools.fixMatchesTable();
                        logger.info("Database schema fixed successfully. Match enrichment will retry on next cycle.");
                    } catch (Exception fixError) {
                        logger.error("Failed to fix database schema: {}", fixError.getMessage(), fixError);
                    }
                } else {
                    logger.error("Database error updating match {}: {}", matchId, e.getMessage());
                    safeRollback(conn);
                }
                return false;
            }
        } catch (SQLException e) {
            logger.error("Error checking if match exists: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Updates player data for the match
     */
    private boolean updatePlayerData(Connection conn, long matchId, JSONObject matchDetails) {
        if (!matchDetails.has("players") || matchDetails.getJSONArray("players").length() == 0) {
            logger.debug("No player data available for match {}", matchId);
            return true;  // Not a failure, just no player data
        }
        
        try {
            conn.setAutoCommit(false);
            
            // Batch updates for player data
            String updatePlayerSql = "UPDATE match_players SET " +
                                   "hero_id = ?, " +
                                   "is_radiant = ?, " +
                                   "kills = ?, " +
                                   "deaths = ?, " +
                                   "assists = ? " +
                                   "WHERE match_id = ? AND account_id = ?";
            
            try (PreparedStatement playerStmt = conn.prepareStatement(updatePlayerSql)) {
                boolean anyPlayerUpdated = false;
                
                for (int i = 0; i < matchDetails.getJSONArray("players").length(); i++) {
                    JSONObject player = matchDetails.getJSONArray("players").getJSONObject(i);
                    
                    // Skip if no account ID (anonymous player)
                    if (!player.has("account_id") || player.isNull("account_id")) {
                        continue;
                    }
                    
                    int accountId = player.getInt("account_id");
                    int heroId = player.has("hero_id") ? player.getInt("hero_id") : 0;
                    boolean isRadiant = player.has("isRadiant") ? player.getBoolean("isRadiant") : 
                                    (player.has("player_slot") ? player.getInt("player_slot") < 128 : true);
                    int kills = player.has("kills") ? player.getInt("kills") : 0;
                    int deaths = player.has("deaths") ? player.getInt("deaths") : 0;
                    int assists = player.has("assists") ? player.getInt("assists") : 0;
                    
                    playerStmt.setInt(1, heroId);
                    playerStmt.setBoolean(2, isRadiant);
                    playerStmt.setInt(3, kills);
                    playerStmt.setInt(4, deaths);
                    playerStmt.setInt(5, assists);
                    playerStmt.setLong(6, matchId);
                    playerStmt.setInt(7, accountId);
                    
                    int playerUpdated = playerStmt.executeUpdate();
                    if (playerUpdated > 0) {
                        anyPlayerUpdated = true;
                    }
                }
                
                if (!anyPlayerUpdated) {
                    logger.debug("No players were updated for match {}, likely no matching account IDs", matchId);
                }
                
                conn.commit();
                return true;
            } catch (SQLException e) {
                logger.warn("Error updating player data for match {}: {}", matchId, e.getMessage());
                safeRollback(conn);
                return false;
            }
        } catch (SQLException e) {
            logger.error("Transaction error for player data update: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Stores the raw match details in the database
     */
    private boolean storeRawMatchDetails(Connection conn, long matchId, JSONObject matchDetails) {
        try {
            conn.setAutoCommit(true);  // Use autocommit for this simple operation
            
            // Store raw match details in a separate table for future reference
            String insertRawDetailsSql = "INSERT INTO match_details (match_id, raw_data, updated_at) " +
                                       "VALUES (?, ?, CURRENT_TIMESTAMP) " +
                                       "ON CONFLICT (match_id) DO UPDATE SET " +
                                       "raw_data = EXCLUDED.raw_data, " +
                                       "updated_at = CURRENT_TIMESTAMP";
            
            try (PreparedStatement detailsStmt = conn.prepareStatement(insertRawDetailsSql)) {
                detailsStmt.setLong(1, matchId);
                detailsStmt.setString(2, matchDetails.toString());
                detailsStmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                // Check if table doesn't exist
                if (e.getMessage() != null && e.getMessage().contains("match_details")) {
                    // Try to fix the schema
                    logger.warn("match_details table missing, attempting to fix schema");
                    try {
                        com.dota2assistant.util.DatabaseTools.fixMatchesTable();
                        logger.info("Database schema fixed successfully. Match enrichment will retry on next cycle.");
                    } catch (Exception fixError) {
                        logger.error("Failed to fix database schema: {}", fixError.getMessage(), fixError);
                    }
                } else {
                    logger.error("Error storing raw match details for match {}: {}", matchId, e.getMessage());
                }
                return false;
            }
        } catch (SQLException e) {
            logger.error("Transaction error for storing raw match details: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Tests if a connection is valid and usable
     */
    private boolean testConnection(Connection conn) {
        if (conn == null) {
            return false;
        }
        
        try {
            if (conn.isClosed()) {
                return false;
            }
            
            // Simple query to test the connection
            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
                stmt.executeQuery();
            }
            return true;
        } catch (SQLException e) {
            logger.debug("Connection test failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Safely closes a connection, ignoring errors
     */
    private void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                logger.debug("Error closing connection: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Safely rolls back a transaction, ignoring errors
     */
    private void safeRollback(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed() && !conn.getAutoCommit()) {
                    conn.rollback();
                }
            } catch (SQLException e) {
                logger.debug("Error during rollback: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Checks if an exception is related to connection issues
     */
    private boolean isConnectionError(Exception e) {
        if (e instanceof SQLException) {
            String message = e.getMessage();
            return message != null && (
                message.contains("connection has been closed") ||
                message.contains("Connection is closed") ||
                message.contains("Connection reset") ||
                message.contains("I/O error") ||
                message.contains("Socket closed") ||
                message.contains("No more data") ||
                message.contains("connection is not available")
            );
        }
        return false;
    }
    
    /**
     * Checks if a match exists in the database.
     * 
     * @param matchId The match ID to check
     * @param conn The database connection to use
     * @return true if the match exists, false otherwise
     * @throws SQLException if a database error occurs
     */
    private boolean checkMatchExists(long matchId, Connection conn) throws SQLException {
        // First test if the connection is valid
        if (conn == null || conn.isClosed()) {
            logger.warn("Connection is null or closed in checkMatchExists");
            throw new SQLException("Connection is null or closed");
        }
        
        String sql = "SELECT 1 FROM matches WHERE id = ?";
        
        try {
            // Use a shorter timeout for this query since it's a simple check
            conn.setNetworkTimeout(Executors.newSingleThreadExecutor(), 5000);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, matchId);
                stmt.setQueryTimeout(5); // Set query timeout to 5 seconds
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            if (isConnectionError(e)) {
                logger.warn("Connection error in checkMatchExists: {}", e.getMessage());
                throw new SQLException("Connection error in checkMatchExists", e);
            }
            throw e;
        }
    }
    
    /**
     * Finds matches in the database that need enrichment with detailed information.
     * 
     * @param limit Maximum number of matches to return
     * @return List of match IDs that need enrichment
     */
    private List<Long> findMatchesNeedingEnrichment(int limit) {
        List<Long> matchIds = new ArrayList<>();
        
        // Try-with-resources to ensure connection is always properly closed
        try (Connection conn = databaseManager.getConnection()) {
            if (conn == null) {
                logger.error("Failed to get database connection for finding matches needing enrichment");
                return matchIds;
            }
            
            // Check and fix the schema if needed
            try {
                checkAndFixMatchEnrichmentSchema();
            } catch (SQLException e) {
                logger.warn("Could not verify or fix schema: {}", e.getMessage());
                // Continue anyway, the query will fail if there's a real problem
            }
            
            // Query with error handling
            findMatchesNeedingEnrichmentWithQuery(conn, matchIds, limit);
            
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("connection has been closed")) {
                logger.error("Connection was unexpectedly closed. Will retry in next cycle.");
            } else if (e.getMessage() != null && 
                (e.getMessage().contains("has_details") || 
                 e.getMessage().contains("match_details"))) {
                logger.warn("Schema issue detected: {}", e.getMessage());
                
                // Try to automatically fix the schema
                try {
                    com.dota2assistant.util.DatabaseTools.fixMatchesTable();
                    logger.info("Database schema fixed. Retry match enrichment in next cycle.");
                } catch (Exception fixError) {
                    logger.error("Failed to fix database schema: {}", fixError.getMessage(), fixError);
                }
            } else {
                logger.error("Error finding matches needing enrichment", e);
            }
        } 
        
        return matchIds;
    }
    
    /**
     * Checks if match enrichment schema is properly set up, fixes if not
     */
    private void checkAndFixMatchEnrichmentSchema() throws SQLException {
        boolean hasRequiredSchema = true;
        
        // Check if has_details column exists
        if (!com.dota2assistant.util.DatabaseTools.columnExists("matches", "has_details")) {
            logger.warn("Column 'has_details' does not exist in matches table, attempting to fix schema");
            hasRequiredSchema = false;
        }
        
        // Check if match_details table exists
        if (!com.dota2assistant.util.DatabaseTools.tableExists("match_details")) {
            logger.warn("Table 'match_details' does not exist, attempting to fix schema");
            hasRequiredSchema = false;
        }
        
        // Fix schema if needed
        if (!hasRequiredSchema) {
            com.dota2assistant.util.DatabaseTools.fixMatchesTable();
            logger.info("Database schema fixed for match enrichment");
        }
    }
    
    /**
     * Executes the query to find matches needing enrichment
     */
    private void findMatchesNeedingEnrichmentWithQuery(Connection conn, List<Long> matchIds, int limit) throws SQLException {
        // First try a query that works even with basic schema
        String simpleSql = "SELECT id FROM matches WHERE NOT EXISTS " +
                         "(SELECT 1 FROM match_details WHERE match_details.match_id = matches.id) " +
                         "ORDER BY start_time DESC LIMIT ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(simpleSql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    matchIds.add(rs.getLong("id"));
                    count++;
                }
                
                if (count > 0) {
                    logger.debug("Found {} matches needing enrichment using simple query", count);
                    return;
                }
            } catch (SQLException e) {
                logger.debug("Simple query failed, trying more specific query: {}", e.getMessage());
                // Continue to the next query
            }
        }
        
        // Use a more specific query if the simple one returned no results or failed
        String detailedSql = "SELECT m.id FROM matches m " +
                           "LEFT JOIN match_details md ON m.id = md.match_id " +
                           "WHERE (m.has_details = false OR m.has_details IS NULL) " +
                           "AND md.match_id IS NULL " +
                           "ORDER BY m.start_time DESC " +
                           "LIMIT ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(detailedSql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    matchIds.add(rs.getLong("id"));
                    count++;
                }
                logger.debug("Found {} matches needing enrichment using detailed query", count);
            }
        }
    }
    
    /**
     * Logs enrichment statistics for monitoring
     */
    private void logEnrichmentStatistics() {
        // Count matches by retry count and success status
        int totalRetryingMatches = 0;
        int totalSuccessfulMatches = 0;
        
        synchronized (processedMatches) {
            for (MatchProcessingMetadata metadata : processedMatches.values()) {
                if (metadata.retryCount > 1) {
                    totalRetryingMatches++;
                }
                if (metadata.success) {
                    totalSuccessfulMatches++;
                }
            }
        }
        
        logger.info("Match enrichment statistics - Queue: {}/{}, Processing: {}, Total processed: {}, " +
                  "Success: {}, Retry count: {}, API requests/minute: {}/{}",
                  enrichmentQueue.size(), QUEUE_CAPACITY,
                  currentlyProcessing.size(),
                  totalProcessed.get(),
                  totalSuccessfulMatches,
                  totalRetryingMatches,
                  requestsInLastMinute.get(),
                  REQUESTS_PER_MINUTE);
    }
    
    public boolean enqueueMatchForEnrichment(long matchId, boolean priority) {
        // Check if match is already processed and see if it needs to be retried
        MatchProcessingMetadata metadata = processedMatches.get(matchId);
        if (metadata != null) {
            // If the match was successfully processed, never retry it
            if (metadata.success) {
                logger.debug("Match {} was successfully processed previously", matchId);
                return false;
            }
            
            // Check backoff period to avoid too frequent retries
            long now = System.currentTimeMillis();
            long timeSinceLastAttempt = now - metadata.lastProcessedTimestamp;
            
            // Apply exponential backoff - wait longer between retry attempts
            // Cap at 64x base backoff (about 64 hours) for extreme cases
            long requiredBackoff = RETRY_BACKOFF_MS * (1L << Math.min(metadata.retryCount - 1, 6));
            
            if (timeSinceLastAttempt < requiredBackoff) {
                logger.debug("Match {} failed processing but is in backoff period ({}ms < {}ms required)", 
                           matchId, timeSinceLastAttempt, requiredBackoff);
                return false;
            }
            
            // If we reach here, we're ready to retry this match
            logger.debug("Retrying match {} (attempt #{})", matchId, metadata.retryCount + 1);
        }
        
        // If match is currently being processed, don't enqueue it again
        if (currentlyProcessing.contains(matchId)) {
            logger.debug("Match {} is currently being processed", matchId);
            return false;
        }
        
        // For priority matches, we'll use offer with timeout to wait for space
        if (priority) {
            try {
                logger.debug("Adding priority match {} to enrichment queue", matchId);
                return enrichmentQueue.offer(matchId, 5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } else {
            // For non-priority, just offer without waiting
            logger.debug("Adding match {} to enrichment queue", matchId);
            return enrichmentQueue.offer(matchId);
        }
    }
    
    /**
     * Enqueues multiple matches for enrichment at once.
     * 
     * @param matchIds List of match IDs to enrich
     * @param priority Whether to prioritize these matches
     * @return The number of matches successfully added to the queue
     */
    public int enqueueMatchesForEnrichment(List<Long> matchIds, boolean priority) {
        int added = 0;
        int skipped = 0;
        
        logger.debug("Attempting to enqueue {} matches for enrichment", matchIds.size());
        
        // Group by reason for better reporting
        int invalidIds = 0;
        int alreadyProcessed = 0;
        int inBackoff = 0;
        int currentlyProcessingCount = 0;
        int queueFull = 0;
        
        for (Long matchId : matchIds) {
            // Skip any invalid match IDs (null, zero, or negative)
            if (matchId == null || matchId <= 0) {
                logger.debug("Skipping invalid match ID: {}", matchId);
                invalidIds++;
                skipped++;
                continue;
            }
            
            // Check if previously processed and in backoff period
            MatchProcessingMetadata metadata = processedMatches.get(matchId);
            if (metadata != null) {
                // If successful, skip
                if (metadata.success) {
                    alreadyProcessed++;
                    skipped++;
                    continue;
                }
                
                // Check backoff
                long now = System.currentTimeMillis();
                long timeSinceLastAttempt = now - metadata.lastProcessedTimestamp;
                long requiredBackoff = RETRY_BACKOFF_MS * (1L << Math.min(metadata.retryCount - 1, 6));
                
                if (timeSinceLastAttempt < requiredBackoff) {
                    inBackoff++;
                    skipped++;
                    continue;
                }
            }
            
            // Check if currently processing
            if (currentlyProcessing.contains(matchId)) {
                currentlyProcessingCount++;
                skipped++;
                continue;
            }
            
            // Try to add to queue
            boolean success;
            try {
                success = priority ? 
                    enrichmentQueue.offer(matchId, 100, TimeUnit.MILLISECONDS) : 
                    enrichmentQueue.offer(matchId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                success = false;
            }
                
            if (success) {
                added++;
            } else {
                queueFull++;
                skipped++;
            }
        }
        
        logger.info("Added {} out of {} matches to enrichment queue ({} skipped)", 
                  added, matchIds.size(), skipped);
                  
        if (skipped > 0) {
            logger.debug("Skip breakdown: {} invalid IDs, {} already processed, {} in backoff period, {} currently processing, {} queue full", 
                       invalidIds, alreadyProcessed, inBackoff, currentlyProcessingCount, queueFull);
        }
        
        return added;
    }
    
    /**
     * Gets statistics about the enrichment service.
     * 
     * @return Map of statistics including queue size, processing count, etc.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("queueSize", enrichmentQueue.size());
        stats.put("queueCapacity", QUEUE_CAPACITY);
        stats.put("currentlyProcessing", currentlyProcessing.size());
        stats.put("processedTotal", totalProcessed.get());
        stats.put("successfulTotal", totalSuccessful.get());
        stats.put("failedTotal", totalFailed.get());
        
        // Count processed matches by status
        int successCount = 0;
        int waitingForRetryCount = 0;
        
        // Collect detailed match processing metadata
        List<Map<String, Object>> matchDetailsList = new ArrayList<>();
        
        synchronized (processedMatches) {
            for (Map.Entry<Long, MatchProcessingMetadata> entry : processedMatches.entrySet()) {
                Long matchId = entry.getKey();
                MatchProcessingMetadata metadata = entry.getValue();
                
                if (metadata.success) {
                    successCount++;
                } else {
                    waitingForRetryCount++;
                }
                
                // Add detailed information for UI display
                Map<String, Object> matchDetails = new HashMap<>();
                matchDetails.put("matchId", matchId);
                matchDetails.put("success", metadata.success);
                matchDetails.put("retryCount", metadata.retryCount);
                matchDetails.put("lastProcessedTimestamp", metadata.lastProcessedTimestamp);
                
                // Calculate next attempt time based on backoff
                if (!metadata.success) {
                    // Apply exponential backoff - wait longer between retry attempts
                    // Cap at 64x base backoff (about 64 hours) for extreme cases
                    long backoffMultiplier = 1L << Math.min(metadata.retryCount - 1, 6);
                    long nextAttemptDelay = RETRY_BACKOFF_MS * backoffMultiplier;
                    long nextAttemptTimestamp = metadata.lastProcessedTimestamp + nextAttemptDelay;
                    matchDetails.put("nextAttemptTimestamp", nextAttemptTimestamp);
                }
                
                matchDetailsList.add(matchDetails);
            }
        }
        
        stats.put("successCount", successCount);
        stats.put("waitingForRetryCount", waitingForRetryCount);
        stats.put("apiRequestsLastMinute", requestsInLastMinute.get());
        stats.put("apiRateLimit", REQUESTS_PER_MINUTE);
        stats.put("matchDetails", matchDetailsList);
        
        // Add currently queued match IDs
        List<Long> queuedMatches = new ArrayList<>();
        enrichmentQueue.forEach(queuedMatches::add);
        stats.put("queuedMatches", queuedMatches);
        
        return stats;
    }
    
    /**
     * Shuts down the enrichment service.
     */
    public void shutdown() {
        logger.info("Shutting down match enrichment service");
        
        // Shutdown executor services
        schedulerService.shutdown();
        executorService.shutdown();
        
        try {
            // Wait for tasks to complete
            if (!schedulerService.awaitTermination(5, TimeUnit.SECONDS)) {
                schedulerService.shutdownNow();
            }
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            logger.info("Match enrichment service shutdown complete. " +
                      "Stats: processed={}, successful={}, failed={}, queue={}",
                      totalProcessed.get(), totalSuccessful.get(), 
                      totalFailed.get(), enrichmentQueue.size());
        } catch (InterruptedException e) {
            schedulerService.shutdownNow();
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Match enrichment service shutdown interrupted");
        }
    }
}