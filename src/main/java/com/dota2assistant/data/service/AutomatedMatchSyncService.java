package com.dota2assistant.data.service;

import com.dota2assistant.data.db.DatabaseManager;
import com.dota2assistant.data.repository.UserMatchRepository;
import com.dota2assistant.util.PropertyLoader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for automated match synchronization based on user preferences.
 */
public class AutomatedMatchSyncService {
    private static final Logger LOGGER = Logger.getLogger(AutomatedMatchSyncService.class.getName());
    
    private final UserMatchRepository matchRepository;
    private final DatabaseManager databaseManager;
    private final MatchHistoryService matchHistoryService;
    private final ScheduledExecutorService scheduler;
    
    // Sync frequency settings
    public enum SyncFrequency {
        REAL_TIME(Duration.ofMinutes(15)),
        HOURLY(Duration.ofHours(1)),
        DAILY(Duration.ofDays(1)),
        WEEKLY(Duration.ofDays(7)),
        MONTHLY(Duration.ofDays(30)),
        NEVER(Duration.ofDays(Integer.MAX_VALUE));
        
        private final Duration interval;
        
        SyncFrequency(Duration interval) {
            this.interval = interval;
        }
        
        public Duration getInterval() {
            return interval;
        }
        
        public static SyncFrequency fromString(String frequencyString) {
            try {
                return valueOf(frequencyString.toUpperCase());
            } catch (IllegalArgumentException e) {
                return DAILY; // Default to daily if invalid
            }
        }
    }
    
    public AutomatedMatchSyncService(UserMatchRepository matchRepository, 
                                    DatabaseManager databaseManager,
                                    MatchHistoryService matchHistoryService,
                                    PropertyLoader propertyLoader) {
        this.matchRepository = matchRepository;
        this.databaseManager = databaseManager;
        this.matchHistoryService = matchHistoryService;
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Start the periodic check for pending syncs
        int checkIntervalMinutes = Integer.parseInt(
            propertyLoader.getProperty("match.sync.check.interval.minutes", "15")
        );
        
        scheduler.scheduleAtFixedRate(
            this::checkPendingSyncs, 
            1, 
            checkIntervalMinutes, 
            TimeUnit.MINUTES
        );
        
        LOGGER.info("Automated match sync service started. Checking every " + 
                  checkIntervalMinutes + " minutes.");
    }
    
    /**
     * Checks for and processes any sync operations that are due.
     */
    private void checkPendingSyncs() {
        try {
            LOGGER.info("Checking for pending match sync operations...");
            
            List<Map<String, Object>> pendingSyncs = getPendingSyncs();
            
            if (pendingSyncs.isEmpty()) {
                LOGGER.info("No pending sync operations found.");
                return;
            }
            
            LOGGER.info("Found " + pendingSyncs.size() + " pending sync operations.");
            
            // Process each pending sync
            for (Map<String, Object> sync : pendingSyncs) {
                long accountId = (long) sync.get("accountId");
                
                try {
                    // Start the sync
                    LOGGER.info("Starting automated sync for account: " + accountId);
                    matchHistoryService.syncPlayerMatchHistory(accountId, false);
                    
                    // Schedule next sync based on frequency
                    String frequencyStr = (String) sync.getOrDefault("syncFrequency", "DAILY");
                    scheduleNextSync(accountId, frequencyStr);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error processing sync for account: " + accountId, e);
                    // Schedule retry after an hour
                    scheduleRetry(accountId);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking pending syncs", e);
        }
    }
    
    /**
     * Gets a list of sync operations that are due.
     * 
     * @return List of pending sync operations
     */
    private List<Map<String, Object>> getPendingSyncs() {
        List<Map<String, Object>> pendingSyncs = new ArrayList<>();
        
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT p.account_id, p.next_sync_date, p.sync_frequency, " +
                       "p.sync_in_progress, p.last_match_id, up.value as auto_sync " +
                       "FROM player_match_history_sync p " +
                       "LEFT JOIN user_preferences up ON p.account_id = up.account_id " +
                       "AND up.preference_type_id = (SELECT id FROM user_preference_types " +
                       "                           WHERE name = 'match_history_refresh_frequency') " +
                       "WHERE p.sync_in_progress = 0 " + // Use 0 instead of false since the column is INTEGER
                       "AND (p.next_sync_date IS NULL OR p.next_sync_date <= NOW()) " +
                       "AND (up.value IS NULL OR up.value != 'NEVER')";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    Map<String, Object> sync = new HashMap<>();
                    sync.put("accountId", rs.getLong("account_id"));
                    sync.put("syncFrequency", rs.getString("sync_frequency"));
                    sync.put("lastMatchId", rs.getLong("last_match_id"));
                    
                    String autoSync = rs.getString("auto_sync");
                    if (autoSync != null) {
                        // User preference overrides default setting
                        sync.put("syncFrequency", autoSync);
                    }
                    
                    pendingSyncs.add(sync);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting pending syncs", e);
        }
        
        return pendingSyncs;
    }
    
    /**
     * Schedules the next sync operation for a player based on frequency.
     * 
     * @param accountId The player's account ID
     * @param frequencyStr String representation of the frequency
     */
    private void scheduleNextSync(long accountId, String frequencyStr) {
        SyncFrequency frequency = SyncFrequency.fromString(frequencyStr);
        LocalDateTime nextSync = LocalDateTime.now().plus(frequency.getInterval());
        
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "UPDATE player_match_history_sync " +
                       "SET next_sync_date = ?, sync_frequency = ? " +
                       "WHERE account_id = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(nextSync));
                stmt.setString(2, frequency.name());
                stmt.setLong(3, accountId);
                stmt.executeUpdate();
                
                LOGGER.info("Scheduled next sync for account " + accountId + 
                          " at " + nextSync + " (" + frequency + ")");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error scheduling next sync", e);
        }
    }
    
