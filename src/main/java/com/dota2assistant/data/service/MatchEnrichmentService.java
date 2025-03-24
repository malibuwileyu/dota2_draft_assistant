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
    private final Set<Long> processedMatches;
    private final Set<Long> currentlyProcessing;
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
        this.processedMatches = Collections.synchronizedSet(new HashSet<>());
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
                                    
                                    // Add to processed set whether successful or not
                                    // to avoid reprocessing failed matches immediately
                                    processedMatches.add(id);
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
        try {
            logger.debug("Fetching details for match {}", matchId);
            
            // Fetch match details from the API
            Map<String, Object> matchDetails = apiClient.fetchMatch(matchId);
            
            // Convert to JSONObject for consistent handling
            JSONObject matchJson = new JSONObject(matchDetails);
            
            // Save to database
            return updateMatchWithDetails(matchId, matchJson);
        } catch (IOException e) {
            logger.warn("Error fetching details for match {}: {}", matchId, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error processing match {}", matchId, e);
            return false;
        }
    }
    
    /**
     * Updates a match in the database with detailed information.
     * 
     * @param matchId The match ID
     * @param matchDetails The match details as a JSONObject
     * @return true if successful, false otherwise
     */
    private boolean updateMatchWithDetails(long matchId, JSONObject matchDetails) {
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            
            // First check if match exists in the database
            boolean matchExists = checkMatchExists(matchId, conn);
            
            if (!matchExists) {
                logger.debug("Match {} not found in database, cannot enrich", matchId);
                return false;
            }
            
            try {
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
                        return false;
                    }
                }
                
                // Update players if we have player data
                if (matchDetails.has("players") && matchDetails.getJSONArray("players").length() > 0) {
                    // Batch updates for player data
                    String updatePlayerSql = "UPDATE match_players SET " +
                                           "hero_id = ?, " +
                                           "is_radiant = ?, " +
                                           "kills = ?, " +
                                           "deaths = ?, " +
                                           "assists = ?, " +
                                           "won = ? " +
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
                            // Get radiant_win from the match details object, not the local variable
                            boolean matchRadiantWin = matchDetails.has("radiant_win") ? matchDetails.getBoolean("radiant_win") : false;
                            boolean won = (isRadiant && matchRadiantWin) || (!isRadiant && !matchRadiantWin);
                            
                            playerStmt.setInt(1, heroId);
                            playerStmt.setBoolean(2, isRadiant);
                            playerStmt.setInt(3, kills);
                            playerStmt.setInt(4, deaths);
                            playerStmt.setInt(5, assists);
                            playerStmt.setBoolean(6, won);
                            playerStmt.setLong(7, matchId);
                            playerStmt.setInt(8, accountId);
                            
                            int playerUpdated = playerStmt.executeUpdate();
                            if (playerUpdated > 0) {
                                anyPlayerUpdated = true;
                            }
                        }
                        
                        if (!anyPlayerUpdated) {
                            logger.debug("No players were updated for match {}, likely no matching account IDs", matchId);
                        }
                    }
                } else {
                    logger.debug("No player data available for match {}", matchId);
                }
                
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
                }
                
                conn.commit();
                logger.debug("Successfully enriched match {} with details", matchId);
                return true;
            } catch (SQLException e) {
                // Check for specific schema errors
                if (e.getMessage() != null && 
                    (e.getMessage().contains("has_details") || 
                     e.getMessage().contains("match_details") ||
                     e.getMessage().contains("lobby_type"))) {
                    
                    // Try to fix the schema using our utility
                    logger.warn("Detected missing schema elements for match enrichment. Attempting to fix...");
                    conn.rollback();
                    
                    // Attempt to fix schema issues
                    try {
                        com.dota2assistant.util.DatabaseTools.fixMatchesTable();
                        logger.info("Database schema fixed successfully. Match enrichment will retry on next cycle.");
                    } catch (Exception fixError) {
                        logger.error("Failed to fix database schema: {}", fixError.getMessage(), fixError);
                    }
                } else {
                    conn.rollback();
                    logger.error("Database error updating match {}: {}", matchId, e.getMessage());
                }
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Database error while updating match {}", matchId, e);
            return false;
        }
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
        String sql = "SELECT 1 FROM matches WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, matchId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
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
        
        String sql = "SELECT m.id FROM matches m " +
                    "LEFT JOIN match_details md ON m.id = md.match_id " +
                    "WHERE (m.has_details = false OR m.has_details IS NULL) " +
                    "AND md.match_id IS NULL " +
                    "ORDER BY m.start_time DESC " +
                    "LIMIT ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    matchIds.add(rs.getLong("id"));
                }
            }
        } catch (SQLException e) {
            // Check if this is likely due to missing columns from migration
            if (e.getMessage() != null && 
                (e.getMessage().contains("has_details") || 
                 e.getMessage().contains("match_details"))) {
                logger.warn("Database schema appears to be missing required columns for match enrichment. " +
                          "Please ensure migrations have been applied correctly. Error: {}", 
                          e.getMessage());
                logger.info("If migrations failed, you can use DatabaseTools.fixMatchesTable() to repair the schema");
            } else {
                logger.error("Error finding matches needing enrichment", e);
            }
        }
        
        return matchIds;
    }
    
    /**
     * Adds a match to the enrichment queue.
     * 
     * @param matchId The match ID to enrich
     * @param priority Whether to prioritize this match
     * @return true if the match was added to the queue, false otherwise
     */
    public boolean enqueueMatchForEnrichment(long matchId, boolean priority) {
        if (processedMatches.contains(matchId) || currentlyProcessing.contains(matchId)) {
            logger.debug("Match {} is already processed or being processed", matchId);
            return false;
        }
        
        // For priority matches, we'll use offer with timeout to wait for space
        if (priority) {
            try {
                return enrichmentQueue.offer(matchId, 5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } else {
            // For non-priority, just offer without waiting
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
        
        for (Long matchId : matchIds) {
            if (enqueueMatchForEnrichment(matchId, priority)) {
                added++;
            }
        }
        
        if (added > 0) {
            logger.info("Added {} out of {} matches to enrichment queue", added, matchIds.size());
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
        stats.put("apiRequestsLastMinute", requestsInLastMinute.get());
        stats.put("apiRateLimit", REQUESTS_PER_MINUTE);
        
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