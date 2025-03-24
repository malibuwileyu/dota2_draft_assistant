package com.dota2assistant.ui.controller;

import com.dota2assistant.AppConfig;
import com.dota2assistant.data.service.MatchEnrichmentService;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controller for the admin monitoring view.
 * Provides statistics and insights into the match enrichment process.
 */
public class AdminMonitoringController implements Initializable, MatchEnrichmentService.MatchProcessingListener {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminMonitoringController.class);
    
    // UI components
    @FXML private Label queueSizeLabel;
    @FXML private Label processingCountLabel;
    @FXML private Label queueCapacityLabel;
    @FXML private Label apiRequestsLabel;
    @FXML private Label totalProcessedLabel;
    @FXML private Label successfulLabel;
    @FXML private Label failedLabel;
    @FXML private Label waitingForRetryLabel;
    @FXML private ProgressBar queueUtilizationBar;
    @FXML private ProgressBar successRateBar;
    @FXML private TableView<MatchProcessingRecord> matchHistoryTable;
    @FXML private TableColumn<MatchProcessingRecord, Long> matchIdColumn;
    @FXML private TableColumn<MatchProcessingRecord, String> statusColumn;
    @FXML private TableColumn<MatchProcessingRecord, Integer> retryCountColumn;
    @FXML private TableColumn<MatchProcessingRecord, LocalDateTime> lastAttemptColumn;
    @FXML private TableColumn<MatchProcessingRecord, LocalDateTime> nextAttemptColumn;
    @FXML private Button forceEnrichmentButton;
    
    private final ObservableList<MatchProcessingRecord> matchProcessingRecords = FXCollections.observableArrayList();
    private ScheduledExecutorService refreshScheduler;
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Record to track match processing status in the UI
     */
    public static class MatchProcessingRecord {
        private final long matchId;
        private String status;
        private int retryCount;
        private LocalDateTime lastAttempt;
        private LocalDateTime nextAttempt;
        private String errorType; // Added field for error type
        private String errorMessage; // Added field for error message
        
        public MatchProcessingRecord(long matchId, String status, int retryCount, 
                                     LocalDateTime lastAttempt, LocalDateTime nextAttempt) {
            this(matchId, status, retryCount, lastAttempt, nextAttempt, null, null);
        }
        
        public MatchProcessingRecord(long matchId, String status, int retryCount, 
                                     LocalDateTime lastAttempt, LocalDateTime nextAttempt,
                                     String errorType, String errorMessage) {
            this.matchId = matchId;
            this.status = status;
            this.retryCount = retryCount;
            this.lastAttempt = lastAttempt;
            this.nextAttempt = nextAttempt;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }

        public long getMatchId() {
            return matchId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }

        public LocalDateTime getLastAttempt() {
            return lastAttempt;
        }

        public void setLastAttempt(LocalDateTime lastAttempt) {
            this.lastAttempt = lastAttempt;
        }

        public LocalDateTime getNextAttempt() {
            return nextAttempt;
        }

        public void setNextAttempt(LocalDateTime nextAttempt) {
            this.nextAttempt = nextAttempt;
        }
        
        public String getErrorType() {
            return errorType;
        }
        
        public void setErrorType(String errorType) {
            this.errorType = errorType;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Match ID: ").append(matchId).append("\n");
            sb.append("Status: ").append(status).append("\n");
            sb.append("Retry Count: ").append(retryCount);
            
            if (errorType != null && !errorType.isEmpty()) {
                sb.append("\nError Type: ").append(errorType);
            }
            
            if (errorMessage != null && !errorMessage.isEmpty()) {
                sb.append("\nDetails: ").append(errorMessage);
            }
            
            return sb.toString();
        }
    }
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTableColumns();
        matchHistoryTable.setItems(matchProcessingRecords);
        
        // Enable button only when a row is selected
        forceEnrichmentButton.disableProperty().bind(
            matchHistoryTable.getSelectionModel().selectedItemProperty().isNull());
        
        // Initial data refresh
        refreshStatistics();
        
        // Schedule periodic refresh every 30 seconds
        refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "admin-monitor-refresh");
            t.setDaemon(true);
            return t;
        });
        refreshScheduler.scheduleAtFixedRate(this::refreshStatistics, 30, 30, TimeUnit.SECONDS);
        
        // Register as listener for match processing events
        try {
            MatchEnrichmentService enrichmentService = AppConfig.getMatchEnrichmentService();
            if (enrichmentService != null) {
                enrichmentService.addMatchProcessingListener(this);
                logger.info("Registered as match processing listener");
            }
        } catch (Exception e) {
            logger.error("Failed to register as match processing listener", e);
        }
        
        logger.info("Admin monitoring view initialized");
    }
    
    /**
     * Implementation of the MatchProcessingListener interface.
     * Called whenever a match is processed.
     */
    @Override
    public void onMatchProcessed(long matchId, boolean success, int retryCount, String message) {
        String status = success ? "SUCCESS" : (retryCount >= 5 ? "FAILED" : "WAITING");
        LocalDateTime lastAttempt = LocalDateTime.now();
        
        // Calculate next attempt time based on exponential backoff
        LocalDateTime nextAttempt = null;
        if (!success && retryCount < 5) {
            long backoffHours = 1L << Math.min(retryCount - 1, 6); // Exponential backoff up to 64 hours
            nextAttempt = lastAttempt.plusHours(backoffHours);
        }
        
        // Extract error type from message if available
        String errorType = null;
        String errorMessage = message;
        
        if (message != null && message.contains(":")) {
            String[] parts = message.split(":", 2);
            if (parts.length == 2) {
                // Check for common error patterns
                if (message.contains("Invalid match ID")) {
                    errorType = "INVALID_ID";
                    status = "INVALID";
                } else if (message.contains("404")) {
                    errorType = "NOT_FOUND";
                    status = "NOT_FOUND";
                } else if (message.contains("500")) {
                    errorType = "SERVER_ERROR";
                } else if (message.contains("429")) {
                    errorType = "RATE_LIMITED";
                } else {
                    errorType = "OTHER_ERROR";
                }
                errorMessage = parts[1].trim();
            }
        }
        
        // Add to the table with error information
        addMatchRecord(matchId, status, retryCount, lastAttempt, nextAttempt, errorType, errorMessage);
        
        // Show notification for high retry counts or specific error types
        boolean shouldNotify = !success && (
            retryCount >= 3 || 
            "INVALID_ID".equals(errorType) || 
            "SERVER_ERROR".equals(errorType)
        );
        
        if (shouldNotify) {
            showMatchProcessingNotification(matchId, status, retryCount, message);
        }
    }
    
    /**
     * Shows a notification about match processing issues
     * Uses the central notification area instead of showing popup alerts
     */
    private void showMatchProcessingNotification(long matchId, String status, int retryCount, String message) {
        Platform.runLater(() -> {
            try {
                // Create a simplified error message for the notification area
                String errorMsg = "Match " + matchId + " failed processing: " + status;
                
                // Get the main controller to use its notification area
                MainController mainController = AppConfig.getMainController();
                if (mainController != null) {
                    // Use the main controller's notification system (which is properly batched)
                    mainController.addMatchProcessingError(errorMsg);
                } else {
                    // If we can't access the main controller, just log the error
                    logger.warn("Can't display notification for match {}: {}", matchId, errorMsg);
                }
            } catch (Exception e) {
                logger.error("Error showing notification", e);
            }
        });
    }
    
    /**
     * Sets up the table columns with cell value factories and cell formatters
     */
    private void setupTableColumns() {
        // Match ID column
        matchIdColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getMatchId()));
        
        // Status column with color coding
        statusColumn.setCellValueFactory(data -> {
            MatchProcessingRecord record = data.getValue();
            String status = record.getStatus();
            String errorType = record.getErrorType();
            
            // If there's an error type, include it in the displayed status
            if (errorType != null && !errorType.isEmpty()) {
                return new SimpleStringProperty(status + " (" + errorType + ")");
            } else {
                return new SimpleStringProperty(status);
            }
        });
        
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    
                    if (item.contains("SUCCESS")) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else if (item.contains("FAILED")) {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else if (item.contains("INVALID")) {
                        setStyle("-fx-text-fill: darkred; -fx-font-weight: bold;");
                    } else if (item.contains("NOT_FOUND")) {
                        setStyle("-fx-text-fill: purple; -fx-font-weight: bold;");
                    } else if (item.contains("WAITING")) {
                        setStyle("-fx-text-fill: orange;");
                    } else if (item.contains("QUEUED")) {
                        setStyle("-fx-text-fill: blue;");
                    } else if (item.contains("SERVER_ERROR")) {
                        setStyle("-fx-text-fill: brown; -fx-font-weight: bold;");
                    } else if (item.contains("RATE_LIMITED")) {
                        setStyle("-fx-text-fill: darkblue; -fx-font-weight: bold;");
                    }
                }
            }
        });
        
        // Retry count column
        retryCountColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getRetryCount()).asObject());
        
        // Last attempt column with date formatting
        lastAttemptColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getLastAttempt()));
        lastAttemptColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.format(dateFormatter));
                }
            }
        });
        
        // Next attempt column with date formatting
        nextAttemptColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getNextAttempt()));
        nextAttemptColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("N/A");
                } else {
                    setText(item.format(dateFormatter));
                }
            }
        });
    }
    
    /**
     * Refreshes all statistics displayed in the view
     */
    private void refreshStatistics() {
        try {
            MatchEnrichmentService enrichmentService = AppConfig.getMatchEnrichmentService();
            if (enrichmentService == null) {
                logger.warn("Match enrichment service not available");
                return;
            }
            
            // Get current statistics
            Map<String, Object> stats = enrichmentService.getStatistics();
            
            // Update UI on JavaFX thread
            Platform.runLater(() -> updateUI(stats));
            
        } catch (Exception e) {
            logger.error("Error refreshing statistics", e);
        }
    }
    
    /**
     * Updates the UI with the latest statistics
     */
    private void updateUI(Map<String, Object> stats) {
        // Queue statistics
        int queueSize = (Integer) stats.getOrDefault("queueSize", 0);
        int queueCapacity = (Integer) stats.getOrDefault("queueCapacity", 1);
        int currentlyProcessing = (Integer) stats.getOrDefault("currentlyProcessing", 0);
        int apiRequestsLastMin = (Integer) stats.getOrDefault("apiRequestsLastMinute", 0);
        int apiRateLimit = (Integer) stats.getOrDefault("apiRateLimit", 60);
        
        queueSizeLabel.setText(queueSize + "/" + queueCapacity);
        processingCountLabel.setText(String.valueOf(currentlyProcessing));
        queueCapacityLabel.setText(String.valueOf(queueCapacity));
        apiRequestsLabel.setText(apiRequestsLastMin + "/" + apiRateLimit);
        
        // Processing statistics
        int totalProcessed = (Integer) stats.getOrDefault("processedTotal", 0);
        int successCount = (Integer) stats.getOrDefault("successCount", 0);
        int failedTotal = (Integer) stats.getOrDefault("failedTotal", 0);
        int waitingForRetry = (Integer) stats.getOrDefault("waitingForRetryCount", 0);
        
        totalProcessedLabel.setText(String.valueOf(totalProcessed));
        successfulLabel.setText(String.valueOf(successCount));
        failedLabel.setText(String.valueOf(failedTotal));
        waitingForRetryLabel.setText(String.valueOf(waitingForRetry));
        
        // Progress bars
        double queueUtilization = queueSize / (double) queueCapacity;
        queueUtilizationBar.setProgress(queueUtilization);
        
        // Success rate (avoid division by zero)
        double successRate = totalProcessed > 0 ? successCount / (double) totalProcessed : 0;
        successRateBar.setProgress(successRate);
        
        // Update color based on utilization
        if (queueUtilization > 0.9) {
            queueUtilizationBar.setStyle("-fx-accent: #C23C2A;"); // Red for high utilization
        } else if (queueUtilization > 0.7) {
            queueUtilizationBar.setStyle("-fx-accent: #E29C45;"); // Orange for medium utilization
        } else {
            queueUtilizationBar.setStyle("-fx-accent: #92A525;"); // Green for low utilization
        }
    }
    
    /**
     * Manually refreshes the statistics
     */
    @FXML
    private void onRefresh() {
        refreshStatistics();
    }
    
    /**
     * Clears the history table
     */
    @FXML
    private void onClearHistory() {
        matchProcessingRecords.clear();
    }
    
    /**
     * Forces enrichment for the selected match
     */
    @FXML
    private void onForceEnrichment() {
        MatchProcessingRecord selectedRecord = matchHistoryTable.getSelectionModel().getSelectedItem();
        if (selectedRecord == null) {
            return;
        }
        
        try {
            MatchEnrichmentService enrichmentService = AppConfig.getMatchEnrichmentService();
            if (enrichmentService == null) {
                logger.warn("Match enrichment service not available");
                return;
            }
            
            long matchId = selectedRecord.getMatchId();
            boolean success = enrichmentService.enqueueMatchForEnrichment(matchId, true);
            
            if (success) {
                // Update the status in the table
                selectedRecord.setStatus("QUEUED");
                selectedRecord.setLastAttempt(LocalDateTime.now());
                matchHistoryTable.refresh();
                
                // Show confirmation alert
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Enrichment Queued");
                alert.setHeaderText("Match Queued for Enrichment");
                alert.setContentText("Match ID " + matchId + " has been successfully queued for enrichment with priority.");
                alert.showAndWait();
            } else {
                // Show error alert
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Enrichment Failed");
                alert.setHeaderText("Failed to Queue Match");
                alert.setContentText("Could not queue match ID " + matchId + " for enrichment. The match may already be in the queue or processing.");
                alert.showAndWait();
            }
        } catch (Exception e) {
            logger.error("Error forcing enrichment for match", e);
            
            // Show error alert
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Enrichment Error");
            alert.setHeaderText("Error During Enrichment");
            alert.setContentText("An error occurred while trying to enqueue the match: " + e.getMessage());
            alert.showAndWait();
        }
    }
    
    /**
     * Adds a new match processing record to the history table
     */
    public void addMatchRecord(long matchId, String status, int retryCount,
                              LocalDateTime lastAttempt, LocalDateTime nextAttempt) {
        addMatchRecord(matchId, status, retryCount, lastAttempt, nextAttempt, null, null);
    }
    
    /**
     * Adds a new match processing record to the history table with error information
     */
    public void addMatchRecord(long matchId, String status, int retryCount,
                              LocalDateTime lastAttempt, LocalDateTime nextAttempt,
                              String errorType, String errorMessage) {
        Platform.runLater(() -> {
            // Check if the match ID already exists in the table
            boolean exists = false;
            for (MatchProcessingRecord record : matchProcessingRecords) {
                if (record.getMatchId() == matchId) {
                    // Update existing record
                    record.setStatus(status);
                    record.setRetryCount(retryCount);
                    record.setLastAttempt(lastAttempt);
                    record.setNextAttempt(nextAttempt);
                    record.setErrorType(errorType);
                    record.setErrorMessage(errorMessage);
                    exists = true;
                    break;
                }
            }
            
            // Add new record if not found
            if (!exists) {
                matchProcessingRecords.add(new MatchProcessingRecord(
                    matchId, status, retryCount, lastAttempt, nextAttempt, errorType, errorMessage));
            }
            
            // Refresh the table
            matchHistoryTable.refresh();
            
            // Sort by status (errors first) then by retry count (descending)
            matchHistoryTable.getItems().sort((r1, r2) -> {
                // Special statuses (INVALID, NOT_FOUND, etc.) come first
                if (!r1.getStatus().equals(r2.getStatus())) {
                    if ("INVALID".equals(r1.getStatus()) || "NOT_FOUND".equals(r1.getStatus())) {
                        return -1;
                    } else if ("INVALID".equals(r2.getStatus()) || "NOT_FOUND".equals(r2.getStatus())) {
                        return 1;
                    } else if ("FAILED".equals(r1.getStatus())) {
                        return -1;
                    } else if ("FAILED".equals(r2.getStatus())) {
                        return 1;
                    }
                }
                
                // Then by retry count (descending)
                return Integer.compare(r2.getRetryCount(), r1.getRetryCount());
            });
        });
    }
    
    /**
     * Cleans up resources when the controller is no longer needed
     */
    public void shutdown() {
        // Unregister from the match processing service
        try {
            MatchEnrichmentService enrichmentService = AppConfig.getMatchEnrichmentService();
            if (enrichmentService != null) {
                enrichmentService.removeMatchProcessingListener(this);
                logger.info("Unregistered from match processing events");
            }
        } catch (Exception e) {
            logger.error("Error unregistering from match processing events", e);
        }
        
        // Shut down the scheduler
        if (refreshScheduler != null) {
            refreshScheduler.shutdown();
            try {
                if (!refreshScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    refreshScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                refreshScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}