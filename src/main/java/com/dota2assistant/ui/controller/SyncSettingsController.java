package com.dota2assistant.ui.controller;

import com.dota2assistant.data.service.AutomatedMatchSyncService;
import com.dota2assistant.data.service.MatchHistoryService;
import com.dota2assistant.data.service.AutomatedMatchSyncService.SyncFrequency;
import com.dota2assistant.auth.SteamUser;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the sync settings component that allows configuration
 * of match history synchronization settings and manual syncing.
 */
@Component
public class SyncSettingsController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(SyncSettingsController.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm");
    
    @FXML private ComboBox<String> syncFrequencyComboBox;
    @FXML private Label lastSyncLabel;
    @FXML private Label matchesSyncedLabel;
    @FXML private Label nextSyncLabel;
    @FXML private Button syncNowButton;
    @FXML private Label syncStatusLabel;
    @FXML private ProgressIndicator syncProgressIndicator;
    
    private MatchHistoryService matchHistoryService;
    private AutomatedMatchSyncService automatedSyncService;
    private SteamUser currentUser;
    private Timer statusRefreshTimer;
    private boolean syncInProgress = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("Initializing SyncSettingsController");
        
        // Initialize ComboBox with sync frequency options
        syncFrequencyComboBox.setItems(FXCollections.observableArrayList(
            "Real-time", "Hourly", "Daily", "Weekly", "Monthly", "Never"
        ));
        
        // Add listener to handle frequency changes
        syncFrequencyComboBox.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> handleFrequencyChange(newValue));
            
        // Initially disable until user is set
        setControlsEnabled(false);
        
        logger.debug("SyncSettingsController initialized");
    }
    
    /**
     * Sets the services used by this controller.
     * This should be called after the controller is instantiated.
     * 
     * @param matchHistoryService The match history service
     * @param automatedSyncService The automated sync service
     */
    public void setServices(MatchHistoryService matchHistoryService, AutomatedMatchSyncService automatedSyncService) {
        this.matchHistoryService = matchHistoryService;
        this.automatedSyncService = automatedSyncService;
        logger.debug("Services set for SyncSettingsController");
    }
    
    /**
     * Sets the current user for the controller.
     * This will update the UI based on the user's sync settings.
     * 
     * @param user The current user
     */
    public void setUser(SteamUser user) {
        this.currentUser = user;
        setControlsEnabled(user != null);
        
        if (user != null) {
            logger.info("Setting user for SyncSettingsController: {}", user.getUsername());
            updateSyncStatus();
            
            // Start periodic status updates
            if (statusRefreshTimer != null) {
                statusRefreshTimer.cancel();
            }
            
            statusRefreshTimer = new Timer(true);
            statusRefreshTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> updateSyncStatus());
                }
            }, 30000, 30000); // Update every 30 seconds
        } else {
            logger.info("Clearing user for SyncSettingsController");
            if (statusRefreshTimer != null) {
                statusRefreshTimer.cancel();
                statusRefreshTimer = null;
            }
        }
    }
    
    /**
     * Enables or disables the UI controls based on login status.
     * 
     * @param enabled Whether the controls should be enabled
     */
    private void setControlsEnabled(boolean enabled) {
        syncFrequencyComboBox.setDisable(!enabled);
        syncNowButton.setDisable(!enabled || syncInProgress);
        
        if (!enabled) {
            lastSyncLabel.setText("Last sync: N/A");
            matchesSyncedLabel.setText("Matches synced: N/A");
            nextSyncLabel.setText("Next scheduled sync: N/A");
            syncStatusLabel.setText("Status: Not logged in");
        }
    }
    
    /**
     * Handles the "Sync Now" button click.
     * Triggers an immediate synchronization of match history.
     */
    @FXML
    private void handleSyncNow() {
        if (currentUser == null || matchHistoryService == null) {
            logger.warn("Cannot sync - user or service not available");
            return;
        }
        
        String steamId = currentUser.getSteamId();
        logger.info("Starting manual sync for user: {}", steamId);
        
        // Update UI to show sync in progress
        syncInProgress = true;
        syncNowButton.setDisable(true);
        syncStatusLabel.setText("Status: Syncing...");
        syncStatusLabel.getStyleClass().removeAll("error", "completed");
        syncStatusLabel.getStyleClass().add("syncing");
        syncProgressIndicator.setVisible(true);
        
        // Parse the Steam ID to get the account ID
        long steamId64;
        try {
            steamId64 = Long.parseLong(steamId);
        } catch (NumberFormatException e) {
            logger.error("Invalid Steam ID format: {}", steamId, e);
            showErrorAlert("Invalid Steam ID format");
            resetSyncStatus();
            return;
        }
        
        // Trigger the sync
        CompletableFuture<Boolean> syncFuture;
        try {
            syncFuture = matchHistoryService.syncPlayerMatchHistory(steamId64, false);
        } catch (Exception e) {
            logger.error("Error starting sync", e);
            showErrorAlert("Failed to start sync: " + e.getMessage());
            resetSyncStatus();
            return;
        }
        
        syncFuture.whenComplete((success, ex) -> {
            Platform.runLater(() -> {
                syncInProgress = false;
                syncNowButton.setDisable(false);
                syncProgressIndicator.setVisible(false);
                
                if (ex != null) {
                    logger.error("Error during sync", ex);
                    syncStatusLabel.setText("Status: Error");
                    syncStatusLabel.getStyleClass().removeAll("syncing", "completed");
                    syncStatusLabel.getStyleClass().add("error");
                    
                    showErrorAlert("There was an error synchronizing your match history: " + ex.getMessage());
                } else if (Boolean.TRUE.equals(success)) {
                    logger.info("Sync completed successfully for user: {}", steamId);
                    syncStatusLabel.setText("Status: Completed");
                    syncStatusLabel.getStyleClass().removeAll("syncing", "error");
                    syncStatusLabel.getStyleClass().add("completed");
                    updateSyncStatus();
                } else {
                    logger.warn("Sync failed for user: {}", steamId);
                    syncStatusLabel.setText("Status: Failed");
                    syncStatusLabel.getStyleClass().removeAll("syncing", "completed");
                    syncStatusLabel.getStyleClass().add("error");
                }
            });
        });
    }
    
    /**
     * Resets the sync status UI elements.
     */
    private void resetSyncStatus() {
        syncInProgress = false;
        syncNowButton.setDisable(false);
        syncProgressIndicator.setVisible(false);
        syncStatusLabel.setText("Status: Idle");
        syncStatusLabel.getStyleClass().removeAll("syncing", "error", "completed");
    }
    
    /**
     * Handles changes to the sync frequency setting.
     * 
     * @param frequencyStr The new frequency setting
     */
    private void handleFrequencyChange(String frequencyStr) {
        if (automatedSyncService == null || currentUser == null || frequencyStr == null) {
            logger.warn("Cannot update frequency - service or user not available");
            return;
        }
        
        logger.info("Updating sync frequency to {} for user: {}", frequencyStr, currentUser.getSteamId());
        
        // Convert to enum format expected by service
        String enumFrequency = frequencyStr.toUpperCase();
        
        try {
            long steamId64 = Long.parseLong(currentUser.getSteamId());
            // Update the sync frequency in the service
            automatedSyncService.setSyncFrequency(steamId64, enumFrequency);
            
            // Also update the next sync time display
            matchHistoryService.scheduleNextSync(steamId64, enumFrequency);
            
            // Update UI to reflect new schedule
            updateSyncStatus();
        } catch (Exception e) {
            logger.error("Error setting sync frequency", e);
            showErrorAlert("Failed to update sync frequency: " + e.getMessage());
        }
    }
    
    /**
     * Updates the UI with the latest sync status information.
     */
    private void updateSyncStatus() {
        if (currentUser == null || matchHistoryService == null) {
            logger.debug("Cannot update sync status - user or service not available");
            return;
        }
        
        try {
            long accountId = Long.parseLong(currentUser.getSteamId());
            Map<String, Object> syncStatus = matchHistoryService.getMatchHistorySyncStatus(accountId);
            
            if (syncStatus.isEmpty()) {
                logger.debug("No sync status found for user: {}", accountId);
                lastSyncLabel.setText("Last sync: Never");
                matchesSyncedLabel.setText("Matches synced: 0");
                nextSyncLabel.setText("Next scheduled sync: Not scheduled");
                
                // Set default frequency to Daily if not set
                if (syncFrequencyComboBox.getSelectionModel().isEmpty()) {
                    syncFrequencyComboBox.getSelectionModel().select("Daily");
                }
                
                return;
            }
            
            // Update last sync time
            Long lastSyncTimestamp = (Long) syncStatus.get("lastSyncTimestamp");
            if (lastSyncTimestamp != null) {
                Date lastSyncDate = new Date(lastSyncTimestamp);
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy 'at' HH:mm");
                lastSyncLabel.setText("Last sync: " + sdf.format(lastSyncDate));
            } else {
                lastSyncLabel.setText("Last sync: Never");
            }
            
            // Update matches count
            Integer matchesCount = (Integer) syncStatus.get("matchesCount");
            if (matchesCount != null) {
                matchesSyncedLabel.setText("Matches synced: " + matchesCount + " matches");
            } else {
                matchesSyncedLabel.setText("Matches synced: 0 matches");
            }
            
            // Update next sync time
            Long nextSyncTime = (Long) syncStatus.get("nextSyncDate");
            if (nextSyncTime != null) {
                Date nextSyncDate = new Date(nextSyncTime);
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy 'at' HH:mm");
                nextSyncLabel.setText("Next scheduled sync: " + sdf.format(nextSyncDate));
            } else {
                nextSyncLabel.setText("Next scheduled sync: Not scheduled");
            }
            
            // Update frequency selector to match stored value
            String syncFrequency = (String) syncStatus.get("syncFrequency");
            if (syncFrequency != null) {
                // Convert from enum format (e.g., "DAILY") to display format ("Daily")
                String displayFrequency = syncFrequency.substring(0, 1).toUpperCase() + 
                                        syncFrequency.substring(1).toLowerCase();
                syncFrequencyComboBox.getSelectionModel().select(displayFrequency);
            } else {
                // Default to Daily
                syncFrequencyComboBox.getSelectionModel().select("Daily");
            }
            
            // Update status label if not already in progress
            Boolean syncInProgressFromDb = (Boolean) syncStatus.get("syncInProgress");
            if (Boolean.TRUE.equals(syncInProgressFromDb) && !syncInProgress) {
                // Sync is in progress according to DB but not tracked by UI
                syncInProgress = true;
                syncStatusLabel.setText("Status: Syncing...");
                syncStatusLabel.getStyleClass().removeAll("error", "completed");
                syncStatusLabel.getStyleClass().add("syncing");
                syncProgressIndicator.setVisible(true);
                syncNowButton.setDisable(true);
            } else if (Boolean.FALSE.equals(syncInProgressFromDb) && syncInProgress) {
                // Sync was in progress but has now completed
                resetSyncStatus();
            }
            
        } catch (Exception e) {
            logger.error("Error updating sync status", e);
        }
    }
    
    /**
     * Shows an error alert with the specified message.
     * 
     * @param message The error message to display
     */
    private void showErrorAlert(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Sync Error");
        alert.setHeaderText("Data Synchronization Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Cleans up resources used by this controller.
     * Should be called when the application is closing.
     */
    public void shutdown() {
        logger.info("Shutting down SyncSettingsController");
        if (statusRefreshTimer != null) {
            statusRefreshTimer.cancel();
            statusRefreshTimer = null;
        }
    }
}