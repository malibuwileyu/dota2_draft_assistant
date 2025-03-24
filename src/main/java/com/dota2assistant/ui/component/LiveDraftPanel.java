package com.dota2assistant.ui.component;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.PlayerHeroPerformance;
import com.dota2assistant.gsi.GsiDraftRecommendationService;
import com.dota2assistant.gsi.GsiConfig;
import com.dota2assistant.gsi.model.DraftState;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * UI component for displaying live draft information from GSI.
 * This panel shows the current draft state and provides recommendations.
 */
@Component
public class LiveDraftPanel extends BorderPane {

    private final GsiDraftRecommendationService gsiDraftRecommendationService;
    private final GsiConfig gsiConfig;
    private final GsiSetupComponent setupComponent;
    private final GsiDraftOverlayView overlayView;
    
    private final BooleanProperty gsiConfigured = new SimpleBooleanProperty(false);
    private final BooleanProperty dota2Running = new SimpleBooleanProperty(false);
    private final TabPane tabPane = new TabPane();
    private final Label statusLabel = new Label("Waiting for GSI connection...");
    private HBox controlBar; // Control bar for bottom buttons
    private java.util.concurrent.ScheduledExecutorService processMonitor; // For checking if Dota 2 is running
    
    @Autowired
    public LiveDraftPanel(
            GsiDraftRecommendationService gsiDraftRecommendationService,
            GsiConfig gsiConfig,
            GsiSetupComponent setupComponent,
            GsiDraftOverlayView overlayView) {
        
        this.gsiDraftRecommendationService = gsiDraftRecommendationService;
        this.gsiConfig = gsiConfig;
        this.setupComponent = setupComponent;
        this.overlayView = overlayView;
        
        initialize();
    }
    
    private void initialize() {
        setPadding(new Insets(10));
        
        // Create the setup tab
        Tab setupTab = new Tab("GSI Setup");
        setupTab.setContent(setupComponent);
        setupTab.setClosable(false);
        
        // Create the live draft tab
        Tab liveTab = new Tab("Live Draft");
        liveTab.setContent(overlayView);
        liveTab.setClosable(false);
        
        // Add tabs to the tab pane
        tabPane.getTabs().addAll(setupTab, liveTab);
        
        // Style the status label
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        statusLabel.setTextFill(Color.DARKGRAY);
        statusLabel.setPadding(new Insets(10, 0, 0, 0));
        
        // Start the Dota 2 process monitor
        startProcessMonitor();
        
        // Start with the setup tab selected initially
        tabPane.getSelectionModel().select(setupTab);
        
        // Attempt automatic GSI config installation after the UI is fully initialized
        javafx.application.Platform.runLater(() -> {
            try {
                // First check if GSI config is already installed
                GsiConfig.GsiStatus gsiStatus = gsiConfig.getGsiStatus();
                if (gsiStatus.isConfigInstalled()) {
                    statusLabel.setText("GSI config is already installed!");
                    statusLabel.setTextFill(Color.GREEN);
                    // Switch to the live draft tab since config is installed
                    tabPane.getSelectionModel().select(liveTab);
                    // Rename reinstall button to indicate it's a reinstall
                    if (controlBar.getChildren().size() > 1 && 
                        controlBar.getChildren().get(1) instanceof Button) {
                        ((Button) controlBar.getChildren().get(1)).setText("Reinstall GSI Config");
                    }
                    return;
                }
                
                // If not installed, proceed with installation
                boolean success = gsiConfig.installConfig();
                String fallbackPath = gsiConfig.getFallbackConfigPath();
                String elevationScriptPath = gsiConfig.getElevationScriptPath();
                
                if (success && fallbackPath == null) {
                    // Direct success in the correct location
                    statusLabel.setText("GSI config installed automatically!");
                    statusLabel.setTextFill(Color.GREEN);
                    // Switch to the live draft tab since config is installed
                    tabPane.getSelectionModel().select(liveTab);
                } else if (success && elevationScriptPath != null && 
                          (elevationScriptPath.contains("install_gsi_config_elevated.bat") ||
                           elevationScriptPath.contains("copy_gsi_config"))) {
                    // Direct UAC/sudo prompt was attempted
                    statusLabel.setText("Please respond to admin privileges prompt...");
                    statusLabel.setTextFill(Color.ORANGE);
                    
                    // After a delay, check if the GSI config file exists in the target location
                    java.util.Timer timer = new java.util.Timer();
                    timer.schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            javafx.application.Platform.runLater(() -> {
                                // Check if the config is installed now
                                if (gsiConfig.getGsiStatus().isConfigInstalled()) {
                                    statusLabel.setText("GSI config installed successfully with administrator privileges!");
                                    statusLabel.setTextFill(Color.GREEN);
                                    // Switch to the live draft tab since config is installed
                                    tabPane.getSelectionModel().select(liveTab);
                                } else {
                                    statusLabel.setText("Admin installation attempted - check setup tab for details");
                                }
                            });
                        }
                    }, 5000); // Check after 5 seconds
                    
                    // Create a button to check the setup tab
                    Button checkButton = new Button("Check Installation Status");
                    checkButton.setOnAction(e -> {
                        tabPane.getSelectionModel().select(setupTab);
                    });
                    
                    // Replace the reinstall button with the check button
                    controlBar.getChildren().set(1, checkButton);
                    
                    // Keep setup tab selected for now
                } else if (success && elevationScriptPath != null) {
                    // Manual elevation script is available
                    String scriptName = new File(elevationScriptPath).getName();
                    statusLabel.setText("Admin installation needed - check setup tab to run " + scriptName);
                    statusLabel.setTextFill(Color.ORANGE);
                    
                    // Create a quick button to open the elevation script
                    Button elevateButton = new Button("Run Admin Installer");
                    elevateButton.setOnAction(e -> {
                        try {
                            // Show setup tab
                            tabPane.getSelectionModel().select(setupTab);
                            
                            // Open the directory containing the script
                            String scriptDirectory = new File(elevationScriptPath).getParent();
                            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                                new ProcessBuilder("explorer.exe", "/select,", elevationScriptPath).start();
                            } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                                new ProcessBuilder("open", "-R", elevationScriptPath).start();
                            } else {
                                new ProcessBuilder("xdg-open", scriptDirectory).start();
                            }
                        } catch (Exception ex) {
                            statusLabel.setText("Could not open file browser - check setup tab for details");
                        }
                    });
                    