    /**
     * Schedules a retry for a failed sync operation.
     * 
     * @param accountId The player's account ID
     */
    private void scheduleRetry(long accountId) {
        LocalDateTime nextSync = LocalDateTime.now().plus(1, ChronoUnit.HOURS);
        
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "UPDATE player_match_history_sync " +
                       "SET next_sync_date = ?, sync_in_progress = ? " +
                       "WHERE account_id = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, java.sql.Timestamp.valueOf(nextSync));
                stmt.setInt(2, 0);  // Use 0 for false since the column is INTEGER
                stmt.setLong(3, accountId);
                stmt.executeUpdate();
                
                LOGGER.info("Scheduled retry sync for account " + accountId + 
                          " at " + nextSync + " (after failure)");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error scheduling retry sync", e);
        }
    }
    
    /**
     * Sets the sync frequency for a player.
     * 
     * @param accountId The player's account ID
     * @param frequency The sync frequency to set
     */
    public void setSyncFrequency(long accountId, String frequency) {
        try {
            SyncFrequency syncFrequency = SyncFrequency.fromString(frequency);
            LocalDateTime nextSync = null;
            
            if (syncFrequency != SyncFrequency.NEVER) {
                nextSync = LocalDateTime.now().plus(syncFrequency.getInterval());
            }
            
            // TODO: Update the player's sync frequency in the database
            LOGGER.info("Updated sync frequency for account " + accountId + 
                      " to " + syncFrequency + 
                      (nextSync != null ? ", next sync at " + nextSync : ""));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error setting sync frequency for account: " + accountId, e);
        }
    }
    
    /**
     * Manually triggers a sync for a player immediately.
     * 
     * @param accountId The player's account ID
     * @param fullSync Whether to do a full sync or incremental
     * @return True if the sync was started successfully
     */
    public boolean triggerSync(long accountId, boolean fullSync) {
        try {
            matchHistoryService.syncPlayerMatchHistory(accountId, fullSync);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error triggering sync for account: " + accountId, e);
            return false;
        }
    }
    
    /**
     * Gets statistics about sync operations.
     * 
     * @return Map containing statistics
     */
    public Map<String, Object> getSyncStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // TODO: Implement actual statistics gathering
        stats.put("totalActiveUsers", 0);
        stats.put("syncOperationsLast24Hours", 0);
        stats.put("pendingSyncs", 0);
        stats.put("averageSyncDuration", 0);
        
        return stats;
    }
    
    /**
     * Stops the scheduled services. Should be called during application shutdown.
     */
    public void shutdown() {
        try {
            LOGGER.info("Shutting down automated match sync service...");
            scheduler.shutdown();
            
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            LOGGER.info("Automated match sync service stopped.");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Interrupted while shutting down sync service", e);
        }
    }
}