                    // Replace the reinstall button with the elevate button
                    controlBar.getChildren().set(1, elevateButton);
                    
                    // Keep setup tab selected since user needs to run the admin installer
                } else if (success && fallbackPath != null) {
                    // Success but using fallback location without elevation script
                    statusLabel.setText("GSI config created in fallback location - check setup tab for details");
                    statusLabel.setTextFill(Color.ORANGE);
                    // Keep setup tab selected since user needs to manually copy the file
                } else {
                    // Complete failure
                    statusLabel.setText("Automatic GSI config installation failed - please use manual installation");
                    statusLabel.setTextFill(Color.ORANGE);
                    // Keep the setup tab selected since manual installation is needed
                }
            } catch (Exception ex) {
                statusLabel.setText("Automatic GSI config installation failed - please use manual installation");
                statusLabel.setTextFill(Color.ORANGE);
            }
        });
        
        // When GSI is configured, switch to the live draft tab
        overlayView.gsiSetupCompleteProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                tabPane.getSelectionModel().select(liveTab);
                statusLabel.setText("GSI connected and ready!");
                statusLabel.setTextFill(Color.GREEN);
                gsiConfigured.set(true);
            }
        });
        
        // If draft becomes active, update the status label
        gsiDraftRecommendationService.draftActiveProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                statusLabel.setText("Live draft in progress!");
                statusLabel.setTextFill(Color.GREEN);
            } else if (gsiConfigured.get()) {
                statusLabel.setText("GSI connected - waiting for draft to start");
                statusLabel.setTextFill(Color.GREEN);
            }
        });
        
        // Helper method for reinstall functionality
        Runnable performReinstall = () -> {
            boolean installSuccess = gsiConfig.installConfig();
            if (installSuccess) {
                statusLabel.setText("GSI config reinstalled successfully!");
                statusLabel.setTextFill(Color.GREEN);
            } else {
                statusLabel.setText("GSI config installation failed - check setup tab");
                statusLabel.setTextFill(Color.RED);
            }
        };
        
        // Create the reinstall button (renamed from Quick Install)
        Button reinstallButton = new Button("Reinstall GSI Config");
        reinstallButton.setOnAction(e -> {
            // Check if config is already installed
            GsiConfig.GsiStatus gsiStatus = gsiConfig.getGsiStatus();
            if (gsiStatus.isConfigInstalled()) {
                // Show confirmation dialog since the config is already working
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.CONFIRMATION,
                    "GSI config is already installed. Are you sure you want to reinstall it?",
                    javafx.scene.control.ButtonType.YES, 
                    javafx.scene.control.ButtonType.NO
                );
                alert.setTitle("Confirm Reinstall");
                alert.setHeaderText("Reinstall GSI Configuration");
                
                alert.showAndWait().ifPresent(response -> {
                    if (response == javafx.scene.control.ButtonType.YES) {
                        performReinstall.run();
                    }
                });
            } else {
                // If not installed, proceed directly with installation
                performReinstall.run();
            }
        });
        
        // Create bottom control bar with status and install button
        controlBar = new HBox(20);
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.setPadding(new Insets(10, 0, 0, 0));
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        controlBar.getChildren().addAll(statusLabel, reinstallButton);
        
        // Add the components to the layout
        setCenter(tabPane);
        setBottom(controlBar);
    }
    
    /**
     * Updates the panel with current Steam ID information
     * 
     * @param steamId The current user's Steam ID for personalized recommendations
     */
    public void updateCurrentPlayer(Long steamId) {
        // Pass the Steam ID to the recommendation service for personalized recommendations
        if (steamId != null && steamId > 0) {
            gsiDraftRecommendationService.setCurrentPlayerSteamId(steamId);
        }
    }
    
    /**
     * Gets the GSI configuration object
     * @return The GsiConfig object
     */
    public GsiConfig getGsiConfig() {
        return this.gsiConfig;
    }
    
    /**
     * Gets the GSI server object through the setup component
     * @return The GsiServer object if available, null otherwise
     */
    public com.dota2assistant.gsi.GsiServer getGsiServer() {
        if (this.setupComponent != null) {
            return this.setupComponent.getGsiServer();
        }
        return null;
    }
    
    /**
     * Starts a background monitor that periodically checks if Dota 2 is running.
     * This updates the dota2Running property which can be used for automatic connection management.
     * It also automatically manages the GSI connection when Dota 2 starts or stops.
     */
    private void startProcessMonitor() {
        // Create a scheduled executor service
        processMonitor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Dota2-Process-Monitor");
            t.setDaemon(true); // Make it a daemon thread so it doesn't block application shutdown
            return t;
        });
        
        // Schedule a task to check if Dota 2 is running every 10 seconds
        processMonitor.scheduleAtFixedRate(() -> {
            try {
                // Check if Dota 2 is running using the ProcessDetector
                boolean isRunning = com.dota2assistant.util.ProcessDetector.isDota2Running();
                
                // Update the property on the JavaFX thread
                javafx.application.Platform.runLater(() -> {
                    boolean wasRunning = dota2Running.get();
                    dota2Running.set(isRunning);
                    
                    // If the running state changed, update the UI and handle connection
                    if (isRunning != wasRunning) {
                        if (isRunning) {
                            // Dota 2 has just started - update status and check GSI connection
                            statusLabel.setText("Dota 2 detected - GSI ready to connect");
                            statusLabel.setTextFill(Color.GREEN);
                            
                            // Check if GSI config is installed
                            if (gsiConfig != null && gsiConfig.getGsiStatus().isConfigInstalled()) {
                                // Optional: Automatically connect when Dota 2 starts
                                // Uncomment this to enable auto-connection when Dota 2 is launched
                                /*
                                // Use a slight delay to ensure Dota 2 is fully initialized
                                java.util.Timer connectTimer = new java.util.Timer();
                                connectTimer.schedule(new java.util.TimerTask() {
                                    @Override
                                    public void run() {
                                        javafx.application.Platform.runLater(() -> {
                                            // Restart the GSI server
                                            restartGsiServer();
                                        });
                                    }
                                }, 5000); // 5 second delay
                                */
                            }
                        } else {
                            // Dota 2 has just closed - update status
                            statusLabel.setText("Dota 2 is not running");
                            statusLabel.setTextFill(Color.RED);
                            
                            // Stop the GSI server if it's running to free up resources
                            stopGsiServerIfRunning();
                        }
                    }
                });
            } catch (Exception e) {
                // Log any errors but keep the monitor running
                org.slf4j.LoggerFactory.getLogger(LiveDraftPanel.class)
                    .error("Error in Dota 2 process monitor: {}", e.getMessage());
            }
        }, 0, 10, java.util.concurrent.TimeUnit.SECONDS);
    }
    
    /**
     * Stops the GSI server if it's currently running.
     * This frees up resources when Dota 2 is closed.
     */
    private void stopGsiServerIfRunning() {
        try {
            // Get the GSI server through the setup component
            com.dota2assistant.gsi.GsiServer gsiServer = getGsiServer();
            if (gsiServer != null) {
                gsiServer.stop();
                org.slf4j.LoggerFactory.getLogger(LiveDraftPanel.class)
                    .info("GSI server stopped because Dota 2 was closed");
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(LiveDraftPanel.class)
                .error("Error stopping GSI server: {}", e.getMessage());
        }
    }
    
    /**
     * Restarts the GSI server to establish a new connection to Dota 2.
     * This is useful when Dota 2 is (re)started or when the connection needs to be refreshed.
     */
    public void restartGsiServer() {
        try {
            // Get the GSI server through the setup component
            com.dota2assistant.gsi.GsiServer gsiServer = getGsiServer();
            if (gsiServer != null) {
                // Stop and restart the server to ensure a fresh connection
                gsiServer.stop();
                Thread.sleep(500); // Brief pause
                gsiServer.start();
                
                org.slf4j.LoggerFactory.getLogger(LiveDraftPanel.class)
                    .info("GSI server restarted successfully");
                
                // Update status
                statusLabel.setText("GSI connection established");
                statusLabel.setTextFill(Color.GREEN);
            } else {
                org.slf4j.LoggerFactory.getLogger(LiveDraftPanel.class)
                    .error("Could not restart GSI server - server reference not available");
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(LiveDraftPanel.class)
                .error("Error restarting GSI server: {}", e.getMessage());
        }
    }
    
    /**
     * Gets the property that indicates whether Dota 2 is currently running.
     * This can be used to bind UI elements to the game's running state.
     * 
     * @return The BooleanProperty representing Dota 2's running state
     */
    public javafx.beans.property.BooleanProperty dota2RunningProperty() {
        return dota2Running;
    }
}