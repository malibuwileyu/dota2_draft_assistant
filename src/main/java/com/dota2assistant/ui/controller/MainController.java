package com.dota2assistant.ui.controller;

import com.dota2assistant.auth.SteamApiService;
import com.dota2assistant.auth.SteamUser;
import com.dota2assistant.core.ai.AiDecisionEngine;
import com.dota2assistant.core.analysis.AnalysisEngine;
import com.dota2assistant.core.analysis.HeroRecommendation;
import com.dota2assistant.core.draft.CaptainsModeDraftEngine;
import com.dota2assistant.core.draft.DraftEngine;
import com.dota2assistant.core.draft.DraftMode;
import com.dota2assistant.core.draft.DraftPhase;
import com.dota2assistant.core.draft.Team;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.PlayerHeroPerformance;
import com.dota2assistant.data.model.PlayerHeroStat;
import com.dota2assistant.data.model.PlayerMatch;
import com.dota2assistant.data.service.PlayerRecommendationService;
import com.dota2assistant.ui.component.HeroCell;
import com.dota2assistant.ui.component.HeroGridView;
import com.dota2assistant.ui.controller.UserStatusController;
import com.dota2assistant.util.PropertyLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Control;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MainController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    private final DraftEngine draftEngine;
    private final AiDecisionEngine aiEngine;
    private final AnalysisEngine analysisEngine;
    private final ExecutorService executorService;
    private final ScheduledExecutorService timerService;
    private PlayerRecommendationService playerRecommendationService;
    
    private ObservableList<Hero> allHeroes = FXCollections.observableArrayList();
    private ObservableList<Hero> filteredHeroes = FXCollections.observableArrayList();
    private ObservableList<HeroRecommendation> recommendedPicks = FXCollections.observableArrayList();
    private ObservableList<HeroRecommendation> recommendedBans = FXCollections.observableArrayList();
    private ObservableList<String> draftTimeline = FXCollections.observableArrayList();
    
    // Map to track player performance data for heroes
    private Map<Integer, PlayerHeroPerformance> heroPerformanceMap = new HashMap<>();
    
    private Hero selectedHero;
    private final SimpleBooleanProperty draftInProgress = new SimpleBooleanProperty(false);
    private boolean summaryShown = false; // Flag to track if summary has been shown
    private HeroGridView heroGridView;
    
    @FXML
    private ComboBox<DraftMode> draftModeComboBox;
    
    @FXML
    private ComboBox<String> pickOrderComboBox;
    
    @FXML
    private ComboBox<String> mapSideComboBox;
    
    @FXML
    private ComboBox<String> opponentLevelComboBox;
    
    @FXML
    private CheckBox timedModeCheckBox;
    
    @FXML
    private Button startEndDraftButton;
    
    @FXML
    private ComboBox<String> heroFilterComboBox;
    
    @FXML
    private FlowPane heroSelectionPane;
    
    @FXML
    private GridPane radiantPicksGrid;
    
    @FXML
    private GridPane direPicksGrid;
    
    @FXML
    private FlowPane radiantBansPane;
    
    @FXML
    private FlowPane direBansPane;
    
    @FXML
    private ListView<com.dota2assistant.core.analysis.HeroRecommendation> recommendedPicksListView;
    
    @FXML
    private ListView<com.dota2assistant.core.analysis.HeroRecommendation> recommendedBansListView;
    
    @FXML
    private ProgressBar radiantStrengthBar;
    
    @FXML
    private ProgressBar direStrengthBar;
    
    @FXML
    private TextArea analysisTextArea;
    
    @FXML
    private ListView<String> draftTimelineListView;
    
    @FXML
    private Label statusLabel;
    
    @FXML
    private Label timerLabel;
    
    // Tab-related controls
    @FXML
    private TabPane mainTabPane;
    
    @FXML
    private Tab draftTab;
    
    @FXML
    private Tab playerProfileTab;
    
    @FXML
    private Tab liveAssistantTab;
    
    // Player profile controls
    @FXML
    private ImageView playerAvatarLarge;
    
    @FXML
    private Label playerNameLabel;
    
    @FXML
    private Label steamIdLabel;
    
    @FXML
    private Label accountLevelLabel;
    
    @FXML
    private Button refreshProfileButton;
    
    @FXML
    private Button updateMatchesButton;
    
    @FXML
    private TabPane profileTabPane;
    
    @FXML
    private ComboBox<String> heroStatsFilterComboBox;
    
    @FXML
    private TextField heroSearchField;
    
    @FXML
    private TableView<PlayerHeroStat> heroStatsTable;
    
    @FXML
    private TableView<PlayerMatch> recentMatchesTable;
    
    // Live assistant controls
    @FXML
    private Button connectToGameButton;
    
    @FXML
    private Label gameConnectionStatus;
    
    @FXML
    private Button actionButton;
    
    @FXML
    private ProgressBar radiantWinBar;
    
    @FXML
    private ProgressBar winProgressBackground;
    
    @FXML
    private Label winPercentageLabel;
    
    @FXML
    private UserStatusController userStatusContainerController;
    
    @FXML
    private SyncSettingsController syncSettingsContainerController;
    
    private final PropertyLoader propertyLoader;
    
    public MainController(DraftEngine draftEngine, 
                          AiDecisionEngine aiEngine, 
                          AnalysisEngine analysisEngine,
                          ExecutorService executorService,
                          ScheduledExecutorService timerService,
                          PropertyLoader propertyLoader) {
        this.draftEngine = draftEngine;
        this.aiEngine = aiEngine;
        this.analysisEngine = analysisEngine;
        this.executorService = executorService;
        this.timerService = timerService;
        this.propertyLoader = propertyLoader;
        
        // PlayerRecommendationService will be set later when database connections are available
        // This typically happens after login
    }
    
    /**
     * Sets the player recommendation service when available.
     * This is called after database connections are established.
     * 
     * @param playerRecommendationService the service to set
     */
    public void setPlayerRecommendationService(PlayerRecommendationService playerRecommendationService) {
        this.playerRecommendationService = playerRecommendationService;
        
        // If we're already logged in, load player performance data
        if (userStatusContainerController != null && 
            userStatusContainerController.getUserService() != null && 
            userStatusContainerController.getUserService().isLoggedIn()) {
            loadPlayerHeroPerformance();
        }
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupComboBoxes();
        setupListViews();
        setupHeroGrid();
        setupTableViews(); // Setup player stats tables
        setupListeners();
        setupUserStatusHandler();
        setupSyncSettingsHandler(); // Setup sync settings handler
        setupTabListeners();
        applyCssClasses();
        setupWinProbabilityBar();
        resetUI();
        loadHeroes();
    }
    
    /**
     * Sets up listeners for tab selection changes
     */
    private void setupTabListeners() {
        // Add a listener to update the player profile when that tab is selected
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == playerProfileTab) {
                logger.info("Player profile tab selected - updating profile data");
                updatePlayerProfileForLoginState();
            }
        });
    }
    
    /**
     * Sets up the user status component and its handlers
     */
    private void setupUserStatusHandler() {
        if (userStatusContainerController != null && userStatusContainerController.getUserService() != null) {
            // Register for login state changes from UserService
            userStatusContainerController.getUserService().addLoginStateListener((evt) -> {
                boolean isLoggedIn = (boolean)evt.getNewValue();
                logger.info("Login state change detected in MainController: {}", 
                    isLoggedIn ? "logged in" : "logged out");
                
                // Update UI on JavaFX thread
                javafx.application.Platform.runLater(() -> {
                    // Update the player profile
                    updatePlayerProfileForLoginState();
                    // Also load player performance data if service is available
                    if (playerRecommendationService != null) {
                        loadPlayerHeroPerformance();
                    }
                    logger.info("MainController UI updated after login state change");
                });
            });
            // Set the callback for when login status changes
            userStatusContainerController.setOnLoginStatusChanged(isLoginRequested -> {
                if (isLoginRequested) {
                    // Navigate to login screen by initiating Steam auth flow
                    logger.info("User requested login - initiating Steam auth flow");
                    if (userStatusContainerController.getUserService() != null && 
                        userStatusContainerController.getUserService().getSteamApiService() != null) {
                        // Start the authentication process
                        userStatusContainerController.getUserService().getSteamApiService().startAuthFlow();
                    } else {
                        logger.error("Cannot start Steam auth flow - services not available");
                        showAlert(Alert.AlertType.ERROR, "Login Error", "Cannot initialize Steam login. Please restart the application.");
                    }
                } else {
                    logger.info("User logged out");
                    // Force UI update for the user status controller
                    if (userStatusContainerController != null) {
                        javafx.application.Platform.runLater(() -> {
                            // Ensure this runs on the JavaFX thread to update UI safely
                            try {
                                userStatusContainerController.updateUI();
                                logger.info("User status UI updated after logout");
                                
                                // Clear performance data
                                heroPerformanceMap.clear();
                                
                                // Reset hero grid to clear performance indicators
                                if (heroGridView != null) {
                                    heroGridView.updateHeroPerformanceData(new HashMap<>());
                                }
                                
                                // Update recommendations without player data
                                updateRecommendations();
                            } catch (Exception e) {
                                logger.error("Error updating UI after logout", e);
                            }
                        });
                    }
                    
                    // Update player profile tab to reflect logged out state
                    updatePlayerProfileForLoginState();
                }
            });
            
            // Make sure UI is up to date
            userStatusContainerController.updateUI();
            
            // Also update the player profile tab based on the current login state
            updatePlayerProfileForLoginState();
            
            logger.info("User status handler set up successfully");
        } else {
            logger.warn("UserStatusController not injected properly");
        }
    }
    
    /**
     * Updates the player profile tab UI based on the current login state.
     * This should be called whenever the login state changes.
     */
    private void updatePlayerProfileForLoginState() {
        logger.info("Updating player profile for current login state");
        boolean isLoggedIn = userStatusContainerController != null && 
                            userStatusContainerController.getUserService().isLoggedIn();
                            
        if (isLoggedIn) {
            logger.info("User is logged in - loading player profile data");
            // Load player profile data for the logged-in user
            loadPlayerProfile();
            
            // Also update sync settings with current user if controller is available
            if (syncSettingsContainerController != null) {
                syncSettingsContainerController.setUser(
                    userStatusContainerController.getUserService().getCurrentUser().orElse(null)
                );
            }
        } else {
            logger.info("User is not logged in - showing guest state");
            // Clear player profile and show not-logged-in state
            playerNameLabel.setText("Please log in to view your profile");
            steamIdLabel.setText("Not logged in");
            accountLevelLabel.setText("--");
            playerAvatarLarge.setImage(null);
            
            // Clear and update table placeholders
            clearPlayerProfileData();
            heroStatsTable.setPlaceholder(new Label("Please log in to view your hero statistics"));
            recentMatchesTable.setPlaceholder(new Label("Please log in to view your match history"));
            
            // Clear sync settings user
            if (syncSettingsContainerController != null) {
                syncSettingsContainerController.setUser(null);
            }
        }
    }
    
    /**
     * Sets up the sync settings component and injects services
     */
    private void setupSyncSettingsHandler() {
        // Check if the controller is properly initialized
        if (syncSettingsContainerController == null) {
            logger.warn("SyncSettingsController was not injected - sync settings will not be available");
            return;
        }
        
        try {
            // Get the required services from the user service (if available)
            if (userStatusContainerController != null && userStatusContainerController.getUserService() != null) {
                var userService = userStatusContainerController.getUserService();
                var steamApiService = userService.getSteamApiService();
                
                if (steamApiService != null) {
                    // Get match history and automated sync services from user match service
                    var userMatchService = steamApiService.getUserMatchService();
                    var matchHistoryService = userMatchService.getMatchHistoryService();
                    var automatedSyncService = userMatchService.getAutomatedSyncService();
                    
                    // Initialize the controller with these services
                    syncSettingsContainerController.setServices(matchHistoryService, automatedSyncService);
                    logger.info("Successfully initialized SyncSettingsController with services");
                    
                    // If user is already logged in, set the user
                    if (userService.isLoggedIn()) {
                        userService.getCurrentUser().ifPresent(user -> {
                            syncSettingsContainerController.setUser(user);
                            logger.info("Set current user in SyncSettingsController: {}", user.getUsername());
                        });
                    }
                } else {
                    logger.warn("SteamApiService is not available - sync settings will have limited functionality");
                }
            } else {
                logger.warn("UserStatusController or UserService is not available - sync settings will have limited functionality");
            }
        } catch (Exception e) {
            logger.error("Error setting up SyncSettingsController", e);
        }
    }
    
    /**
     * Sets up the win probability bar colors and styles
     */
    private void setupWinProbabilityBar() {
        // Apply direct styling to override any default theme settings
        String radiantColor = "#92A525"; // Green
        String direColor = "#C23C2A"; // Red
        
        // Apply styles directly to the progress bars
        radiantWinBar.setStyle("-fx-accent: " + radiantColor + ";");
        winProgressBackground.setStyle("-fx-accent: " + direColor + ";");
        
        // Set the initial progress (50/50)
        radiantWinBar.setProgress(0.5);
        winProgressBackground.setProgress(1.0);
        
        logger.info("Win probability bar styles applied - Radiant: {}, Dire: {}", radiantColor, direColor);
    }
    
    private void setupComboBoxes() {
        draftModeComboBox.setItems(FXCollections.observableArrayList(DraftMode.values()));
        draftModeComboBox.getSelectionModel().selectFirst();
        
        // Setup pick order combo box
        pickOrderComboBox.setItems(FXCollections.observableArrayList(
                "First Pick", "Second Pick"
        ));
        pickOrderComboBox.getSelectionModel().selectFirst();
        
        // Setup map side combo box
        mapSideComboBox.setItems(FXCollections.observableArrayList(
                "Radiant", "Dire"
        ));
        mapSideComboBox.getSelectionModel().selectFirst();
        
        opponentLevelComboBox.setItems(FXCollections.observableArrayList(
                "Herald", "Guardian", "Crusader", "Archon", "Legend", 
                "Ancient", "Divine", "Immortal", "Professional"
        ));
        opponentLevelComboBox.getSelectionModel().select(4); // Legend default
        
        heroFilterComboBox.setItems(FXCollections.observableArrayList(
                "All Heroes", "Strength", "Agility", "Intelligence",
                "Carry", "Support", "Nuker", "Disabler", "Durable"
        ));
        heroFilterComboBox.getSelectionModel().selectFirst();
    }
    
    private void setupListViews() {
        recommendedPicksListView.setItems(recommendedPicks);
        recommendedPicksListView.setCellFactory(lv -> new HeroListCell());
        recommendedPicksListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        selectedHero = newVal.getHero();
                        if (heroGridView != null) {
                            heroGridView.selectHero(newVal.getHero().getId());
                        }
                    }
                });
        
        recommendedBansListView.setItems(recommendedBans);
        recommendedBansListView.setCellFactory(lv -> new HeroListCell());
        recommendedBansListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        selectedHero = newVal.getHero();
                        if (heroGridView != null) {
                            heroGridView.selectHero(newVal.getHero().getId());
                        }
                    }
                });
        
        draftTimelineListView.setItems(draftTimeline);
    }
    
    private void setupHeroGrid() {
        // Initialize filtered heroes list
        filteredHeroes = FXCollections.observableArrayList();
        
        // Create the hero grid view with filtered heroes
        heroGridView = new HeroGridView(filteredHeroes);
        
        // Set layout constraints
        heroGridView.setPrefWidth(Region.USE_COMPUTED_SIZE);
        heroGridView.setPrefHeight(Region.USE_COMPUTED_SIZE);
        heroGridView.setMaxWidth(Double.MAX_VALUE);
        heroGridView.setMaxHeight(Double.MAX_VALUE);
        
        // Add to parent with proper constraints
        heroSelectionPane.getChildren().clear();
        heroSelectionPane.getChildren().add(heroGridView);
        FlowPane.setMargin(heroGridView, new Insets(10));
        
        // Set up hero selection callback
        heroGridView.setOnHeroSelected(hero -> {
            selectedHero = hero;
            logger.debug("Selected hero: {}", hero != null ? hero.getLocalizedName() : "none");
        });
        
        // Log setup completion
        logger.debug("Hero grid setup complete");
    }
    
    private void setupListeners() {
        heroSearchField.textProperty().addListener((obs, oldVal, newVal) -> filterHeroes());
        
        heroFilterComboBox.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> filterHeroes());
        
        draftInProgress.addListener((obs, oldVal, newVal) -> {
            boolean inProgress = newVal;
            
            // Disable draft settings during draft
            draftModeComboBox.setDisable(inProgress);
            opponentLevelComboBox.setDisable(inProgress);
            timedModeCheckBox.setDisable(inProgress);
            pickOrderComboBox.setDisable(inProgress);
            mapSideComboBox.setDisable(inProgress);
            
            // Reset UI state when starting/ending draft
            if (inProgress) {
                updateDraftStatus(); // Update button states based on current phase
            } else {
                updateActionButton(false, true); // Disable action button when not in progress
            }
        });
        
        // Setup listener for hero selection to update button enabled state
        // This will make action button only clickable when a hero is selected
        InvalidationListener heroSelectionListener = observable -> {
            logger.info("Hero selection listener triggered - Draft in progress: {}, selectedHero: {}", 
                       draftInProgress.get(), 
                       selectedHero != null ? selectedHero.getLocalizedName() : "null");
            
            if (draftInProgress.get() && selectedHero != null) {
                DraftPhase currentPhase = draftEngine.getCurrentPhase();
                Team currentTeam = draftEngine.getCurrentTeam();
                
                // Determine if it's player's turn
                Team playerTeam = mapSideComboBox.getValue().equals("Radiant") ? Team.RADIANT : Team.DIRE;
                boolean isPlayerTurn = currentTeam == playerTeam;
                
                // Update the player turn property to ensure UI is consistent
                updatePlayerTurnProperty(playerTeam);
                
                // Determine if it's pick or ban phase
                boolean isPick = currentPhase.toString().contains("PICK");
                
                logger.info("Hero selection processing - Phase: {}, currentTeam: {}, playerTeam: {}, isPlayerTurn: {}, isPick: {}", 
                          currentPhase, currentTeam, playerTeam, isPlayerTurn, isPick);
                
                // Update action button
                updateActionButton(isPlayerTurn, isPick);
            } else {
                // Either no draft in progress or no hero selected - disable button
                logger.info("Disabling action button - draft in progress: {}, selectedHero present: {}", 
                          draftInProgress.get(), selectedHero != null);
                updateActionButton(false, true);
            }
        };
        
        // Add a property to track hero selection changes
        // We'll use JavaFX property binding for this
        javafx.beans.property.ObjectProperty<Hero> selectedHeroProperty = 
                new javafx.beans.property.SimpleObjectProperty<>();
        
        // Update the property whenever the selectedHero changes
        selectedHeroProperty.addListener(heroSelectionListener);
        
        // Set up the property bridge from the heroGridView
        heroGridView.setOnHeroSelected(hero -> {
            selectedHero = hero;
            logger.info("Hero selected from grid: {}", hero != null ? hero.getLocalizedName() : "null");
            selectedHeroProperty.set(hero);
            
            // Directly update the action button state when hero is selected
            if (draftInProgress.get() && selectedHero != null) {
                DraftPhase currentPhase = draftEngine.getCurrentPhase();
                Team currentTeam = draftEngine.getCurrentTeam();
                
                // Determine if it's player's turn
                Team playerTeam = mapSideComboBox.getValue().equals("Radiant") ? Team.RADIANT : Team.DIRE;
                boolean isPlayerTurn = currentTeam == playerTeam;
                
                // Update the player turn property to ensure UI is consistent
                updatePlayerTurnProperty(playerTeam);
                
                // Determine if it's pick or ban phase
                boolean isPick = currentPhase.toString().contains("PICK");
                
                // Update action button immediately
                updateActionButton(isPlayerTurn, isPick);
            }
        });
        
        // Initial update
        selectedHeroProperty.set(selectedHero);
    }
    
    /**
     * Sets up the TableViews for player hero stats and match history
     */
    private void setupTableViews() {
        // Configure Hero Stats table columns
        setupHeroStatsTable();
        
        // Configure Recent Matches table columns
        setupRecentMatchesTable();
        
        // Set initial placeholders
        heroStatsTable.setPlaceholder(new Label("No hero statistics available"));
        recentMatchesTable.setPlaceholder(new Label("No match history available"));
        
        // Set up hero stats filter
        setupHeroStatsFilter();
    }
    
    /**
     * Sets up the filtering functionality for the hero stats table
     */
    private void setupHeroStatsFilter() {
        // The field should be injected by JavaFX at this point
        
        // Setup filter combo box
        heroStatsFilterComboBox.getItems().addAll(
            "All Heroes", "Most Played", "Highest Win Rate", "Best KDA", 
            "Strength Heroes", "Agility Heroes", "Intelligence Heroes"
        );
        heroStatsFilterComboBox.getSelectionModel().selectFirst();
        
        // Create a filtered list wrapper around the hero stats items
        FilteredList<PlayerHeroStat> filteredData = new FilteredList<>(heroStatsTable.getItems(), p -> true);
        
        // Set up text search
        heroSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(heroStat -> {
                // If filter text is empty, display all heroes
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                
                // Compare hero name with filter text
                String lowerCaseFilter = newValue.toLowerCase();
                Hero hero = heroStat.getHero();
                
                if (hero.getLocalizedName().toLowerCase().contains(lowerCaseFilter)) {
                    return true; // Filter by hero name
                }
                
                // Check if hero has roles that match the filter
                if (hero.getRoles() != null) {
                    for (String role : hero.getRoles()) {
                        if (role.toLowerCase().contains(lowerCaseFilter)) {
                            return true;
                        }
                    }
                }
                
                return false; // Does not match
            });
            
            // Apply combo box filter too
            applyHeroStatsComboFilter(filteredData, heroStatsFilterComboBox.getValue());
        });
        
        // Set up combo box filter
        heroStatsFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            applyHeroStatsComboFilter(filteredData, newValue);
        });
        
        // Wrap the filtered list in a sorted list
        SortedList<PlayerHeroStat> sortedData = new SortedList<>(filteredData);
        
        // Bind the sorted list comparator to the TableView comparator
        sortedData.comparatorProperty().bind(heroStatsTable.comparatorProperty());
        
        // Set the sorted and filtered data to the table
        heroStatsTable.setItems(sortedData);
    }
    
    /**
     * Applies the selected filter from the hero stats combo box
     *
     * @param filteredData The filtered list to apply the filter to
     * @param filter The filter value from the combo box
     */
    private void applyHeroStatsComboFilter(FilteredList<PlayerHeroStat> filteredData, String filter) {
        if (filter == null) {
            return;
        }
        
        // Get current text filter
        String searchText = "";
        if (heroSearchField != null) {
            searchText = heroSearchField.getText().toLowerCase();
        }
        
        // Create final variables for the lambda
        final String finalSearchText = searchText;
        
        // Apply appropriate filter based on selected filter
        filteredData.setPredicate(heroStat -> {
            // Apply text filter first
            boolean passesTextFilter = true;
            if (!finalSearchText.isEmpty()) {
                Hero hero = heroStat.getHero();
                passesTextFilter = hero.getLocalizedName().toLowerCase().contains(finalSearchText);
                
                // Check roles too if text doesn't match name
                if (!passesTextFilter && hero.getRoles() != null) {
                    for (String role : hero.getRoles()) {
                        if (role.toLowerCase().contains(finalSearchText)) {
                            passesTextFilter = true;
                            break;
                        }
                    }
                }
            }
            
            // If doesn't pass text filter, reject immediately
            if (!passesTextFilter) {
                return false;
            }
            
            // Now apply combo box filter
            if (filter.equals("All Heroes")) {
                return true;
            } else if (filter.equals("Most Played")) {
                // Consider heroes with more than 20 matches as "most played"
                return heroStat.getMatches() >= 20;
            } else if (filter.equals("Highest Win Rate")) {
                // Only show heroes with at least 10 matches and win rate over 50%
                return heroStat.getMatches() >= 10 && heroStat.getWinRate() >= 0.5;
            } else if (filter.equals("Best KDA")) {
                // Only show heroes with KDA over 3.0
                return heroStat.getKdaRatio() >= 3.0;
            } else {
                // Filter by attribute
                Hero hero = heroStat.getHero();
                if (hero.getPrimaryAttribute() == null) {
                    return false;
                }
                
                switch (filter) {
                    case "Strength Heroes":
                        return "str".equals(hero.getPrimaryAttribute());
                    case "Agility Heroes":
                        return "agi".equals(hero.getPrimaryAttribute());
                    case "Intelligence Heroes":
                        return "int".equals(hero.getPrimaryAttribute());
                    default:
                        return true;
                }
            }
        });
    }
    
    /**
     * Configures columns and cell factories for the hero statistics table
     */
    @SuppressWarnings("unchecked")
    private void setupHeroStatsTable() {
        // Configure columns (getting the existing columns from FXML)
        TableColumn<PlayerHeroStat, Hero> heroColumn = (TableColumn<PlayerHeroStat, Hero>) heroStatsTable.getColumns().get(0);
        TableColumn<PlayerHeroStat, Number> matchesColumn = (TableColumn<PlayerHeroStat, Number>) heroStatsTable.getColumns().get(1);
        TableColumn<PlayerHeroStat, Number> winRateColumn = (TableColumn<PlayerHeroStat, Number>) heroStatsTable.getColumns().get(2);
        TableColumn<PlayerHeroStat, Number> kdaColumn = (TableColumn<PlayerHeroStat, Number>) heroStatsTable.getColumns().get(3);
        TableColumn<PlayerHeroStat, LocalDateTime> lastPlayedColumn = (TableColumn<PlayerHeroStat, LocalDateTime>) heroStatsTable.getColumns().get(4);
        
        // Hero column with hero image and name
        heroColumn.setCellValueFactory(cellData -> cellData.getValue().heroProperty());
        heroColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Hero hero, boolean empty) {
                super.updateItem(hero, empty);
                
                if (empty || hero == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Create a custom cell with hero icon and name
                    HBox container = new HBox(5);
                    container.setAlignment(Pos.CENTER_LEFT);
                    
                    // Try to load hero icon (32x32 size)
                    ImageView heroIcon = new ImageView();
                    heroIcon.setFitWidth(32);
                    heroIcon.setFitHeight(32);
                    heroIcon.setPreserveRatio(true);
                    
                    try {
                        // If we have a local image resource for this hero, use it
                        String imagePath = "/images/heroes/" + hero.getId() + ".png";
                        heroIcon.setImage(new Image(getClass().getResourceAsStream(imagePath)));
                    } catch (Exception e) {
                        // Fallback to placeholder based on primary attribute
                        String placeholderPath = "/images/placeholder.png";
                        if (hero.getPrimaryAttribute() != null) {
                            switch (hero.getPrimaryAttribute()) {
                                case "str": placeholderPath = "/images/placeholder_str.png"; break;
                                case "agi": placeholderPath = "/images/placeholder_agi.png"; break;
                                case "int": placeholderPath = "/images/placeholder_int.png"; break;
                            }
                        }
                        try {
                            heroIcon.setImage(new Image(getClass().getResourceAsStream(placeholderPath)));
                        } catch (Exception ex) {
                            // If all else fails, just don't show an image
                            logger.warn("Could not load hero icon or placeholder for hero ID: {}", hero.getId());
                        }
                    }
                    
                    Label nameLabel = new Label(hero.getLocalizedName());
                    nameLabel.setStyle("-fx-font-weight: normal;");
                    
                    container.getChildren().addAll(heroIcon, nameLabel);
                    setGraphic(container);
                    setText(null);
                }
            }
        });
        
        // Matches column
        matchesColumn.setCellValueFactory(cellData -> cellData.getValue().matchesProperty());
        matchesColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(String.valueOf(value.intValue()));
                }
            }
        });
        
        // Win Rate column
        winRateColumn.setCellValueFactory(cellData -> cellData.getValue().winRateProperty());
        winRateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    // Format win rate as percentage
                    setText(String.format("%.1f%%", value.doubleValue() * 100));
                    
                    // Optional: Color-code the win rate
                    double winRate = value.doubleValue();
                    if (winRate > 0.55) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else if (winRate < 0.45) {
                        setStyle("-fx-text-fill: red;");
                    } else {
                        setStyle("-fx-text-fill: black;");
                    }
                }
            }
        });
        
        // KDA column
        kdaColumn.setCellValueFactory(cellData -> cellData.getValue().kdaRatioProperty());
        kdaColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    // Format KDA with 1 decimal place
                    setText(String.format("%.1f", value.doubleValue()));
                    
                    // Optional: Color-code the KDA
                    double kda = value.doubleValue();
                    if (kda > 4.0) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else if (kda < 2.0) {
                        setStyle("-fx-text-fill: red;");
                    } else {
                        setStyle("-fx-text-fill: black;");
                    }
                }
            }
        });
        
        // Last Played column
        lastPlayedColumn.setCellValueFactory(cellData -> cellData.getValue().lastPlayedProperty());
        lastPlayedColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    // Format date in a human-readable way
                    // For recent dates, show "Today", "Yesterday", otherwise show date
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime today = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 0, 0);
                    LocalDateTime yesterday = today.minusDays(1);
                    
                    if (date.isAfter(today)) {
                        setText("Today");
                    } else if (date.isAfter(yesterday)) {
                        setText("Yesterday");
                    } else {
                        // Format date as "MMM d" (e.g., "Jan 15")
                        setText(date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")));
                    }
                }
            }
        });
    }
    
    /**
     * Configures columns and cell factories for the recent matches table
     */
    @SuppressWarnings("unchecked")
    private void setupRecentMatchesTable() {
        // Configure columns (getting the existing columns from FXML)
        TableColumn<PlayerMatch, LocalDateTime> dateColumn = (TableColumn<PlayerMatch, LocalDateTime>) recentMatchesTable.getColumns().get(0);
        TableColumn<PlayerMatch, Hero> heroColumn = (TableColumn<PlayerMatch, Hero>) recentMatchesTable.getColumns().get(1);
        TableColumn<PlayerMatch, Boolean> resultColumn = (TableColumn<PlayerMatch, Boolean>) recentMatchesTable.getColumns().get(2);
        TableColumn<PlayerMatch, Number> durationColumn = (TableColumn<PlayerMatch, Number>) recentMatchesTable.getColumns().get(3);
        TableColumn<PlayerMatch, String> kdaColumn = (TableColumn<PlayerMatch, String>) recentMatchesTable.getColumns().get(4);
        TableColumn<PlayerMatch, String> modeColumn = (TableColumn<PlayerMatch, String>) recentMatchesTable.getColumns().get(5);
        
        // Date column
        dateColumn.setCellValueFactory(cellData -> cellData.getValue().dateProperty());
        dateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    // Format date in a human-readable way
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime today = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 0, 0);
                    LocalDateTime yesterday = today.minusDays(1);
                    
                    if (date.isAfter(today)) {
                        // Today with time
                        setText("Today " + date.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
                    } else if (date.isAfter(yesterday)) {
                        // Yesterday with time
                        setText("Yesterday " + date.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
                    } else {
                        // Full date for older entries
                        setText(date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm")));
                    }
                }
            }
        });
        
        // Hero column
        heroColumn.setCellValueFactory(cellData -> cellData.getValue().heroProperty());
        heroColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Hero hero, boolean empty) {
                super.updateItem(hero, empty);
                
                if (empty || hero == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Just show hero name for the match table to keep it compact
                    setText(hero.getLocalizedName());
                }
            }
        });
        
        // Result column (Win/Loss)
        resultColumn.setCellValueFactory(cellData -> cellData.getValue().wonProperty());
        resultColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean won, boolean empty) {
                super.updateItem(won, empty);
                if (empty || won == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(won ? "Win" : "Loss");
                    setStyle(won ? "-fx-text-fill: green; -fx-font-weight: bold;" : "-fx-text-fill: red;");
                }
            }
        });
        
        // Duration column
        durationColumn.setCellValueFactory(cellData -> cellData.getValue().durationProperty());
        durationColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number duration, boolean empty) {
                super.updateItem(duration, empty);
                if (empty || duration == null) {
                    setText(null);
                } else {
                    // Format as mm:ss
                    int minutes = duration.intValue() / 60;
                    int seconds = duration.intValue() % 60;
                    setText(String.format("%d:%02d", minutes, seconds));
                }
            }
        });
        
        // KDA column
        kdaColumn.setCellValueFactory(cellData -> {
            // Create a custom binding that formats the KDA
            PlayerMatch match = cellData.getValue();
            return new SimpleStringProperty(match.getKdaFormatted());
        });
        
        // Mode column
        modeColumn.setCellValueFactory(cellData -> cellData.getValue().gameModeProperty());
        
        // Add double-click handler to show match details
        recentMatchesTable.setRowFactory(tv -> {
            TableRow<PlayerMatch> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    // Show match details dialog
                    showMatchDetails(row.getItem());
                }
            });
            return row;
        });
    }
    
    /**
     * Shows detailed information about a specific match
     * @param match The match to display details for
     */
    private void showMatchDetails(PlayerMatch match) {
        if (match == null) {
            return;
        }
        
        // Create a custom dialog for match details
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Match Details");
        dialog.setHeaderText("Match #" + match.getMatchId());
        
        // Create a detailed content layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 20));
        
        // Date and time
        grid.add(new Label("Date:"), 0, 0);
        grid.add(new Label(match.getDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))), 1, 0);
        
        // Hero
        grid.add(new Label("Hero:"), 0, 1);
        grid.add(new Label(match.getHero().getLocalizedName()), 1, 1);
        
        // Result
        grid.add(new Label("Result:"), 0, 2);
        Label resultLabel = new Label(match.getResultFormatted());
        resultLabel.setStyle(match.isWon() ? "-fx-text-fill: green; -fx-font-weight: bold;" : "-fx-text-fill: red;");
        grid.add(resultLabel, 1, 2);
        
        // Duration
        grid.add(new Label("Duration:"), 0, 3);
        grid.add(new Label(match.getDurationFormatted()), 1, 3);
        
        // Game mode
        grid.add(new Label("Game Mode:"), 0, 4);
        grid.add(new Label(match.getGameMode()), 1, 4);
        
        // KDA statistics
        grid.add(new Label("Kills:"), 0, 5);
        grid.add(new Label(String.valueOf(match.getKills())), 1, 5);
        
        grid.add(new Label("Deaths:"), 0, 6);
        grid.add(new Label(String.valueOf(match.getDeaths())), 1, 6);
        
        grid.add(new Label("Assists:"), 0, 7);
        grid.add(new Label(String.valueOf(match.getAssists())), 1, 7);
        
        grid.add(new Label("KDA Ratio:"), 0, 8);
        double kdaRatio = match.getKdaRatio();
        Label kdaLabel = new Label(String.format("%.1f", kdaRatio));
        
        // Color-code KDA
        if (kdaRatio > 4.0) {
            kdaLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else if (kdaRatio < 2.0) {
            kdaLabel.setStyle("-fx-text-fill: red;");
        }
        grid.add(kdaLabel, 1, 8);
        
        // Add a separator
        Separator separator = new Separator();
        separator.setPrefWidth(Double.MAX_VALUE);
        grid.add(separator, 0, 9, 2, 1);
        
        // Future enhancement message
        Label enhancementMsg = new Label("More detailed statistics will be available in a future update.");
        enhancementMsg.setStyle("-fx-font-style: italic; -fx-text-fill: #555;");
        grid.add(enhancementMsg, 0, 10, 2, 1);
        
        // Add the grid to the dialog
        dialog.getDialogPane().setContent(grid);
        
        // Add close button
        ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(closeButton);
        
        // Show the dialog
        dialog.showAndWait();
    }
    
    private void applyCssClasses() {
        radiantStrengthBar.getStyleClass().add("radiant");
        direStrengthBar.getStyleClass().add("dire");
        
        radiantPicksGrid.getParent().getStyleClass().add("radiant-team");
        direPicksGrid.getParent().getStyleClass().add("dire-team");
    }
    
    private void loadHeroes() {
        // Show loading status
        statusLabel.setText("Loading heroes...");
        logger.info("Starting hero loading");
        
        executorService.submit(() -> {
            try {
                // Initialize the draft engine first
                logger.info("Initializing draft engine");
                draftEngine.initDraft(DraftMode.CAPTAINS_MODE, false);
                logger.info("Draft engine initialized");
                
                // Now get the heroes
                List<Hero> heroes = draftEngine.getAvailableHeroes();
                logger.info("Loaded {} heroes from draft engine", heroes.size());
                
                if (heroes.isEmpty()) {
                    logger.warn("No heroes were loaded from draft engine");
                    throw new RuntimeException("No heroes were loaded from the draft engine");
                }
                
                Platform.runLater(() -> {
                    try {
                        // Clear existing heroes
                        allHeroes.clear();
                        filteredHeroes.clear();
                        
                        // Add new heroes
                        allHeroes.addAll(heroes);
                        logger.debug("Added {} heroes to allHeroes list", allHeroes.size());
                        
                        // Apply initial filter
                    filterHeroes();
                        logger.debug("Filtered heroes list contains {} heroes", filteredHeroes.size());
                        
                        // Reset draft state since we just initialized it
                        draftEngine.resetDraft();
                        draftInProgress.set(false);
                        
                        // Update status
                        statusLabel.setText("Ready - Loaded " + heroes.size() + " heroes");
                    } catch (Exception e) {
                        logger.error("Error updating UI with heroes", e);
                        statusLabel.setText("Error updating hero display");
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to load heroes", e);
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to load heroes: " + e.getMessage());
                    statusLabel.setText("Error loading heroes");
                });
            }
        });
    }
    
    private void filterHeroes() {
        String searchText = heroSearchField.getText().toLowerCase();
        String filter = heroFilterComboBox.getValue();
        
        List<Hero> filtered = allHeroes.stream()
                .filter(hero -> hero.getLocalizedName().toLowerCase().contains(searchText))
                .filter(hero -> filterByAttribute(hero, filter))
                .collect(Collectors.toList());
        
        filteredHeroes.setAll(filtered);
    }
    
    private boolean filterByAttribute(Hero hero, String filter) {
        if (filter == null || filter.equals("All Heroes")) {
            return true;
        }
        
        switch (filter) {
            case "Strength":
                return "str".equals(hero.getPrimaryAttribute());
            case "Agility":
                return "agi".equals(hero.getPrimaryAttribute());
            case "Intelligence":
                return "int".equals(hero.getPrimaryAttribute());
            default:
                return hero.getRoles() != null && hero.getRoles().contains(filter);
        }
    }
    
    @FXML
    private void onStartEndDraft() {
        if (draftInProgress.get()) {
            // End the draft
            endDraft();
        } else {
            // Start the draft
            startDraft();
        }
    }
    
    private void startDraft() {
        DraftMode mode = draftModeComboBox.getValue();
        boolean timedMode = timedModeCheckBox.isSelected();
        boolean isFirstPick = pickOrderComboBox.getValue().equals("First Pick");
        boolean isRadiant = mapSideComboBox.getValue().equals("Radiant");
        
        try {
            // Initialize the draft with proper settings
            draftEngine.initDraft(mode, timedMode);
            draftInProgress.set(true);
            summaryShown = false; // Reset the summary shown flag
            draftTimeline.clear();
            
            // Determine player and AI teams based on side selection
            Team playerTeam = isRadiant ? Team.RADIANT : Team.DIRE;
            Team aiTeam = playerTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
            
            // Setup a direct player turn setter function since the listener approach has limitations
            // We'll update this after each draft action
            
            // Get the DRAFT_SEQUENCE field from CaptainsModeDraftEngine using reflection
            // to modify first pick team based on user selection
            try {
                java.lang.reflect.Field sequenceField = draftEngine.getClass().getDeclaredField("DRAFT_SEQUENCE");
                sequenceField.setAccessible(true);
                
                // Get the current sequence
                @SuppressWarnings("unchecked")
                List<Object> sequence = (List<Object>)sequenceField.get(draftEngine);
                
                // We need to modify the draft sequence based on the selected options
                // For both team selection and pick order:
                //
                // 1. First Pick + Radiant: Default sequence, no swap needed
                // 2. First Pick + Dire: Need to swap teams so Dire gets first pick
                // 3. Second Pick + Radiant: Need to swap teams so Dire gets first pick
                // 4. Second Pick + Dire: Need to swap teams so Radiant gets first pick
                //
                boolean needToSwapTeams = !isFirstPick || (isFirstPick && !isRadiant);
                
                if (needToSwapTeams) {
                    // Iterate through the sequence and swap the teams
                    for (Object turnInfo : sequence) {
                        // Get the team field from TurnInfo class
                        java.lang.reflect.Field teamField = turnInfo.getClass().getDeclaredField("team");
                        teamField.setAccessible(true);
                        Team currentTeam = (Team)teamField.get(turnInfo);
                        
                        // Swap RADIANT with DIRE and vice versa
                        if (currentTeam == Team.RADIANT) {
                            teamField.set(turnInfo, Team.DIRE);
                        } else {
                            teamField.set(turnInfo, Team.RADIANT);
                        }
                    }
                    
                    // Log what we did based on the specific configuration
                    if (isFirstPick && !isRadiant) {
                        logger.info("Modified draft sequence to give Dire team first pick");
                    } else if (!isFirstPick && isRadiant) {
                        logger.info("Modified draft sequence to give Dire first pick (Radiant second pick)");
                    } else if (!isFirstPick && !isRadiant) {
                        logger.info("Modified draft sequence to give Radiant first pick (Dire second pick)");
                        
                        // Add detailed logging for Second Pick + Dire scenario
                        logger.info("SEQUENCE DEBUG for Second Pick + Dire scenario:");
                        for (int i = 0; i < Math.min(10, sequence.size()); i++) {
                            Object turnInfo = sequence.get(i);
                            try {
                                java.lang.reflect.Field teamField = turnInfo.getClass().getDeclaredField("team");
                                teamField.setAccessible(true);
                                Team team = (Team)teamField.get(turnInfo);
                                
                                java.lang.reflect.Field phaseField = turnInfo.getClass().getDeclaredField("phase");
                                phaseField.setAccessible(true);
                                DraftPhase phase = (DraftPhase)phaseField.get(turnInfo);
                                
                                java.lang.reflect.Field isBanField = turnInfo.getClass().getDeclaredField("isBan");
                                isBanField.setAccessible(true);
                                boolean isBan = (boolean)isBanField.get(turnInfo);
                                
                                logger.info("Turn {}: Team {}, Phase {}, IsBan: {}", 
                                          i, 
                                          team == Team.RADIANT ? "RADIANT" : "DIRE", 
                                          phase, 
                                          isBan);
                            } catch (Exception e) {
                                logger.error("Error inspecting sequence", e);
                            }
                        }
                    }
                    
                    // Reset turn index and refresh current team and phase to ensure 
                    // draft engine's internal state matches our modified sequence
                    if (draftEngine instanceof CaptainsModeDraftEngine) {
                        try {
                            java.lang.reflect.Field turnIndexField = draftEngine.getClass().getDeclaredField("currentTurnIndex");
                            turnIndexField.setAccessible(true);
                            turnIndexField.set(draftEngine, 0);
                            
                            // Update engine's state based on the first turn in our modified sequence
                            Object firstTurn = sequence.get(0);
                            java.lang.reflect.Field teamField = firstTurn.getClass().getDeclaredField("team");
                            teamField.setAccessible(true);
                            Team firstTeam = (Team)teamField.get(firstTurn);
                            
                            java.lang.reflect.Field phaseField = firstTurn.getClass().getDeclaredField("phase");
                            phaseField.setAccessible(true);
                            DraftPhase firstPhase = (DraftPhase)phaseField.get(firstTurn);
                            
                            // Use reflection to update the current team and phase in the draft state
                            java.lang.reflect.Field draftStateField = draftEngine.getClass().getDeclaredField("draftState");
                            draftStateField.setAccessible(true);
                            Object draftState = draftStateField.get(draftEngine);
                            
                            java.lang.reflect.Method setCurrentTeamMethod = draftState.getClass().getMethod("setCurrentTeam", Team.class);
                            setCurrentTeamMethod.invoke(draftState, firstTeam);
                            
                            java.lang.reflect.Method setCurrentPhaseMethod = draftState.getClass().getMethod("setCurrentPhase", DraftPhase.class);
                            setCurrentPhaseMethod.invoke(draftState, firstPhase);
                            
                            logger.info("Reset draft engine state to match modified sequence. First team: {}", firstTeam);
                            
                            // Important - Determine if player should go first based on settings
                            // When player has second pick, ensure we start with AI's turn
                            // Logic for handling Second Pick option with either Radiant or Dire team
                            if (!isFirstPick) {
                                // Force a second update to ensure currentTeam is correct
                                logger.info("Second pick selected - Player team should go second");
                                Team currentPlayerTeam = isRadiant ? Team.RADIANT : Team.DIRE;
                                Team currentAiTeam = currentPlayerTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
                                
                                // First team after swapping should be the AI team for second pick
                                boolean isPlayerFirst = (firstTeam == currentPlayerTeam);
                                
                                if (isPlayerFirst) {
                                    logger.info("Correcting turn order: player with second pick ({}) should not go first, AI ({}) should", 
                                              currentPlayerTeam == Team.RADIANT ? "Radiant" : "Dire",
                                              currentAiTeam == Team.RADIANT ? "Radiant" : "Dire");
                                    
                                    // Advance turn index to next team's turn
                                    // Skip the first team's turn (which would be the player's)
                                    turnIndexField.set(draftEngine, 1);
                                    
                                    // Get second turn info
                                    Object secondTurn = sequence.get(1); 
                                    teamField = secondTurn.getClass().getDeclaredField("team");
                                    teamField.setAccessible(true);
                                    Team secondTeam = (Team)teamField.get(secondTurn);
                                    
                                    phaseField = secondTurn.getClass().getDeclaredField("phase");
                                    phaseField.setAccessible(true);
                                    DraftPhase secondPhase = (DraftPhase)phaseField.get(secondTurn);
                                    
                                    // Update with second turn info
                                    setCurrentTeamMethod.invoke(draftState, secondTeam);
                                    setCurrentPhaseMethod.invoke(draftState, secondPhase);
                                    
                                    logger.info("Adjusted turn order for second pick: now on team {}'s turn", secondTeam);
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Failed to reset draft engine state after modifying sequence", e);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error modifying draft sequence", e);
                // If modification fails, we'll use the default sequence (Radiant first)
            }
            
            // Initialize for current draft state and refresh UI with updated state
            updateTeamDisplays();
            
            // Set the initial player turn property based on the engine's current team
            // This ensures the UI correctly reflects whether it's the player or AI's turn
            Team currentTeam = draftEngine.getCurrentTeam();
            boolean isPlayerTurn = (currentTeam == playerTeam);
            
            // Update the player turn property and refresh status for consistency
            updatePlayerTurnProperty(playerTeam);
            
            // Extra logging to verify team state
            logger.info("Team states after initialization - Current team in engine: {}, Player team: {}, AI team: {}, isPlayerTurn: {}",
                       currentTeam == Team.RADIANT ? "Radiant" : "Dire",
                       playerTeam == Team.RADIANT ? "Radiant" : "Dire",
                       aiTeam == Team.RADIANT ? "Radiant" : "Dire",
                       isPlayerTurn);
            
            // Make sure action button reflects the correct state
            boolean isPick = draftEngine.getCurrentPhase().toString().contains("PICK");
            updateActionButton(isPlayerTurn, isPick);
            
            // Update draft status display
            updateDraftStatus();
            
            // Update button text
            startEndDraftButton.setText("End Draft");
            
            // If timed mode is enabled, start the timer
            if (timedMode) {
                startTimer();
            }
            
            // Add descriptive entry to draft timeline
            String playerRole = isFirstPick ? "First Pick" : "Second Pick";
            String playerSide = isRadiant ? "Radiant" : "Dire";
            String aiSide = isRadiant ? "Dire" : "Radiant";
            String firstPickTeam = isFirstPick ? playerSide : aiSide;
            
            draftTimeline.add("Draft started - " + mode + 
                             (timedMode ? " (Timed)" : "") +
                             " - Player: " + playerSide + " | AI: " + aiSide +
                             " - " + firstPickTeam + " has first pick");
            
            // Now that the draft sequence is properly set up, get the first team's turn from the engine
            Team firstTeamTurn = draftEngine.getCurrentTeam();
            
            // Determine if player gets to make the first move directly from the engine state
            boolean isPlayerFirst = (firstTeamTurn == playerTeam);
            
            logger.info("Starting draft - Player team: {}, AI team: {}, First team to move: {}, Player starts: {}",
                       playerTeam == Team.RADIANT ? "Radiant" : "Dire", 
                       aiTeam == Team.RADIANT ? "Radiant" : "Dire",
                       firstTeamTurn == Team.RADIANT ? "Radiant" : "Dire",
                       isPlayerFirst);
            
            if (!isPlayerFirst) {
                // If the AI is first, let it make a move
                makeAiMove();
            }
        } catch (Exception e) {
            logger.error("Failed to start draft", e);
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to start draft: " + e.getMessage());
        }
    }
    
    private void endDraft() {
        // End the current draft and show summary
        draftEngine.resetDraft();
        draftInProgress.set(false);
        resetUI();
        statusLabel.setText("Draft ended");
        
        // Update button text back to "Start Draft"
        startEndDraftButton.setText("Start Draft");
        
        // Show draft summary if it hasn't been shown yet
        if (!summaryShown) {
            summaryShown = true;
            showDraftSummary();
        }
        
        // Shut down the timer if it's running
        timerService.shutdownNow();
    }
    
    @FXML
    private void onResetDraft() {
        draftEngine.resetDraft();
        draftInProgress.set(false);
        summaryShown = false; // Reset the summary shown flag
        resetUI();
        statusLabel.setText("Draft reset");
        
        // Reset button text
        startEndDraftButton.setText("Start Draft");
        
        timerService.shutdownNow();
    }
    
    /**
     * Unified action method for picking or banning heroes
     */
    @FXML
    private void onHeroAction() {
        logger.info("==== onHeroAction called - Draft in progress: {}, selectedHero: {} ====", 
                   draftInProgress.get(), 
                   selectedHero != null ? selectedHero.getLocalizedName() : "null");
                   
        if (!draftInProgress.get() || selectedHero == null) {
            logger.warn("Cannot perform hero action - draft not in progress or no hero selected");
            return;
        }
        
        try {
            Hero actionHero = selectedHero; // Store reference before clearing
            DraftPhase currentPhase = draftEngine.getCurrentPhase();
            Team currentTeam = draftEngine.getCurrentTeam();
            Team playerTeam = mapSideComboBox.getValue().equals("Radiant") ? Team.RADIANT : Team.DIRE;
            
            logger.info("ACTION INFO - Current phase: {}, Current team: {}, Player team: {}", 
                       currentPhase, currentTeam, playerTeam);
                       
            // Verify it's actually the player's turn
            if (currentTeam != playerTeam) {
                logger.warn("Cannot perform action - not player's turn. Current team: {}, Player team: {}", 
                          currentTeam, playerTeam);
                return;
            }
            
            boolean isPick = currentPhase.toString().contains("PICK");
            logger.info("Action type: {}", isPick ? "PICK" : "BAN");
            boolean success;
            
            if (isPick) {
                // Execute a pick action
                success = draftEngine.selectHero(actionHero);
            } else {
                // Execute a ban action
                success = draftEngine.banHero(actionHero);
            }
            
            if (success) {
                // Get team name before advancing the turn (since currentTeam will be the next team after the action)
                // This ensures we attribute the action to the team that actually made it
                String teamName = currentTeam == Team.RADIANT ? "Radiant" : "Dire";
                String action = isPick ? "picked" : "banned";
                String phaseStr = "";
                
                // Get a more user-friendly phase name
                switch (currentPhase) {
                    case CM_BAN_1: phaseStr = "Ban Phase 1"; break;
                    case CM_PICK_1: phaseStr = "Pick Phase 1"; break;
                    case CM_BAN_2: phaseStr = "Ban Phase 2"; break;
                    case CM_PICK_2: phaseStr = "Pick Phase 2"; break;
                    case CM_BAN_3: phaseStr = "Ban Phase 3"; break;
                    case CM_PICK_3: phaseStr = "Pick Phase 3"; break;
                    default: phaseStr = currentPhase.toString();
                }
                
                int turnIndex = draftEngine.getCurrentTurnIndex();
                draftTimeline.add(String.format("Turn %d - %s: %s %s %s", 
                    turnIndex, phaseStr, teamName, action, actionHero.getLocalizedName()));
                
                // Clear selection
                heroGridView.clearSelection();
                selectedHero = null;
                
                updateTeamDisplays();
                updateDraftStatus();
                
                // Update the player turn property to ensure UI consistency
                updatePlayerTurnProperty(playerTeam);
                
                // Check if it's AI's turn or if draft is complete
                // playerTeam was already defined above so we'll reuse it
                Team aiTeam = playerTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
                Team nextTeam = draftEngine.getCurrentTeam();
                
                // Update status for player's turn or show draft complete
                if (draftEngine.isDraftComplete()) {
                    statusLabel.setText("Draft completed!");
                } else if (nextTeam == playerTeam) {
                    statusLabel.setText("Your turn to " + 
                        (draftEngine.getCurrentPhase().toString().contains("PICK") ? "pick" : "ban"));
                } else if (nextTeam == aiTeam) {
                    // If it's AI's turn, add a small delay before making the AI move
                    // This gives the player time to see what they just did
                    statusLabel.setText("AI is thinking...");
                    executorService.submit(() -> {
                        try {
                            Thread.sleep(1200); // 1.2 second delay 
                            Platform.runLater(() -> makeAiMove());
                        } catch (InterruptedException e) {
                            logger.error("AI move delay interrupted", e);
                        }
                    });
                }
                
                // Update analysis after both teams have made selections
                if (draftEngine.getTeamPicks(Team.RADIANT).size() > 0 && 
                        draftEngine.getTeamPicks(Team.DIRE).size() > 0) {
                    updateDraftAnalysis();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to perform hero action", e);
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to perform action: " + e.getMessage());
        }
    }
    
    private void makeAiMove() {
        // Check if draft is complete and return immediately if it is
        if (draftEngine.isDraftComplete()) {
            logger.info("AI move called but draft is already complete. Ignoring.");
            return;
        }
        
        // Update status
        statusLabel.setText("AI is thinking...");
        
        // Determine teams based on user selections
        boolean isFirstPick = pickOrderComboBox.getValue().equals("First Pick");
        boolean isRadiant = mapSideComboBox.getValue().equals("Radiant");
        Team playerTeam = isRadiant ? Team.RADIANT : Team.DIRE;
        Team aiTeam = playerTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
        
        // Determine which team should have first pick based on user selections
        Team firstPickTeam;
        if (isFirstPick) {
            firstPickTeam = playerTeam;
        } else {
            firstPickTeam = aiTeam;
        }
        
        // Re-fetch the current state from the draft engine to ensure we have accurate information
        int turnIndex = draftEngine.getCurrentTurnIndex();
        DraftPhase phase = draftEngine.getCurrentPhase();
        Team currentTeam = draftEngine.getCurrentTeam();
        
        // Add detailed debugging information
        logger.info("===== AI MOVE CALLED =====");
        logger.info("Current turn: {} of 24", turnIndex + 1);
        logger.info("Current phase: {}", phase);
        logger.info("Current team: {} ({})", currentTeam, currentTeam == Team.RADIANT ? "Radiant" : "Dire");
        logger.info("Player team: {} ({})", playerTeam, playerTeam == Team.RADIANT ? "Radiant" : "Dire");
        logger.info("AI team: {} ({})", aiTeam, aiTeam == Team.RADIANT ? "Radiant" : "Dire");
        logger.info("First pick team: {}", firstPickTeam == Team.RADIANT ? "Radiant" : "Dire");
        logger.info("Is it AI's turn? {}", currentTeam == aiTeam);
        logger.info("Pick order: {}, Map side: {}", 
                   pickOrderComboBox.getValue(), 
                   mapSideComboBox.getValue());
        logger.info("=========================");
        
        // Verify that it's actually the AI's turn
        if (currentTeam != aiTeam) {
            logger.error("AI move was called but it's not AI's turn! Current team: {}, AI team: {}", 
                      currentTeam == Team.RADIANT ? "Radiant" : "Dire", 
                      aiTeam == Team.RADIANT ? "Radiant" : "Dire");
            
            // Update status to indicate it's the player's turn
            boolean isPick = phase.toString().contains("PICK");
            statusLabel.setText("Your turn to " + (isPick ? "pick" : "ban"));
            
            // Update the player turn property explicitly to ensure UI is consistent
            updatePlayerTurnProperty(playerTeam);
            
            return;
        }
        
        executorService.submit(() -> {
            try {
                Thread.sleep(1000); // Add a small delay to simulate thinking
                
                // Get current game state
                boolean isPick = draftEngine.getCurrentPhase().toString().contains("PICK");
                // We already determined the AI and player teams before starting the thread
                
                Hero selectedHero;
                
                if (isPick) {
                    // AI is picking - consider current game state
                    selectedHero = aiEngine.suggestPick(
                            draftEngine.getTeamPicks(Team.RADIANT),
                            draftEngine.getTeamPicks(Team.DIRE),
                            draftEngine.getBannedHeroes()
                    );
                    
                    if (selectedHero != null) {
                        final Hero hero = selectedHero;
                        Platform.runLater(() -> {
                            // Save the team before executing the action
                            String aiTeamName = aiTeam == Team.RADIANT ? "Radiant" : "Dire";
                            
                            // Execute action and update UI
                            draftEngine.selectHero(hero);
                            updateTeamDisplays();
                            updateDraftStatus();
                            
                            // Update the player turn property to ensure UI consistency
                            updatePlayerTurnProperty(playerTeam);
                            DraftPhase currentPhase = draftEngine.getCurrentPhase();
                            String phaseStr = "";
                            
                            // Get a more user-friendly phase name
                            switch (currentPhase) {
                                case CM_BAN_1: phaseStr = "Ban Phase 1"; break;
                                case CM_PICK_1: phaseStr = "Pick Phase 1"; break;
                                case CM_BAN_2: phaseStr = "Ban Phase 2"; break;
                                case CM_PICK_2: phaseStr = "Pick Phase 2"; break;
                                case CM_BAN_3: phaseStr = "Ban Phase 3"; break;
                                case CM_PICK_3: phaseStr = "Pick Phase 3"; break;
                                default: phaseStr = currentPhase.toString();
                            }
                            
                            int currentTurnIndex = draftEngine.getCurrentTurnIndex();
                            draftTimeline.add(String.format("Turn %d - %s: %s picked %s", 
                                currentTurnIndex, phaseStr, aiTeamName, hero.getLocalizedName()));
                            
                            // Update analysis after picks
                            if (!draftEngine.getTeamPicks(Team.RADIANT).isEmpty() && 
                                !draftEngine.getTeamPicks(Team.DIRE).isEmpty()) {
                                updateDraftAnalysis();
                            }
                            
                            // Check if draft is complete or it's the player's turn next
                            if (draftEngine.isDraftComplete()) {
                                statusLabel.setText("Draft completed!");
                                // Show the draft summary if it hasn't been shown yet
                                if (!summaryShown) {
                                    summaryShown = true;
                                    showDraftSummary();
                                }
                            } else {
                                Team nextTeam = draftEngine.getCurrentTeam();
                                boolean isPlayerTurn = nextTeam == playerTeam;
                                
                                if (isPlayerTurn) {
                                    statusLabel.setText("Your turn to " + 
                                        (draftEngine.getCurrentPhase().toString().contains("PICK") ? "pick" : "ban"));
                                } else {
                                    // If AI still has another turn, make another AI move
                                    makeAiMove();
                                }
                            }
                        });
                    }
                } else {
                    // AI is banning - consider current game state
                    selectedHero = aiEngine.suggestBan(
                            draftEngine.getTeamPicks(Team.RADIANT),
                            draftEngine.getTeamPicks(Team.DIRE),
                            draftEngine.getBannedHeroes()
                    );
                    
                    if (selectedHero != null) {
                        final Hero hero = selectedHero;
                        Platform.runLater(() -> {
                            // Save the team before executing the action
                            String aiTeamName = aiTeam == Team.RADIANT ? "Radiant" : "Dire";
                            
                            // Execute action and update UI
                            draftEngine.banHero(hero);
                            updateTeamDisplays();
                            updateDraftStatus();
                            
                            // Update the player turn property to ensure UI consistency
                            updatePlayerTurnProperty(playerTeam);
                            DraftPhase currentPhase = draftEngine.getCurrentPhase();
                            String phaseStr = "";
                            
                            // Get a more user-friendly phase name
                            switch (currentPhase) {
                                case CM_BAN_1: phaseStr = "Ban Phase 1"; break;
                                case CM_PICK_1: phaseStr = "Pick Phase 1"; break;
                                case CM_BAN_2: phaseStr = "Ban Phase 2"; break;
                                case CM_PICK_2: phaseStr = "Pick Phase 2"; break;
                                case CM_BAN_3: phaseStr = "Ban Phase 3"; break;
                                case CM_PICK_3: phaseStr = "Pick Phase 3"; break;
                                default: phaseStr = currentPhase.toString();
                            }
                            
                            int currentTurnIndex = draftEngine.getCurrentTurnIndex();
                            draftTimeline.add(String.format("Turn %d - %s: %s banned %s", 
                                currentTurnIndex, phaseStr, aiTeamName, hero.getLocalizedName()));
                            
                            // Check if draft is complete or it's the player's turn next
                            if (draftEngine.isDraftComplete()) {
                                statusLabel.setText("Draft completed!");
                                // Show the draft summary if it hasn't been shown yet
                                if (!summaryShown) {
                                    summaryShown = true;
                                    showDraftSummary();
                                }
                            } else {
                                Team nextTeam = draftEngine.getCurrentTeam();
                                boolean isPlayerTurn = nextTeam == playerTeam;
                                
                                if (isPlayerTurn) {
                                    statusLabel.setText("Your turn to " + 
                                        (draftEngine.getCurrentPhase().toString().contains("PICK") ? "pick" : "ban"));
                                } else {
                                    // If AI still has another turn, make another AI move
                                    makeAiMove();
                                }
                            }
                        });
                    }
                }
            } catch (Exception e) {
                logger.error("AI move failed", e);
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Error", "AI move failed: " + e.getMessage());
                    statusLabel.setText("AI move failed");
                });
            }
        });
    }
    
    private void updateTeamDisplays() {
        // Update Radiant picks - limit to 2 heroes per row
        radiantPicksGrid.getChildren().clear();
        List<Hero> radiantPicks = draftEngine.getTeamPicks(Team.RADIANT);
        for (int i = 0; i < radiantPicks.size(); i++) {
            Hero hero = radiantPicks.get(i);
            HeroCell cell = new HeroCell(hero);
            // Use 2 heroes per row instead of 3
            radiantPicksGrid.add(cell, i % 2, i / 2);
        }
        
        // Update Dire picks - limit to 2 heroes per row
        direPicksGrid.getChildren().clear();
        List<Hero> direPicks = draftEngine.getTeamPicks(Team.DIRE);
        for (int i = 0; i < direPicks.size(); i++) {
            Hero hero = direPicks.get(i);
            HeroCell cell = new HeroCell(hero);
            // Use 2 heroes per row instead of 3
            direPicksGrid.add(cell, i % 2, i / 2);
        }
        
        // Update bans
        radiantBansPane.getChildren().clear();
        direBansPane.getChildren().clear();
        
        List<Hero> bannedHeroes = draftEngine.getBannedHeroes();
        
        // Attribution of bans depends on the current sequence, which may have been modified
        // if the player chose Dire with first pick or Second Pick in general.
        // We need to adjust our ban attribution based on these changes.
        
        // Get the draft sequence info that we need
        boolean isFirstPick = pickOrderComboBox.getValue().equals("First Pick");
        boolean isRadiant = mapSideComboBox.getValue().equals("Radiant");
        boolean needToSwapTeams = !isFirstPick || (isFirstPick && !isRadiant);
        
        logger.info("==== BAN ATTRIBUTION DEBUG ====");
        logger.info("Total bans: {}", bannedHeroes.size());
        logger.info("First pick: {}, isRadiant: {}, needToSwapTeams: {}", isFirstPick, isRadiant, needToSwapTeams);
        
        // The default ban patterns for all Captain's Mode ban phases
        // Based on the first pick team (Radiant by default) making these bans
        // Ban Phase 1: ABBABBA - First pick team makes bans at positions 0, 3, 6
        // Ban Phase 2: AAB - First pick team makes bans at positions 0, 1
        // Ban Phase 3: ABBA - First pick team makes bans at positions 0, 3
        
        // Default ban patterns based on first ban order in Captain's Mode:
        // Ban Phase 1 (idx 0-6): ABBABBA - First pick team makes bans at positions 0, 3, 6
        // Ban Phase 2 (idx 7-9): AAB - First pick team makes bans at positions 7, 8
        // Ban Phase 3 (idx 10-13): ABBA - First pick team makes bans at positions 10, 13
        
        // These are the indices where first pick team makes bans in standard ordering
        List<Integer> firstPickTeamBans = Arrays.asList(0, 3, 6, 7, 8, 10, 13);

        // Special fix for Second Pick + Dire scenario, which needs special handling
        // The standard Captain's Mode draft sequence is:
        // - Ban Phase 1: ABBABBA (7 bans total)
        //   * A = First Pick team bans (1st, 4th, 7th bans)
        //   * B = Second Pick team bans (2nd, 3rd, 5th, 6th bans)
        // - Ban Phase 2: AAB (3 bans)
        //   * A = First Pick team bans (1st, 2nd bans) 
        //   * B = Second Pick team bans (3rd ban)
        // - Ban Phase 3: ABBA (4 bans)
        //   * A = First Pick team bans (1st, 4th bans)
        //   * B = Second Pick team bans (2nd, 3rd bans)
        //
        // For Second Pick + Dire scenario specifically:
        // - AI is Radiant = First Pick team (A)
        // - Player is Dire = Second Pick team (B)
        // - Correct ban sequence should be ABBABBA where Dire (player) gets 2nd, 3rd, 5th, and 6th bans
        boolean isSecondPickDire = !isFirstPick && !isRadiant;
        
        // Get both team references for clearer logging
        Team playerTeam = isRadiant ? Team.RADIANT : Team.DIRE;
        Team aiTeam = playerTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
        
        // Special handling for second pick Dire since turn sequence is unique
        // For this case, indices get swapped but attribution gets inverted
        
        for (int i = 0; i < bannedHeroes.size(); i++) {
            Hero hero = bannedHeroes.get(i);
            
            // Step 1: Determine which team would make this ban in standard ordering
            boolean isBanByFirstPickTeam = firstPickTeamBans.contains(i);
            
            // Step 2: Apply sequence swap if needed
            Team banningTeam;
            
            // Special handling for second pick + Dire case
            if (isSecondPickDire) {
                // In Second Pick + Dire scenario:
                // - AI is Radiant/First Pick team (A)
                // - Player is Dire/Second Pick team (B)
                // Correct ban order should be ABBABBA where:
                // - A = First Pick team (Radiant/AI)
                // - B = Second Pick team (Dire/Player)
                
                // Standard sequence indices for first ban phase (0-6):
                // Index: 0 1 2 3 4 5 6
                // Team:  A B B A B B A
                
                // Initialize with default
                banningTeam = Team.RADIANT; // Default to avoid compiler error
                
                if (i < bannedHeroes.size()) {
                    if (i == 0) {
                        // First ban by First Pick team (AI/Radiant)
                        banningTeam = Team.RADIANT;
                        logger.info("Ban attribution - Ban #{}: assigned to AI/Radiant (first ban)", i);
                    } else if (i == 1 || i == 2) {
                        // Second and third bans by Second Pick team (Player/Dire)
                        banningTeam = Team.DIRE;
                        logger.info("Ban attribution - Ban #{}: assigned to Player/Dire (B bans in ABBABBA)", i);
                    } else if (i == 3) {
                        // Fourth ban by First Pick team (AI/Radiant)
                        banningTeam = Team.RADIANT;
                        logger.info("Ban attribution - Ban #{}: assigned to AI/Radiant (A in ABBABBA)", i);
                    } else if (i == 4 || i == 5) {
                        // Fifth and sixth bans by Second Pick team (Player/Dire)
                        banningTeam = Team.DIRE;
                        logger.info("Ban attribution - Ban #{}: assigned to Player/Dire (B bans in ABBABBA)", i);
                    } else if (i == 6) {
                        // Seventh ban by First Pick team (AI/Radiant)
                        banningTeam = Team.RADIANT;
                        logger.info("Ban attribution - Ban #{}: assigned to AI/Radiant (final A in ABBABBA)", i);
                    } else if (i >= 7 && i <= 9) {
                        // Ban Phase 2 (AAB): First two bans by First Pick team, third by Second Pick
                        banningTeam = (i < 9) ? Team.RADIANT : Team.DIRE;
                        logger.info("Ban attribution - Ban #{}: assigned to {} (Ban Phase 2: AAB)", 
                                  i, banningTeam == Team.RADIANT ? "AI/Radiant" : "Player/Dire");
                    } else if (i >= 10) {
                        // Ban Phase 3 (ABBA): First and last bans by First Pick team, middle two by Second Pick
                        if (i == 10 || i == 13) {
                            banningTeam = Team.RADIANT;
                        } else {
                            banningTeam = Team.DIRE;
                        }
                        logger.info("Ban attribution - Ban #{}: assigned to {} (Ban Phase 3: ABBA)", 
                                  i, banningTeam == Team.RADIANT ? "AI/Radiant" : "Player/Dire");
                    } else {
                        // Fallback for any other case
                        banningTeam = (i % 2 == 0) ? Team.RADIANT : Team.DIRE;
                        logger.warn("Using default alternating attribution for unexpected index {}", i);
                    }
                } else {
                    // Default fallback (shouldn't happen)
                    logger.warn("Using fallback ban attribution for index {}", i);
                    banningTeam = (i % 2 == 0) ? Team.RADIANT : Team.DIRE;
                }
            
            } else if (needToSwapTeams) {
                // In swapped sequences, the first pick team is Dire or Radiant gets second pick
                banningTeam = isBanByFirstPickTeam ? Team.DIRE : Team.RADIANT;
                
                // Add detailed info for debugging all other swap cases
                logger.info("Ban attribution [swap] - Ban #{}: using swapped teams logic, assigned to {}", 
                         i, banningTeam == Team.RADIANT ? "Radiant" : "Dire");
            } else {
                // In standard sequence (Radiant + First Pick), first pick team is Radiant
                banningTeam = isBanByFirstPickTeam ? Team.RADIANT : Team.DIRE;
                
                logger.info("Ban attribution [default] - Ban #{}: using standard team logic, assigned to {}", 
                         i, banningTeam == Team.RADIANT ? "Radiant" : "Dire");
            }
            
            // In UI, we want to show the ban on the team's side that made the ban
            boolean showOnRadiantSide = (banningTeam == Team.RADIANT);
            
            // Log the attribution decision
            String attributedTeam = showOnRadiantSide ? "RADIANT UI" : "DIRE UI";
            String whichTeam = (banningTeam == playerTeam) ? "Player" : "AI";
            boolean isPlayerBan = (banningTeam == playerTeam);
            
            // Detailed logging for attribution
            if (isSecondPickDire) {
                logger.info("Ban #{}: {} - Made by {} team ({}), showing on {} {}", 
                          i, 
                          hero.getLocalizedName(),
                          banningTeam == Team.RADIANT ? "RADIANT" : "DIRE",
                          whichTeam,
                          attributedTeam,
                          isPlayerBan ? " (Player Ban)" : " (AI Ban)");
            } else {
                logger.info("Ban #{}: {} - Made by {} team, showing on {} {}", 
                          i, 
                          hero.getLocalizedName(),
                          banningTeam == Team.RADIANT ? "RADIANT" : "DIRE",
                          attributedTeam,
                          isPlayerBan ? " (Player Ban)" : " (AI Ban)");
            }
            
            // Add the hero cell to the appropriate pane
            HeroCell cell = new HeroCell(hero);
            if (showOnRadiantSide) {
                radiantBansPane.getChildren().add(cell);
            } else {
                direBansPane.getChildren().add(cell);
            }
        }
    }
    
    private void updateDraftStatus() {
        if (draftEngine.isDraftComplete()) {
            statusLabel.setText("Draft completed");
            draftInProgress.set(false);
            timerService.shutdownNow();
            highlightCurrentTeam(null);  // Clear team highlights
            updateActionButton(false, true);  // Disable button, default to pick action
            
            // Reset the Start/End Draft button
            startEndDraftButton.setText("Start Draft");
            
            // Only show the summary once
            if (!summaryShown) {
                summaryShown = true;
            showDraftSummary();
            }
        } else if (draftEngine.isDraftInProgress()) {
            Team currentTeam = draftEngine.getCurrentTeam();
            DraftPhase currentPhase = draftEngine.getCurrentPhase();
            
            // Get more descriptive phase text
            String phaseText;
            switch (currentPhase) {
                case CM_BAN_1:
                    phaseText = "BAN PHASE 1";
                    break;
                case CM_PICK_1:
                    phaseText = "PICK PHASE 1";
                    break;
                case CM_BAN_2:
                    phaseText = "BAN PHASE 2";
                    break;
                case CM_PICK_2:
                    phaseText = "PICK PHASE 2";
                    break;
                case CM_BAN_3:
                    phaseText = "BAN PHASE 3";
                    break;
                case CM_PICK_3:
                    phaseText = "PICK PHASE 3";
                    break;
                default:
                    phaseText = currentPhase.toString().replace("CM_", "").replace("AP_", "");
                    break;
            }
            
            String teamText = currentTeam == Team.RADIANT ? "Radiant" : "Dire";
            int turnIndex = draftEngine.getCurrentTurnIndex();
            int totalTurns = 24; // Total turns in CM draft
            
            // More descriptive status that includes what type of action is required
            boolean isPick = currentPhase.toString().contains("PICK");
            String actionText = isPick ? "pick" : "ban";
            
            // Check if it's player's turn to make it more clear
            Team currentPlayerTeam = mapSideComboBox.getValue().equals("Radiant") ? Team.RADIANT : Team.DIRE;
            boolean currentPlayerTurn = currentTeam == currentPlayerTeam;
            
            // Update the player turn property to ensure UI consistency
            updatePlayerTurnProperty(currentPlayerTeam);
            
            if (currentPlayerTurn) {
                statusLabel.setText(String.format("%s: YOUR TURN to %s (Turn %d of %d)", 
                                   phaseText, actionText, turnIndex + 1, totalTurns));
            } else {
                statusLabel.setText(String.format("%s: %s turn to %s (Turn %d of %d)", 
                                   phaseText, teamText, actionText, turnIndex + 1, totalTurns));
            }
            
            // Highlighting is now handled by updatePlayerTurnProperty
            
            // Determine if it's the player's turn
            Team playerTeam = mapSideComboBox.getValue().equals("Radiant") ? Team.RADIANT : Team.DIRE;
            Team aiTeam = playerTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
            boolean isPlayerTurn = currentTeam == playerTeam;
            
            // Extra debug logging
            logger.info("Current turn: Team {} ({}), Player is {}, AI is {}, Phase: {}, Turn: {} of 24", 
                      currentTeam, 
                      currentTeam == Team.RADIANT ? "Radiant" : "Dire",
                      playerTeam == Team.RADIANT ? "Radiant" : "Dire",
                      aiTeam == Team.RADIANT ? "Radiant" : "Dire",
                      currentPhase,
                      turnIndex + 1);
            
            // Update button style and text - only enable if it's player's turn
            updateActionButton(isPlayerTurn, isPick);
            
            // Update recommended picks/bans
            updateRecommendations();
        }
    }
    
    /**
     * Updates the action button style and text based on the current phase
     * 
     * @param active true if action is available, false otherwise
     * @param isPick true if it's a pick phase, false for ban phase
     */
    private void updateActionButton(boolean active, boolean isPick) {
        // Reset styles
        actionButton.getStyleClass().removeAll("active-action", "disabled-action", "pick-action", "ban-action");
        
        // Set button text based on phase
        if (isPick) {
            actionButton.setText("Pick Selected Hero");
            actionButton.getStyleClass().add("pick-action");
        } else {
            actionButton.setText("Ban Selected Hero");
            actionButton.getStyleClass().add("ban-action");
        }
        
        // Debug logging to check why button isn't enabled
        logger.info("Updating action button - Active: {}, isPick: {}, selectedHero: {}", 
                  active, 
                  isPick, 
                  selectedHero != null ? selectedHero.getLocalizedName() : "null");
        
        // Apply enabled/disabled style
        if (active && selectedHero != null) {
            actionButton.getStyleClass().add("active-action");
            actionButton.setDisable(false);
            // Force a UI refresh
            actionButton.setVisible(true);
            logger.info("Action button enabled and refreshed");
        } else {
            actionButton.getStyleClass().add("disabled-action");
            actionButton.setDisable(true);
            // Force a UI refresh
            actionButton.setVisible(true);
            logger.info("Action button disabled - active: {}, selectedHero: {}", 
                      active, selectedHero != null);
        }
    }
    
    /**
     * Highlights the UI area for the current team's turn.
     * 
     * @param team The team to highlight, or null to clear highlights
     */
    private void highlightCurrentTeam(Team team) {
        // Reset both team pane styles
        radiantPicksGrid.getParent().getStyleClass().remove("active-team");
        direPicksGrid.getParent().getStyleClass().remove("active-team");
        
        if (team == Team.RADIANT) {
            radiantPicksGrid.getParent().getStyleClass().add("active-team");
            logger.debug("Highlighting RADIANT team as active");
        } else if (team == Team.DIRE) {
            direPicksGrid.getParent().getStyleClass().add("active-team");
            logger.debug("Highlighting DIRE team as active");
        } else {
            logger.debug("No team highlighted (null team)");
        }
    }
    
    private void updateRecommendations() {
        executorService.submit(() -> {
            try {
                // Get team picks
                List<Hero> radiantTeam = draftEngine.getTeamPicks(Team.RADIANT);
                List<Hero> direTeam = draftEngine.getTeamPicks(Team.DIRE);
                List<Hero> banned = draftEngine.getBannedHeroes();
                
                // Get hero suggestions
                List<Hero> pickHeroes = aiEngine.suggestPicks(radiantTeam, direTeam, banned, 5);
                List<Hero> banHeroes = aiEngine.suggestBans(radiantTeam, direTeam, banned, 5);
                
                // Check if we have player-specific recommendations to include
                boolean includePlayerData = false;
                long playerAccountId = 0;
                
                if (playerRecommendationService != null && 
                    userStatusContainerController != null && 
                    userStatusContainerController.getUserService().isLoggedIn()) {
                    
                    try {
                        // Get current user's account ID
                        String steamId = userStatusContainerController.getUserService()
                            .getCurrentUser().orElseThrow().getSteamId();
                        long steam64Id = Long.parseLong(steamId);
                        playerAccountId = (int)(steam64Id & 0xFFFFFFFFL);
                        includePlayerData = true;
                        
                        logger.info("Including player-specific recommendations for account ID: {}", 
                                   playerAccountId);
                        
                        // Get the player's comfort heroes and add them to the picks list if they're not already there
                        if (includePlayerData) {
                            List<PlayerHeroPerformance> comfortHeroes = 
                                playerRecommendationService.getComfortHeroes(playerAccountId, 3);
                            
                            for (PlayerHeroPerformance perf : comfortHeroes) {
                                Hero comfortHero = perf.getHero();
                                // Only add if not banned and not already in picks list
                                if (comfortHero != null && 
                                    !banned.contains(comfortHero) && 
                                    !pickHeroes.contains(comfortHero)) {
                                    
                                    // Add to beginning of list for higher priority
                                    pickHeroes.add(0, comfortHero);
                                    
                                    // Ensure we don't exceed the limit
                                    if (pickHeroes.size() > 5) {
                                        pickHeroes.remove(pickHeroes.size() - 1);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to include player recommendations", e);
                        includePlayerData = false;
                    }
                }
                
                // Convert to detailed recommendations
                List<HeroRecommendation> pickRecommendations = new ArrayList<>();
                List<HeroRecommendation> banRecommendations = new ArrayList<>();
                
                // Process pick recommendations
                for (Hero hero : pickHeroes) {
                    // Get synergy and counter metrics for the recommendation
                    double synergyScore = analysisEngine.calculateSynergy(hero, 
                            draftEngine.getCurrentTeam() == Team.RADIANT ? radiantTeam : direTeam);
                    double counterScore = analysisEngine.calculateCounter(hero, 
                            draftEngine.getCurrentTeam() == Team.RADIANT ? direTeam : radiantTeam);
                    double winRate = analysisEngine.getHeroWinRate(hero);
                    int pickCount = analysisEngine.getHeroPickCount(hero);
                    
                    // Generate synergy and counter reasons
                    List<String> synergyReasons = generateSynergyReasons(hero, 
                            draftEngine.getCurrentTeam() == Team.RADIANT ? radiantTeam : direTeam);
                    List<String> counterReasons = generateCounterReasons(hero, 
                            draftEngine.getCurrentTeam() == Team.RADIANT ? direTeam : radiantTeam);
                    
                    // Base score calculation
                    double score = 0.4 * synergyScore + 0.4 * counterScore + 0.2 * (winRate > 0 ? winRate : 0.5);
                    
                    // Add player-specific performance data if available
                    if (includePlayerData && heroPerformanceMap.containsKey(hero.getId())) {
                        PlayerHeroPerformance heroPerf = heroPerformanceMap.get(hero.getId());
                        
                        if (heroPerf != null) {
                            // Boost score for comfort heroes
                            if (heroPerf.isComfortPick()) {
                                score *= 1.2; // 20% boost for comfort heroes
                                synergyReasons.add(0, "Your comfort hero ★");
                            }
                            
                            // Adjust by personal win rate if significant matches played
                            if (heroPerf.getMatches() >= 5) {
                                double personalBoost = (heroPerf.getWinRate() - 0.5) * 0.3; // +/- 15% max
                                score += personalBoost;
                                
                                // Add reason for personal performance
                                if (heroPerf.getWinRate() > 0.55) {
                                    synergyReasons.add("Strong personal win rate: " + 
                                                    heroPerf.getWinRateFormatted());
                                }
                            }
                        }
                    }
                    
                    // Create recommendation with detailed metrics and reasons
                    HeroRecommendation recommendation = 
                        new HeroRecommendation(
                            hero, score, winRate, synergyScore, counterScore, pickCount, 
                            synergyReasons, counterReasons
                        );
                    
                    pickRecommendations.add(recommendation);
                }
                
                // Sort the pick recommendations by the final score
                pickRecommendations.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                
                // Process ban recommendations - similar approach as picks but with a focus on counters
                for (Hero hero : banHeroes) {
                    double synergyScore = analysisEngine.calculateSynergy(hero, 
                            draftEngine.getCurrentTeam() == Team.RADIANT ? direTeam : radiantTeam);
                    double counterScore = analysisEngine.calculateCounter(hero, 
                            draftEngine.getCurrentTeam() == Team.RADIANT ? radiantTeam : direTeam);
                    double winRate = analysisEngine.getHeroWinRate(hero);
                    int pickCount = analysisEngine.getHeroPickCount(hero);
                    
                    List<String> synergyReasons = generateSynergyReasons(hero, 
                            draftEngine.getCurrentTeam() == Team.RADIANT ? direTeam : radiantTeam);
                    List<String> counterReasons = generateCounterReasons(hero, 
                            draftEngine.getCurrentTeam() == Team.RADIANT ? radiantTeam : direTeam);
                    
                    double score = 0.3 * synergyScore + 0.5 * counterScore + 0.2 * (winRate > 0 ? winRate : 0.5);
                    
                    // For ban recommendations, consider heroes that counter your comfort picks
                    if (includePlayerData) {
                        // Try to get the player's comfort heroes
                        try {
                            List<PlayerHeroPerformance> comfortHeroes = 
                                playerRecommendationService.getComfortHeroes(playerAccountId, 5);
                            
                            // Check if this hero is a strong counter to any of the player's comfort heroes
                            for (PlayerHeroPerformance comfortHero : comfortHeroes) {
                                double counterStrength = analysisEngine.calculateHeroCounter(
                                    hero, comfortHero.getHero());
                                
                                // If this is a strong counter to a comfort hero, prioritize the ban
                                if (counterStrength > 0.65) {
                                    score *= 1.15; // 15% boost to ban priority
                                    counterReasons.add(0, "Counters your comfort hero " + 
                                                      comfortHero.getHero().getLocalizedName());
                                    break; // Only add this reason once
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("Error calculating player-specific ban priorities", e);
                        }
                    }
                    
                    banRecommendations.add(new HeroRecommendation(
                        hero, score, winRate, synergyScore, counterScore, pickCount,
                        synergyReasons, counterReasons
                    ));
                }
                
                // Sort the ban recommendations by the final score
                banRecommendations.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                
                Platform.runLater(() -> {
                    recommendedPicks.setAll(pickRecommendations);
                    recommendedBans.setAll(banRecommendations);
                });
            } catch (Exception e) {
                logger.error("Failed to update recommendations", e);
            }
        });
    }
    
    /**
     * Generate synergy reasons for a hero with the given team
     */
    private List<String> generateSynergyReasons(Hero hero, List<Hero> team) {
        List<String> reasons = new ArrayList<>();
        
        if (team.isEmpty()) {
            return reasons;
        }
        
        // Analyze entire team composition
        int strengthCount = 0;
        int agilityCount = 0;
        int intelligenceCount = 0;
        int carriesCount = 0;
        int supportsCount = 0;
        int disablersCount = 0;
        int nukersCount = 0;
        int initiatorCount = 0;
        
        for (Hero teammate : team) {
            if (teammate.getPrimaryAttribute() != null) {
                switch (teammate.getPrimaryAttribute()) {
                    case "str": strengthCount++; break;
                    case "agi": agilityCount++; break;
                    case "int": intelligenceCount++; break;
                }
            }
            
            if (teammate.getRoles() != null) {
                List<String> roles = teammate.getRoles();
                if (roles.contains("Carry")) carriesCount++;
                if (roles.contains("Support")) supportsCount++;
                if (roles.contains("Disabler")) disablersCount++;
                if (roles.contains("Nuker")) nukersCount++;
                if (roles.contains("Initiator")) initiatorCount++;
            }
        }
        
        // Check if this hero fills a team gap
        if (hero.getRoles() != null) {
            List<String> roles = hero.getRoles();
            if (roles.contains("Carry") && carriesCount == 0) {
                reasons.add("Provides needed carry potential for your lineup");
            }
            if (roles.contains("Support") && supportsCount == 0) {
                reasons.add("Adds critical support capabilities to your draft");
            }
            if (roles.contains("Disabler") && disablersCount < 2) {
                reasons.add("Adds essential crowd control to your team");
            }
            if (roles.contains("Initiator") && initiatorCount == 0) {
                reasons.add("Provides crucial initiation your team needs");
            }
        }
        
        if (reasons.size() >= 2) {
            return reasons;
        }
        
        // Find heroes with highest synergy
        Map<Hero, Double> synergies = new HashMap<>();
        for (Hero teammate : team) {
            double synergyScore = analysisEngine.calculateHeroSynergy(hero, teammate);
            if (synergyScore > 0.6) {
                synergies.put(teammate, synergyScore);
            }
        }
        
        // Convert top synergies to specific tactical reasons
        List<Map.Entry<Hero, Double>> topSynergies = synergies.entrySet().stream()
            .sorted(Map.Entry.<Hero, Double>comparingByValue().reversed())
            .limit(2)
            .collect(Collectors.toList());
        
        // Specific synergy pairs based on hero names
        Map<String, Map<String, String>> synergyPairs = new HashMap<>();
        synergyPairs.put("Crystal Maiden", Map.of(
            "Juggernaut", "Mana aura enables Jugg's Blade Fury spam",
            "Phantom Assassin", "Slow enables easier Phantom Strike hits",
            "Ursa", "Frostbite + Overpower combo secures early kills"
        ));
        
        synergyPairs.put("Tiny", Map.of(
            "Io", "Classic Tiny+Io relocation ganks",
            "Centaur Warrunner", "Toss into Hoof Stomp combo"
        ));
        
        synergyPairs.put("Vengeful Spirit", Map.of(
            "Luna", "Aura stacking amplifies damage",
            "Drow Ranger", "Swap saves + aura synergy"
        ));
        
        for (Map.Entry<Hero, Double> entry : topSynergies) {
            Hero teammate = entry.getKey();
            double score = entry.getValue();
            
            // Check for specific synergy pairs first
            String specificSynergy = null;
            if (synergyPairs.containsKey(hero.getLocalizedName())) {
                Map<String, String> pairings = synergyPairs.get(hero.getLocalizedName());
                specificSynergy = pairings.get(teammate.getLocalizedName());
            }
            
            if (specificSynergy == null && synergyPairs.containsKey(teammate.getLocalizedName())) {
                Map<String, String> pairings = synergyPairs.get(teammate.getLocalizedName());
                specificSynergy = pairings.get(hero.getLocalizedName());
            }
            
            if (specificSynergy != null) {
                reasons.add(specificSynergy);
            } else {
                // Use a general template with hero name
                String reasonTemplate;
                if (score > 0.75) {
                    reasonTemplate = "Exceptional synergy with %s";
                } else if (score > 0.65) {
                    reasonTemplate = "Strong synergy with %s";
                } else {
                    reasonTemplate = "Good pairing with %s";
                }
                
                reasons.add(String.format(reasonTemplate, teammate.getLocalizedName()));
            }
        }
        
        return reasons;
    }
    
    /**
     * Generate counter reasons for a hero against the given team
     */
    private List<String> generateCounterReasons(Hero hero, List<Hero> enemyTeam) {
        List<String> reasons = new ArrayList<>();
        
        if (enemyTeam.isEmpty()) {
            return reasons;
        }
        
        // First check for specific strategic counters
        if (enemyTeam.size() >= 3) {
            // Team characteristic counters
            boolean enemyIsPhysicalHeavy = enemyTeam.stream()
                .filter(e -> "agi".equals(e.getPrimaryAttribute()))
                .count() >= 2;
                
            boolean enemyIsMagicHeavy = enemyTeam.stream()
                .filter(e -> "int".equals(e.getPrimaryAttribute()))
                .count() >= 2;
            
            boolean enemyHasMobility = enemyTeam.stream().anyMatch(e -> 
                e.getRoles() != null && (e.getRoles().contains("Escape") || e.getRoles().contains("Initiator")));
                
            boolean enemyHasLowHP = enemyTeam.stream()
                .filter(e -> "int".equals(e.getPrimaryAttribute()) || "agi".equals(e.getPrimaryAttribute()))
                .count() >= 3;
                
            // Check if this hero is good against these team traits
            String heroName = hero.getLocalizedName();
            if (enemyIsPhysicalHeavy) {
                List<String> armorHeroes = Arrays.asList("Tiny", "Dragon Knight", "Ogre Magi", "Timbersaw", "Sven");
                if (armorHeroes.contains(heroName)) {
                    reasons.add("High armor is strong against physical-heavy enemy lineup");
                }
            }
            
            if (enemyIsMagicHeavy) {
                List<String> magicResistHeroes = Arrays.asList("Anti-Mage", "Huskar", "Lifestealer", "Pudge");
                if (magicResistHeroes.contains(heroName)) {
                    reasons.add("Magic resistance counters enemy spell damage");
                }
            }
            
            if (enemyHasMobility) {
                List<String> lockdownHeroes = Arrays.asList("Lion", "Shadow Shaman", "Bane", "Axe", "Legion Commander");
                if (lockdownHeroes.contains(heroName)) {
                    reasons.add("Lockdown abilities control enemy mobility heroes");
                }
            }
            
            if (enemyHasLowHP) {
                List<String> burstHeroes = Arrays.asList("Lina", "Lion", "Tinker", "Zeus", "Morphling");
                if (burstHeroes.contains(heroName)) {
                    reasons.add("Burst damage exploits enemy's low HP pool");
                }
            }
        }
        
        if (reasons.size() >= 2) {
            return reasons;
        }
        
        // Specific counter matchups for well-known counters
        Map<String, Map<String, String>> counterPairs = new HashMap<>();
        counterPairs.put("Anti-Mage", Map.of(
            "Storm Spirit", "Mana Void punishes Storm's mana usage",
            "Medusa", "Mana Break depletes Medusa's shield",
            "Invoker", "Spell Shield negates Invoker's combo damage"
        ));
        
        counterPairs.put("Earthshaker", Map.of(
            "Broodmother", "Echo Slam destroys Broodmother's spiders",
            "Meepo", "Echo Slam instantly kills multiple Meepos",
            "Phantom Lancer", "Fissure and Echo counter PL's illusions"
        ));
        
        counterPairs.put("Silencer", Map.of(
            "Enigma", "Global Silence prevents Black Hole initiation",
            "Tide Hunter", "Global Silence prevents Ravage initiation"
        ));
        
        // Find specific hero counters first
        for (Hero enemy : enemyTeam) {
            if (counterPairs.containsKey(hero.getLocalizedName())) {
                Map<String, String> matchups = counterPairs.get(hero.getLocalizedName());
                String specificReason = matchups.get(enemy.getLocalizedName());
                if (specificReason != null) {
                    reasons.add(specificReason);
                }
            }
        }
        
        // If we already have 2 reasons, return them
        if (reasons.size() >= 2) {
            return reasons;
        }
        
        // Find heroes that this hero counters well
        Map<Hero, Double> counters = new HashMap<>();
        for (Hero enemy : enemyTeam) {
            double counterScore = analysisEngine.calculateHeroCounter(hero, enemy);
            if (counterScore > 0.6) {
                counters.put(enemy, counterScore);
            }
        }
        
        // Convert top counters to reasons
        List<Map.Entry<Hero, Double>> topCounters = counters.entrySet().stream()
            .sorted(Map.Entry.<Hero, Double>comparingByValue().reversed())
            .limit(2)
            .collect(Collectors.toList());
        
        for (Map.Entry<Hero, Double> entry : topCounters) {
            Hero enemy = entry.getKey();
            double score = entry.getValue();
            
            String reasonTemplate;
            if (score > 0.8) {
                reasonTemplate = "Hard counters %s";
            } else if (score > 0.7) {
                reasonTemplate = "Strong counter to %s";
            } else {
                reasonTemplate = "Effective against %s";
            }
            
            reasons.add(String.format(reasonTemplate, enemy.getLocalizedName()));
        }
        
        return reasons;
    }
    
    private void updateDraftAnalysis() {
        executorService.submit(() -> {
            try {
                double radiantStrength = analysisEngine.calculateTeamStrength(draftEngine.getTeamPicks(Team.RADIANT));
                double direStrength = analysisEngine.calculateTeamStrength(draftEngine.getTeamPicks(Team.DIRE));
                double radiantWinProbability = analysisEngine.predictWinProbability(
                        draftEngine.getTeamPicks(Team.RADIANT),
                        draftEngine.getTeamPicks(Team.DIRE)
                );
                String analysis = analysisEngine.analyzeDraft(
                        draftEngine.getTeamPicks(Team.RADIANT),
                        draftEngine.getTeamPicks(Team.DIRE)
                );
                
                Platform.runLater(() -> {
                    // Update team strength bars
                    radiantStrengthBar.setProgress(radiantStrength);
                    direStrengthBar.setProgress(direStrength);
                    analysisTextArea.setText(analysis);
                    
                    // Update win percentage visualization
                    updateWinProbabilityBar(radiantWinProbability);
                });
            } catch (Exception e) {
                logger.error("Failed to update draft analysis", e);
            }
        });
    }
    
    /**
     * Updates the win probability visualization bar
     * 
     * @param radiantWinProbability Value between 0 and 1 representing Radiant's win probability
     */
    private void updateWinProbabilityBar(double radiantWinProbability) {
        // Ensure probability is between 0 and 1
        radiantWinProbability = Math.max(0, Math.min(1, radiantWinProbability));
        
        // Calculate Dire's probability
        double direWinProbability = 1 - radiantWinProbability;
        
        // Format percentages for display
        String radiantPercentText = String.format("%.1f%%", radiantWinProbability * 100);
        String direPercentText = String.format("%.1f%%", direWinProbability * 100);
        
        // Update the label
        winPercentageLabel.setText(radiantPercentText + " - " + direPercentText);
        
        // Update both progress bars with their respective probabilities
        radiantWinBar.setProgress(radiantWinProbability);
        winProgressBackground.setProgress(direWinProbability);  // Update Dire's probability bar too
        
        // Set colors to ensure they always apply
        String radiantColor = "#92A525"; // Green
        String direColor = "#C23C2A"; // Red
        
        // Reapply styles
        radiantWinBar.setStyle("-fx-accent: " + radiantColor + ";");
        winProgressBackground.setStyle("-fx-accent: " + direColor + ";");
        
        // Add some visual emphasis if one team has a significant advantage
        if (radiantWinProbability > 0.6) {
            winPercentageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + radiantColor + ";");
        } else if (direWinProbability > 0.6) {
            winPercentageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + direColor + ";");
        } else {
            winPercentageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
        }
        
        logger.debug("Updated win probability bar: Radiant {}%, Dire {}%", 
                    radiantWinProbability * 100, direWinProbability * 100);
    }
    
    private void startTimer() {
        timerService.scheduleAtFixedRate(() -> {
            if (draftEngine.isDraftInProgress() && draftEngine.getRemainingTime() > 0) {
                int remainingTime = draftEngine.getRemainingTime();
                Platform.runLater(() -> {
                    int minutes = remainingTime / 60;
                    int seconds = remainingTime % 60;
                    timerLabel.setText(String.format("%02d:%02d", minutes, seconds));
                });
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    private void showDraftSummary() {
        String summary = analysisEngine.getDraftSummary(
                draftEngine.getTeamPicks(Team.RADIANT),
                draftEngine.getTeamPicks(Team.DIRE)
        );
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Draft Summary");
        alert.setHeaderText("Draft Completed");
        alert.setContentText(summary);
        alert.showAndWait();
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    @FXML
    private void onNewDraft() {
        summaryShown = false; // Reset the summary shown flag
        onResetDraft();
    }
    
    /**
     * Updates the UI to reflect the initial state.
     * This should be called during initialization and after resetting.
     */
    private void resetUI() {
        // Clear all team displays
        radiantPicksGrid.getChildren().clear();
        direPicksGrid.getChildren().clear();
        radiantBansPane.getChildren().clear();
        direBansPane.getChildren().clear();
        
        // Clear analysis and recommendations
        analysisTextArea.clear();
        recommendedPicks.clear();
        recommendedBans.clear();
        draftTimeline.clear();
        
        // Reset progress bars
        radiantStrengthBar.setProgress(0);
        direStrengthBar.setProgress(0);
        
        // Reset win probability bar
        updateWinProbabilityBar(0.5); // Reset to 50/50
        
        // Reset team highlights
        highlightCurrentTeam(null);
        
        // Disable action button
        updateActionButton(false, true);
        
        // Update status
        statusLabel.setText("Ready to start draft");
        timerLabel.setText("00:00");
    }
    
    @FXML
    private void onRefreshHeroData() {
        executorService.submit(() -> {
            Platform.runLater(() -> statusLabel.setText("Refreshing hero data..."));
            try {
                // This would be implemented in a repository class in a real app
                Thread.sleep(2000); // Simulate network delay
                Platform.runLater(() -> {
                    statusLabel.setText("Hero data refreshed");
                    loadHeroes();
                });
            } catch (Exception e) {
                logger.error("Failed to refresh hero data", e);
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Failed to refresh hero data"));
            }
        });
    }
    
    @FXML
    private void onUpdateMatchData() {
        executorService.submit(() -> {
            Platform.runLater(() -> statusLabel.setText("Updating match data..."));
            try {
                // This would be implemented in a repository class in a real app
                Thread.sleep(3000); // Simulate network delay
                Platform.runLater(() -> {
                    statusLabel.setText("Match data updated");
                });
            } catch (Exception e) {
                logger.error("Failed to update match data", e);
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Failed to update match data"));
            }
        });
    }
    
    @FXML
    private void onShowPreferences() {
        showAlert(Alert.AlertType.INFORMATION, "Preferences", "Preferences dialog would be shown here");
    }
    
    @FXML
    private void onShowAbout() {
        showAlert(Alert.AlertType.INFORMATION, "About", "Dota 2 Draft Assistant\nVersion 0.1");
    }
    
    @FXML
    private void onExit() {
        // Shut down the sync settings controller if available
        if (syncSettingsContainerController != null) {
            syncSettingsContainerController.shutdown();
        }
        
        executorService.shutdownNow();
        timerService.shutdownNow();
        Platform.exit();
    }
    
    // ----- New Tab Navigation and Player Profile Methods -----
    
    /**
     * Switches to the Player Profile tab.
     */
    public void switchToPlayerProfile() {
        mainTabPane.getSelectionModel().select(playerProfileTab);
        // Update the profile based on the current login state
        updatePlayerProfileForLoginState();
    }
    
    /**
     * Switches to the Draft tab.
     */
    public void switchToDraft() {
        mainTabPane.getSelectionModel().select(draftTab);
    }
    
    /**
     * Switches to the Live Assistant tab.
     */
    public void switchToLiveAssistant() {
        mainTabPane.getSelectionModel().select(liveAssistantTab);
    }
    
    /**
     * Load and display player profile data from the Steam account.
     */
    private void loadPlayerProfile() {
        // Check if user is logged in - use explicit method call to ensure we get fresh state
        boolean isLoggedIn = false;
        try {
            if (userStatusContainerController != null && userStatusContainerController.getUserService() != null) {
                isLoggedIn = userStatusContainerController.getUserService().isLoggedIn();
                logger.info("Checked login status: {}", isLoggedIn ? "logged in" : "not logged in");
            } else {
                logger.warn("Cannot check login status - UserStatusController or UserService is null");
            }
        } catch (Exception e) {
            logger.error("Error checking login status", e);
        }
                             
        if (!isLoggedIn) {
            // Show not logged in state
            playerNameLabel.setText("Please log in to view your profile");
            steamIdLabel.setText("Not logged in");
            accountLevelLabel.setText("--");
            playerAvatarLarge.setImage(null);
            
            // Show login prompt in the hero stats and recent matches tables
            heroStatsTable.setPlaceholder(new Label("Please log in to view your hero statistics"));
            recentMatchesTable.setPlaceholder(new Label("Please log in to view your match history"));
            
            return;
        }
        
        try {
            // Get user data with explicit error handling
            var userService = userStatusContainerController.getUserService();
            var currentUserOpt = userService.getCurrentUser();
            
            if (currentUserOpt.isEmpty()) {
                logger.warn("User is reported as logged in but getCurrentUser returned empty");
                playerNameLabel.setText("Error loading profile");
                steamIdLabel.setText("User data not available");
                return;
            }
            
            var user = currentUserOpt.get();
            // Update UI with user profile information
            playerNameLabel.setText(user.getUsername());
            steamIdLabel.setText(user.getSteamId());
            accountLevelLabel.setText("Loading...");
            
            // Log that we're loading the profile
            logger.info("Loading player profile for: {} ({})", user.getUsername(), user.getSteamId());
            
            // Load avatar if available
            if (user.getAvatarFullUrl() != null) {
                try {
                    Image avatarImage = new Image(user.getAvatarFullUrl());
                    playerAvatarLarge.setImage(avatarImage);
                    logger.debug("Loaded avatar from URL: {}", user.getAvatarFullUrl());
                } catch (Exception e) {
                    logger.warn("Failed to load player avatar: {}", e.getMessage());
                }
            }
            
            // Load player stats in background through the appropriate service
            loadPlayerStats(user.getSteamId());
        } catch (Exception e) {
            logger.error("Error loading user profile", e);
            playerNameLabel.setText("Error loading profile");
            steamIdLabel.setText("An error occurred");
        }
    }
    
    /**
     * Load player statistics from the database or API.
     * @param steamId The Steam ID of the player
     */
    private void loadPlayerStats(String steamId) {
        executorService.submit(() -> {
            Platform.runLater(() -> {
                statusLabel.setText("Loading player statistics...");
                // Show loading indicators
                heroStatsTable.setPlaceholder(new Label("Loading hero statistics..."));
                recentMatchesTable.setPlaceholder(new Label("Loading match history..."));
            });
            
            try {
                if (userStatusContainerController == null) {
                    logger.error("UserStatusController is null - cannot access Steam API services");
                    throw new RuntimeException("UserStatusController not available");
                }
                
                // Access the Steam API Service through the UserService
                SteamApiService steamApiService = getSteamApiService();
                
                if (steamApiService == null) {
                    logger.error("Cannot access SteamApiService");
                    throw new RuntimeException("Steam API Service not available");
                }
                
                // Get recent matches for this player - requesting more matches (50)
                logger.debug("Fetching recent matches for SteamID: {}", steamId);
                List<Long> recentMatchIds;
                try {
                    recentMatchIds = steamApiService.getRecentMatches(steamId, 50);
                    logger.info("Found {} recent matches for player {}", recentMatchIds.size(), steamId);
                } catch (Exception e) {
                    logger.error("Error fetching recent matches", e);
                    // Use mock match data in case of error
                    recentMatchIds = new ArrayList<>();
                }
                
                // Convert 64-bit Steam ID to 32-bit account ID for display
                try {
                    long steam64Id = Long.parseLong(steamId);
                    int accountId = (int) (steam64Id & 0xFFFFFFFFL);
                    
                    logger.debug("Converted 64-bit Steam ID {} to 32-bit account ID: {}", steamId, accountId);
                    
                    // Default account level calculation - would be replaced with actual API data
                    int accountLevel = calculateEstimatedLevel(accountId);
                    
                    // Update account level on UI
                    final int finalLevel = accountLevel;
                    Platform.runLater(() -> {
                        accountLevelLabel.setText("" + finalLevel);
                    });
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse Steam ID as number: {}", steamId);
                }
                
                // Process each match ID to get full details
                final List<Long> finalMatchIds = recentMatchIds;
                if (finalMatchIds.isEmpty()) {
                    logger.warn("No recent matches found for player {}", steamId);
                    // Get environment from PropertyLoader for better environment detection
                    String env = propertyLoader.getProperty("app.environment", "production");
                    // Generate mock matches for development
                    if ("development".equals(env) || "test".equals(env)) {
                        logger.info("Generating mock match data for development");
                        List<PlayerMatch> mockMatches = generateMockMatches(20);
                        Platform.runLater(() -> {
                            recentMatchesTable.setItems(FXCollections.observableArrayList(mockMatches));
                            populatePartialPlayerStatsFromMatches(mockMatches);
                            statusLabel.setText("Player statistics loaded (with mock match data)");
                        });
                    } else {
                        // If no matches found, populate with some mock data for demonstration
                        Platform.runLater(() -> {
                            populatePartialPlayerStats();
                            statusLabel.setText("Player statistics loaded (with mock match data)");
                            recentMatchesTable.setPlaceholder(new Label("No recent matches found"));
                        });
                    }
                } else {
                    // In a real implementation, we would fetch actual match data from the API
                    // Here, we'd typically query a match repository or API for details of each match
                    
                    // For demonstration purposes, we'll create generated data with real match IDs
                    // Here we would normally make API calls to get match details for each ID
                    // For example:
                    //   List<MatchDetails> matchDetails = matchRepository.getMatchDetails(finalMatchIds);
                    //   List<PlayerMatch> playerMatches = convertToPlayerMatches(matchDetails, steamId);
                    //
                    // Get the property from PropertyLoader for better environment detection
                    boolean isDevelopmentMode = "development".equals(propertyLoader.getProperty("app.environment", "production"));
                    
                    Platform.runLater(() -> {
                        // Generate player match data with the real match IDs
                        generatePlayerMatchesByIds(finalMatchIds);
                        statusLabel.setText("Player statistics loaded");
                        
                        // Update placeholders
                        heroStatsTable.setPlaceholder(new Label("No hero statistics available"));
                        recentMatchesTable.setPlaceholder(new Label("No match history available"));
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to load player statistics", e);
                Platform.runLater(() -> {
                    // Fall back to mock data if there's an error
                    populateMockPlayerStats();
                    statusLabel.setText("Failed to load player statistics, showing mock data");
                    
                    // Show error placeholders
                    heroStatsTable.setPlaceholder(new Label("Error loading hero statistics"));
                    recentMatchesTable.setPlaceholder(new Label("Error loading match history"));
                });
            }
        });
    }
    
    /**
     * Calculates an estimated player level based on account ID
     * In a real implementation, this would be fetched from the API
     */
    private int calculateEstimatedLevel(int accountId) {
        // Use account ID to seed a deterministic "random" level
        // This ensures the same account always gets the same level
        Random random = new Random(accountId);
        
        // Most players are between levels 30-150
        int baseLevel = 30 + random.nextInt(120);
        
        // Players with lower account IDs tend to be older accounts with higher levels
        // Lower IDs indicate older accounts, which would have more experience
        int modifierByAge = Math.max(0, 100 - (accountId % 1000) / 10);
        
        return baseLevel + modifierByAge;
    }
    
    /**
     * Helper method to get the SteamApiService from the UserService
     */
    private SteamApiService getSteamApiService() {
        try {
            // This would normally be done with proper dependency injection
            // but this is a helper method to access the service through the existing controllers
            return userStatusContainerController.getUserService().getSteamApiService();
        } catch (Exception e) {
            logger.error("Failed to retrieve SteamApiService", e);
            return null;
        }
    }
    
    // Static rate limiting controls to ensure we don't exceed API limits across the app
    private static final Object API_RATE_LOCK = new Object();
    private static final AtomicInteger pendingApiRequests = new AtomicInteger(0);
    private static final AtomicLong lastApiRequestTime = new AtomicLong(0);
    private static final int MAX_CONCURRENT_REQUESTS = 1; // Only allow 1 request at a time
    private static final long MIN_REQUEST_INTERVAL_MS = 2000; // 2 seconds between requests
    
    /**
     * Rate limiter that ensures we don't exceed OpenDota API limits
     * OpenDota free tier allows ~60 requests per minute
     * We'll target ~30 requests per minute to be safe (1 request every 2 seconds)
     */
    private void applyApiRateLimit() {
        synchronized (API_RATE_LOCK) {
            // Check if we're under the concurrent request limit
            while (pendingApiRequests.get() >= MAX_CONCURRENT_REQUESTS) {
                try {
                    // Wait until a request completes
                    API_RATE_LOCK.wait(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("API rate limiting interrupted", e);
                }
            }
            
            // Check if we need to wait for time-based rate limiting
            long currentTime = System.currentTimeMillis();
            long timeToWait = (lastApiRequestTime.get() + MIN_REQUEST_INTERVAL_MS) - currentTime;
            
            if (timeToWait > 0) {
                try {
                    // Wait until the minimum interval has passed
                    Thread.sleep(timeToWait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("API rate limiting interrupted", e);
                }
            }
            
            // Mark this request as starting
            pendingApiRequests.incrementAndGet();
            lastApiRequestTime.set(System.currentTimeMillis());
        }
    }
    
    /**
     * Release the rate limiter after an API request completes
     */
    private void releaseApiRateLimit() {
        synchronized (API_RATE_LOCK) {
            // Mark this request as completed
            pendingApiRequests.decrementAndGet();
            // Notify waiting threads
            API_RATE_LOCK.notifyAll();
        }
    }
    
    /**
     * Generate player match data using real match IDs from the API
     * @param matchIds List of actual match IDs from the API
     */
    private void generatePlayerMatchesByIds(List<Long> matchIds) {
        logger.info("Generating player matches from match IDs: {}", matchIds);
        
        // No need to clear the table here as we'll be setting a new ObservableList
        // This avoids UnsupportedOperationException with filtered/sorted views
        
        // Show loading message
        Platform.runLater(() -> {
            recentMatchesTable.setPlaceholder(new Label("Loading match data... this may take a moment"));
            heroStatsTable.setPlaceholder(new Label("Calculating hero statistics..."));
            statusLabel.setText("Loading match data from OpenDota API...");
        });
        
        // Use an observable list that can be updated from any thread
        ObservableList<PlayerMatch> matches = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
        
        if (allHeroes.isEmpty() || matchIds.isEmpty()) {
            logger.warn("No heroes or match IDs available");
            Platform.runLater(() -> {
                recentMatchesTable.setPlaceholder(new Label("No match data available"));
                statusLabel.setText("No match data available");
            });
            return;
        }
        
        // Get current Steam ID to find the player in each match
        String currentSteamId = null;
        if (userStatusContainerController != null && 
            userStatusContainerController.getUserService().isLoggedIn()) {
            var user = userStatusContainerController.getUserService().getCurrentUser();
            if (user.isPresent()) {
                currentSteamId = user.get().getSteamId();
                logger.info("Current Steam ID for match player search: {}", currentSteamId);
            }
        }
        
        // Convert Steam ID to 32-bit account ID
        int accountId32 = 0;
        if (currentSteamId != null) {
            try {
                long steam64Id = Long.parseLong(currentSteamId);
                accountId32 = (int) (steam64Id & 0xFFFFFFFFL);
                logger.info("Current 32-bit account ID for match player search: {}", accountId32);
            } catch (NumberFormatException e) {
                logger.error("Failed to parse Steam ID", e);
            }
        }
        
        final int playerAccountId = accountId32;
        
        // Create a hero lookup map by ID
        Map<Integer, Hero> heroesById = new HashMap<>();
        for (Hero hero : allHeroes) {
            heroesById.put(hero.getId(), hero);
        }
        
        // Use a background thread pool for this work to avoid freezing the UI
        executorService.submit(() -> {
            try {
                // Set up our match fetch operation
                int maxMatches = Math.min(30, matchIds.size()); // Reduce to 30 matches to avoid too many API calls
                int batchSize = 1; // Process one match at a time to avoid rate limiting
                AtomicInteger processedCount = new AtomicInteger(0);
                AtomicInteger failedCount = new AtomicInteger(0);
                AtomicInteger cacheHitCount = new AtomicInteger(0);
                
                // Use a single thread to avoid multiple threads hitting the API
                // Process matches in small batches to keep UI responsive
                for (int i = 0; i < maxMatches; i += batchSize) {
                    // Get current batch
                    int endIdx = Math.min(i + batchSize, maxMatches);
                    List<Long> batch = matchIds.subList(i, endIdx);
                    List<PlayerMatch> batchMatches = new ArrayList<>();
                    
                    // Process batch - one at a time with improved rate limiting
                    for (Long matchId : batch) {
                        try {
                            // Try to fetch match details with improved rate limiting
                            // We no longer need synchronized block since our rate limiter handles that
                            PlayerMatch match = fetchMatchDetails(matchId, playerAccountId, heroesById, cacheHitCount);
                            
                            if (match != null) {
                                batchMatches.add(match);
                                int currentProcessed = processedCount.incrementAndGet();
                                
                                // Update progress in UI for each match 
                                Platform.runLater(() -> {
                                    statusLabel.setText(String.format("Loading matches... %d of %d (%d cached, %d failed)", 
                                                       currentProcessed, maxMatches, 
                                                       cacheHitCount.get(), failedCount.get()));
                                });
                            } else {
                                // Count failed fetches
                                failedCount.incrementAndGet();
                                logger.warn("Failed to fetch match {}", matchId);
                            }
                        } catch (Exception e) {
                            failedCount.incrementAndGet();
                            logger.error("Error processing match {}", matchId, e);
                        }
                    }
                    
                    // Add batch to main list and update UI
                    if (!batchMatches.isEmpty()) {
                        // Update table with the matches we have so far
                        final List<PlayerMatch> currentBatch = new ArrayList<>(batchMatches);
                        Platform.runLater(() -> {
                            // Add new matches to the table
                            matches.addAll(currentBatch);
                            
                            // Sort by date (most recent first)
                            SortedList<PlayerMatch> sortedMatches = new SortedList<>(matches);
                            sortedMatches.setComparator((a, b) -> b.getDate().compareTo(a.getDate()));
                            
                            // Update the table
                            recentMatchesTable.setItems(sortedMatches);
                            
                            // Also update hero stats table progressively
                            if (matches.size() > 5) { // Wait until we have enough data
                                populatePartialPlayerStatsFromMatches(new ArrayList<>(matches));
                            }
                        });
                    }
                }
                
                // Handle empty results
                if (matches.isEmpty()) {
                    logger.warn("Failed to fetch any real match data, falling back to mock data");
                    // If we couldn't fetch any real data, create mock matches with the match IDs
                    int counter = 0;
                    List<PlayerMatch> mockMatches = new ArrayList<>();
                    
                    for (Long matchId : matchIds) {
                        if (counter >= 20) break;
                        
                        Hero randomHero = allHeroes.get((int)(Math.random() * allHeroes.size()));
                        boolean won = Math.random() > 0.5;
                        int duration = 1500 + (int)(Math.random() * 1200);
                        LocalDateTime date = LocalDateTime.now().minusDays(counter);
                        int kills = 5 + (int)(Math.random() * 10);
                        int deaths = 2 + (int)(Math.random() * 8);
                        int assists = 5 + (int)(Math.random() * 15);
                        String mode = "All Pick";
                        
                        mockMatches.add(new PlayerMatch(matchId, randomHero, won, duration, date, 
                                                     kills, deaths, assists, mode));
                        counter++;
                    }
                    
                    // Update UI with mock data
                    Platform.runLater(() -> {
                        matches.addAll(mockMatches);
                        
                        // Sort by date
                        matches.sort((a, b) -> b.getDate().compareTo(a.getDate()));
                        recentMatchesTable.setItems(matches);
                        
                        // Update hero stats
                        populatePartialPlayerStatsFromMatches(mockMatches);
                        statusLabel.setText("Showing mock match data (no real data available)");
                    });
                } else {
                    // Final UI update for successful fetch
                    Platform.runLater(() -> {
                        // Make final sort by date (most recent first)
                        matches.sort((a, b) -> b.getDate().compareTo(a.getDate()));
                        
                        // Final hero stats update with all matches
                        populatePartialPlayerStatsFromMatches(new ArrayList<>(matches));
                        
                        // Update status message with detailed statistics
                        int cacheHits = cacheHitCount.get();
                        int failed = failedCount.get();
                        int apiHits = processedCount.get() - cacheHits;
                        
                        statusLabel.setText(String.format("Loaded %d matches (%d from cache, %d from API, %d failed)", 
                                           matches.size(), cacheHits, apiHits, failed));
                        
                        logger.info("Match loading complete: {} matches loaded ({} from cache, {} from API, {} failed)",
                                   matches.size(), cacheHits, apiHits, failed);
                    });
                }
            } catch (Exception e) {
                logger.error("Error in match processing thread", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error loading match data");
                    recentMatchesTable.setPlaceholder(new Label("Error loading match data"));
                });
            }
        });
    }
    
    /**
     * Fetch details for a single match from the OpenDota API with caching
     * 
     * @param matchId The match ID to fetch
     * @param playerAccountId The player's account ID
     * @param heroesById Map of heroes by ID for looking up heroes
     * @param cacheHitCounter Optional counter to track cache hits (can be null)
     * @return PlayerMatch object if successful, null if failed
     */
    private PlayerMatch fetchMatchDetails(Long matchId, int playerAccountId, Map<Integer, Hero> heroesById, AtomicInteger cacheHitCounter) {
        // First check if we already have cached the match on disk
        Path cacheFile = null;
        try {
            // Determine cache directory location - use application data directory for better isolation
            Path appDataDir = determineAppDataDirectory();
            Path cacheDir = appDataDir.resolve("cache").resolve("matches");
            
            // Try to create cache directories with proper error handling
            if (!ensureCacheDirectoryExists(cacheDir)) {
                logger.warn("Cannot create or access cache directory, will fetch directly from API");
            } else {
                // Define cache file path
                cacheFile = cacheDir.resolve(matchId + ".json");
                
                // Check if cache file exists and is recent (less than 7 days old)
                if (Files.exists(cacheFile)) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(cacheFile, BasicFileAttributes.class);
                        long fileAgeDays = ChronoUnit.DAYS.between(
                            attrs.creationTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                            LocalDate.now()
                        );
                        
                        // If cache is fresh, read from file
                        if (fileAgeDays < 7) {
                            try {
                                // Read cached data with proper error handling
                                String cachedData = new String(Files.readAllBytes(cacheFile), StandardCharsets.UTF_8);
                                logger.debug("Using cached match data for match {}", matchId);
                                
                                // Parse and process the cached data
                                ObjectMapper objectMapper = new ObjectMapper();
                                JsonNode matchDetails = objectMapper.readTree(cachedData);
                                
                                // Increment cache hit counter if we passed one in
                                if (cacheHitCounter != null) {
                                    cacheHitCounter.incrementAndGet();
                                }
                                
                                return processMatchDetails(matchDetails, playerAccountId, heroesById);
                            } catch (IOException e) {
                                logger.warn("Error reading cached match {}: {}", matchId, e.getMessage());
                                // Continue to API fetch if cache read fails
                            }
                        } else {
                            logger.debug("Cache expired for match {} ({} days old)", matchId, fileAgeDays);
                        }
                    } catch (IOException e) {
                        logger.warn("Error checking cache file attributes: {}", e.getMessage());
                        // Continue to API fetch if we can't check attributes
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error with cache directory operations: {}", e.getMessage());
            // Continue with API request if cache setup fails
        }
        
        // Not in cache or cache expired or access issues, fetch from API
        try {
            // Apply our rate limiting before making the request
            applyApiRateLimit();
            
            try {
                // Create a connection to get match details from OpenDota API
                OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
                
                // Build URL for the match details
                String url = "https://api.opendota.com/api/matches/" + matchId;
                logger.debug("Fetching match details from API: {}", matchId);
                
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Dota2DraftAssistant/1.0")
                    .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        
                        // Save to cache file for future use if we have a valid cache file path
                        if (cacheFile != null) {
                            try {
                                // Use atomic file writing for safer caching
                                Path tempFile = cacheFile.getParent().resolve(matchId + ".tmp.json");
                                Files.write(tempFile, responseBody.getBytes(StandardCharsets.UTF_8));
                                Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                                logger.debug("Saved match {} to cache", matchId);
                            } catch (IOException e) {
                                logger.warn("Failed to save match to cache: {}", e.getMessage());
                                // Continue processing even if caching fails
                            }
                        }
                        
                        // Parse the match details
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode matchDetails = objectMapper.readTree(responseBody);
                        
                        // Process the match details into a PlayerMatch object
                        return processMatchDetails(matchDetails, playerAccountId, heroesById);
                    } else {
                        logger.error("Failed to get match details for {}: HTTP {}", 
                                  matchId, response.code());
                        
                        // If rate limited, add an additional delay to recover
                        if (response.code() == 429) {
                            logger.info("Rate limited by API - adding additional delay");
                            Thread.sleep(5000); // Wait an additional 5 seconds
                        }
                        
                        return null;
                    }
                } finally {
                    // Always release the rate limit, even if there was an error
                    releaseApiRateLimit();
                }
            } catch (Exception e) {
                // If there's an exception in the inner try block, we still need to release the rate limit
                releaseApiRateLimit();
                throw e; // Re-throw so the outer catch block can handle it
            }
        } catch (Exception e) {
            logger.error("Error fetching match details for match ID: {}", matchId, e);
            return null;
        }
    }
    
    /**
     * Determines the appropriate application data directory based on the operating system
     * @return Path to the application data directory
     */
    private Path determineAppDataDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        Path appDataDir;
        
        if (os.contains("win")) {
            // Windows: Use %APPDATA%\Dota2Assistant
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isEmpty()) {
                appData = System.getProperty("user.home") + "\\AppData\\Roaming";
            }
            appDataDir = Paths.get(appData, "Dota2Assistant");
        } else if (os.contains("mac")) {
            // macOS: Use ~/Library/Application Support/Dota2Assistant
            appDataDir = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "Dota2Assistant");
        } else {
            // Linux and others: Use ~/.dota2assistant
            appDataDir = Paths.get(System.getProperty("user.home"), ".dota2assistant");
        }
        
        logger.debug("Using application data directory: {}", appDataDir);
        return appDataDir;
    }
    
    /**
     * Ensures the cache directory exists and is writable
     * @param cacheDir Path to the cache directory
     * @return true if the directory exists and is writable, false otherwise
     */
    private boolean ensureCacheDirectoryExists(Path cacheDir) {
        try {
            // Create all directories in the path if they don't exist
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
                logger.info("Created cache directory: {}", cacheDir);
            }
            
            // Verify the directory is writable
            if (!Files.isWritable(cacheDir)) {
                logger.warn("Cache directory exists but is not writable: {}", cacheDir);
                return false;
            }
            
            // Create a test file to verify we can write to the directory
            Path testFile = cacheDir.resolve(".write-test");
            Files.write(testFile, "test".getBytes(StandardCharsets.UTF_8));
            Files.deleteIfExists(testFile);
            
            return true;
        } catch (IOException e) {
            logger.warn("Failed to create or verify cache directory {}: {}", cacheDir, e.getMessage());
            return false;
        }
    }
    
    /**
     * Process match details from JSON into a PlayerMatch object
     */
    private PlayerMatch processMatchDetails(JsonNode matchDetails, int playerAccountId, Map<Integer, Hero> heroesById) {
        try {
            // Extract match-level data
            boolean radiantWin = matchDetails.path("radiant_win").asBoolean();
            int duration = matchDetails.path("duration").asInt();
            long startTime = matchDetails.path("start_time").asLong();
            int gameMode = matchDetails.path("game_mode").asInt();
            long matchId = matchDetails.path("match_id").asLong();
            
            // Find the player's data in the players array
            boolean playerFound = false;
            boolean isRadiant = false;
            int heroId = 0;
            int kills = 0;
            int deaths = 0;
            int assists = 0;
            boolean won = false;
            
            JsonNode players = matchDetails.path("players");
            if (players.isArray()) {
                for (JsonNode player : players) {
                    // Check if this is the current player
                    int playerAccId = player.path("account_id").asInt();
                    if (playerAccountId > 0 && playerAccId == playerAccountId) {
                        playerFound = true;
                        heroId = player.path("hero_id").asInt();
                        kills = player.path("kills").asInt();
                        deaths = player.path("deaths").asInt();
                        assists = player.path("assists").asInt();
                        isRadiant = player.path("player_slot").asInt() < 128; // 0-127 are Radiant
                        won = (isRadiant && radiantWin) || (!isRadiant && !radiantWin);
                        break;
                    }
                }
            }
            
            // If player wasn't found in the match
            if (!playerFound) {
                logger.warn("Player account {} not found in match {}", playerAccountId, matchId);
                // Take first player's hero for testing/development
                if (players.isArray() && players.size() > 0) {
                    heroId = players.get(0).path("hero_id").asInt();
                    kills = players.get(0).path("kills").asInt();
                    deaths = players.get(0).path("deaths").asInt();
                    assists = players.get(0).path("assists").asInt();
                    isRadiant = players.get(0).path("player_slot").asInt() < 128;
                    won = (isRadiant && radiantWin) || (!isRadiant && !radiantWin);
                } else {
                    return null; // Skip this match if no player data
                }
            }
            
            // Convert timestamp to LocalDateTime
            LocalDateTime date = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(startTime), 
                ZoneId.systemDefault()
            );
            
            // Map game mode ID to readable name
            String modeStr;
            switch (gameMode) {
                case 1: modeStr = "All Pick"; break;
                case 2: modeStr = "Captains Mode"; break;
                case 3: modeStr = "Random Draft"; break;
                case 4: modeStr = "Single Draft"; break;
                case 5: modeStr = "All Random"; break;
                case 22: modeStr = "Ranked All Pick"; break;
                case 23: modeStr = "Turbo"; break;
                default: modeStr = "Mode " + gameMode;
            }
            
            // Find the hero by ID from our hero repository
            Hero hero = heroesById.get(heroId);
            if (hero == null) {
                logger.warn("Hero ID {} not found in repository", heroId);
                // Use first hero as placeholder if hero not found
                if (!allHeroes.isEmpty()) {
                    hero = allHeroes.get(0);
                } else {
                    return null; // Skip this match if no hero found
                }
            }
            
            // Create the player match with actual data
            if (hero != null) {
                PlayerMatch match = new PlayerMatch(
                    matchId, hero, won, duration, date, kills, deaths, assists, modeStr
                );
                match.setRadiantSide(isRadiant);
                match.setRadiantWin(radiantWin);
                return match;
            }
            
            return null;
        } catch (Exception e) {
            logger.error("Error processing match details: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Generate hero stats based on match data for more consistency
     * @param matches The list of matches to base hero stats on
     */
    private void populatePartialPlayerStatsFromMatches(List<PlayerMatch> matches) {
        // No need to clear existing items since we're going to set a new list
        // This avoids potential UnsupportedOperationException with filtered/sorted views
        
        // Create a map to track stats for each hero
        Map<Hero, PlayerHeroStatTracker> heroStats = new HashMap<>();
        
        // Process all matches to build hero stats
        for (PlayerMatch match : matches) {
            Hero hero = match.getHero();
            
            // Get or create tracker for this hero
            PlayerHeroStatTracker tracker = heroStats.computeIfAbsent(hero, h -> new PlayerHeroStatTracker(h));
            
            // Update tracker with match data
            tracker.addMatch(match.isWon(), match.getDate(), match.getKills(), match.getDeaths(), match.getAssists());
        }
        
        // Convert trackers to hero stat objects
        ObservableList<PlayerHeroStat> heroStatList = FXCollections.observableArrayList();
        for (PlayerHeroStatTracker tracker : heroStats.values()) {
            heroStatList.add(tracker.toPlayerHeroStat());
        }
        
        // Sort by most played
        heroStatList.sort((a, b) -> Integer.compare(b.getMatches(), a.getMatches()));
        
        // Set hero stats to the table
        heroStatsTable.setItems(heroStatList);
    }
    
    /**
     * Helper class to track hero stats across multiple matches
     */
    private static class PlayerHeroStatTracker {
        private Hero hero;
        private int matches;
        private int wins;
        private LocalDateTime lastPlayed;
        private int totalKills;
        private int totalDeaths;
        private int totalAssists;
        
        public PlayerHeroStatTracker(Hero hero) {
            this.hero = hero;
            this.matches = 0;
            this.wins = 0;
            this.lastPlayed = LocalDateTime.MIN;
            this.totalKills = 0;
            this.totalDeaths = 0;
            this.totalAssists = 0;
        }
        
        public void addMatch(boolean won, LocalDateTime date, int kills, int deaths, int assists) {
            matches++;
            if (won) wins++;
            
            // Update last played if this match is more recent
            if (date.isAfter(lastPlayed)) {
                lastPlayed = date;
            }
            
            // Add stats
            totalKills += kills;
            totalDeaths += deaths;
            totalAssists += assists;
        }
        
        public PlayerHeroStat toPlayerHeroStat() {
            double winRate = matches > 0 ? (double) wins / matches : 0;
            double kdaRatio = totalDeaths > 0 ? 
                (double)(totalKills + totalAssists) / totalDeaths : 
                totalKills + totalAssists; // If no deaths, KDA is just K+A
            
            return new PlayerHeroStat(hero, matches, winRate, kdaRatio, lastPlayed);
        }
    }
    
    /**
     * Populate only hero statistics without match data.
     * This is used when no match data is available.
     */
    private void populatePartialPlayerStats() {
        // No need to clear existing items since we're going to set a new list
        // This avoids potential UnsupportedOperationException with filtered/sorted views
        
        // Create hero stats data based on the heroes that were played in matches
        // In a real implementation, this would be fetched from the API
        ObservableList<PlayerHeroStat> heroStats = FXCollections.observableArrayList();
        
        // For now, we'll generate some semi-realistic hero stats
        if (!allHeroes.isEmpty()) {
            // Generate some mock match data to derive hero stats from
            List<PlayerMatch> mockMatches = generateMockMatches(20);
            
            // Now use the same logic as when processing real match data
            populatePartialPlayerStatsFromMatches(mockMatches);
            return;
        }
        
        // Set empty hero stats to the table if we get here
        heroStatsTable.setItems(heroStats);
    }
    
    /**
     * Generates mock match data for testing purposes
     * @param count Number of matches to generate
     * @return List of mock matches
     */
    private List<PlayerMatch> generateMockMatches(int count) {
        List<PlayerMatch> matches = new ArrayList<>();
        
        if (allHeroes.isEmpty()) {
            return matches;
        }
        
        // Create a distribution of heroes that's weighted toward favorites
        // Players tend to play a core set of heroes more frequently
        List<Hero> favoriteHeroes = new ArrayList<>();
        // Select 5-8 favorite heroes
        int favoriteCount = 5 + new Random().nextInt(4);
        Set<Integer> selectedIndices = new HashSet<>();
        
        // Select random favorite heroes
        while (favoriteHeroes.size() < favoriteCount && favoriteHeroes.size() < allHeroes.size()) {
            int index = new Random().nextInt(allHeroes.size());
            if (!selectedIndices.contains(index)) {
                selectedIndices.add(index);
                favoriteHeroes.add(allHeroes.get(index));
            }
        }
        
        // Current time to base our match timestamps on
        LocalDateTime now = LocalDateTime.now();
        
        // Get the match history timespan - last 30 days
        long totalTimespan = 30 * 24 * 60; // 30 days in minutes
        
        // Generate matches
        for (int i = 0; i < count; i++) {
            Hero hero;
            
            // 70% chance to play a favorite hero
            if (Math.random() < 0.7 && !favoriteHeroes.isEmpty()) {
                hero = favoriteHeroes.get(new Random().nextInt(favoriteHeroes.size()));
            } else {
                // 30% chance to play another hero
                hero = allHeroes.get(new Random().nextInt(allHeroes.size()));
            }
            
            // Generate match timestamp with exponential distribution (more recent = more common)
            double timeFactor = Math.exp(-0.1 * i);
            long minutesAgo = (long)(totalTimespan * (1 - timeFactor));
            LocalDateTime date = now.minusMinutes(minutesAgo);
            
            // Win rate tends to be higher on favorite heroes
            boolean won = favoriteHeroes.contains(hero) ? 
                Math.random() < 0.55 : // 55% win rate on favorites
                Math.random() < 0.48;  // 48% win rate on other heroes
            
            // Other match stats
            long matchId = 8000000000L + (long)(Math.random() * 200000000L);
            int duration = 1500 + (int)(Math.random() * 1800); // 25-55 minutes
            
            // Game performance more likely to be good if they won
            int kills = won ? 
                4 + (int)(Math.random() * 16) : // 4-20 kills for wins
                2 + (int)(Math.random() * 10);  // 2-12 kills for losses
            
            int deaths = won ? 
                1 + (int)(Math.random() * 7) :  // 1-8 deaths for wins
                3 + (int)(Math.random() * 10);  // 3-13 deaths for losses
            
            int assists = 3 + (int)(Math.random() * 20); // 3-23 assists
            
            // Game modes with realistic distribution
            String mode;
            double modeRand = Math.random();
            if (modeRand < 0.6) {
                mode = "Ranked All Pick";
            } else if (modeRand < 0.8) {
                mode = "Turbo";
            } else if (modeRand < 0.95) {
                mode = "All Pick";
            } else {
                mode = "Captains Mode";
            }
            
            matches.add(new PlayerMatch(matchId, hero, won, duration, date, kills, deaths, assists, mode));
        }
        
        // Sort by date
        matches.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        
        return matches;
    }
    
    /**
     * Populate tables with mock player statistics for demonstration.
     * In a real implementation, this would use data from the API.
     */
    private void populateMockPlayerStats() {
        // Clear existing data
        if (heroStatsTable.getItems() != null) {
            heroStatsTable.getItems().clear();
        }
        if (recentMatchesTable.getItems() != null) {
            recentMatchesTable.getItems().clear();
        }
        
        // Set account level
        accountLevelLabel.setText("42");
        
        // Create mock hero stats data
        ObservableList<PlayerHeroStat> heroStats = FXCollections.observableArrayList();
        
        // Create some mock heroes from our list
        if (!allHeroes.isEmpty()) {
            // Use the first 10 heroes as mock data
            List<Hero> sampleHeroes = allHeroes.size() > 10 ? 
                    allHeroes.subList(0, 10) : allHeroes;
                    
            // Add hero stats with randomized values
            for (Hero hero : sampleHeroes) {
                int matches = 10 + (int)(Math.random() * 90); // 10-100 matches
                double winRate = 0.40 + (Math.random() * 0.30); // 40-70% win rate
                double kdaRatio = 1.5 + (Math.random() * 3.5); // 1.5-5.0 KDA
                LocalDateTime lastPlayed = LocalDateTime.now().minusDays((int)(Math.random() * 30));
                
                heroStats.add(new PlayerHeroStat(hero, matches, winRate, kdaRatio, lastPlayed));
            }
        }
        
        // Sort by most played
        heroStats.sort((a, b) -> Integer.compare(b.getMatches(), a.getMatches()));
        
        // Create mock match data
        ObservableList<PlayerMatch> matches = FXCollections.observableArrayList();
        
        if (!allHeroes.isEmpty()) {
            // Create 20 random matches
            for (int i = 0; i < 20; i++) {
                // Random hero from the available list
                Hero randomHero = allHeroes.get((int)(Math.random() * allHeroes.size()));
                
                long matchId = 8000000000L + (long)(Math.random() * 200000000L);
                boolean won = Math.random() > 0.5;
                int duration = 1200 + (int)(Math.random() * 2400); // 20-60 minutes
                LocalDateTime date = LocalDateTime.now().minusDays(i).minusHours((int)(Math.random() * 24));
                int kills = (int)(Math.random() * 20);
                int deaths = (int)(Math.random() * 12);
                int assists = (int)(Math.random() * 25);
                
                // Game modes
                String[] modes = {"All Pick", "Captains Mode", "Ranked All Pick", "Turbo", "Ability Draft"};
                String mode = modes[(int)(Math.random() * modes.length)];
                
                matches.add(new PlayerMatch(matchId, randomHero, won, duration, date, 
                                            kills, deaths, assists, mode));
            }
        }
        
        // Sort matches by date (most recent first)
        matches.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        
        // Add to tables
        heroStatsTable.setItems(heroStats);
        recentMatchesTable.setItems(matches);
    }
    
    /**
     * Handler for the Refresh Profile button.
     */
    @FXML
    private void onRefreshProfile() {
        // Clear any existing data
        clearPlayerProfileData();
        
        // Show loading status
        statusLabel.setText("Refreshing player profile...");
        
        // Load fresh data
        loadPlayerProfile();
    }
    
    /**
     * Loads player hero performance data from the recommendation service.
     * This updates the hero cells in the grid with performance indicators.
     */
    private void loadPlayerHeroPerformance() {
        if (playerRecommendationService == null) {
            logger.warn("Cannot load player hero performance: PlayerRecommendationService not set");
            return;
        }
        
        if (userStatusContainerController == null || 
            !userStatusContainerController.getUserService().isLoggedIn()) {
            logger.info("No user logged in, skipping player hero performance load");
            return;
        }
        
        // Get current user's Steam ID and convert to account ID
        try {
            var userOpt = userStatusContainerController.getUserService().getCurrentUser();
            if (userOpt.isPresent()) {
                String steamId = userOpt.get().getSteamId();
                long steam64Id = Long.parseLong(steamId);
                int accountId = (int) (steam64Id & 0xFFFFFFFFL);
                
                logger.info("Loading performance data for account ID: {}", accountId);
                
                // Load performance data in background thread
                executorService.submit(() -> {
                    try {
                        // Get performance data from service
                        Map<Integer, PlayerHeroPerformance> performanceData = 
                            playerRecommendationService.getPlayerHeroPerformance(accountId);
                        
                        // Store performance data
                        heroPerformanceMap.clear();
                        heroPerformanceMap.putAll(performanceData);
                        
                        // Update UI on JavaFX thread
                        Platform.runLater(() -> {
                            updateHeroGridWithPerformanceData();
                            logger.info("Updated hero grid with player performance data");
                        });
                    } catch (Exception e) {
                        logger.error("Error loading player hero performance data", e);
                    }
                });
            } else {
                logger.warn("Cannot load performance data: no user available");
            }
        } catch (Exception e) {
            logger.error("Error retrieving player account ID", e);
        }
    }
    
    /**
     * Updates the hero grid cells with player performance data.
     */
    private void updateHeroGridWithPerformanceData() {
        if (heroGridView == null || heroPerformanceMap.isEmpty()) {
            return;
        }
        
        // Apply performance data to heroes in the grid
        heroGridView.updateHeroPerformanceData(heroPerformanceMap);
        
        // Also update any recommendation lists
        updateRecommendations();
    }
    
    /**
     * Handler for the Update Matches button on the player profile tab.
     * Triggers a match history sync for the current user.
     */
    @FXML
    private void onUpdateMatches() {
        // Check if user is logged in
        boolean isLoggedIn = userStatusContainerController != null && 
                             userStatusContainerController.getUserService().isLoggedIn();
                             
        if (!isLoggedIn) {
            showAlert(Alert.AlertType.INFORMATION, "Login Required", 
                    "You need to log in with Steam to update match history.");
            return;
        }
        
        try {
            // Get the current user's Steam ID
            var userService = userStatusContainerController.getUserService();
            var currentUserOpt = userService.getCurrentUser();
            
            if (currentUserOpt.isEmpty()) {
                logger.warn("User is reported as logged in but getCurrentUser returned empty");
                showAlert(Alert.AlertType.ERROR, "Error", "Could not retrieve current user information");
                return;
            }
            
            SteamUser user = currentUserOpt.get();
            String steamId = user.getSteamId();
            
            // Show loading indicator
            statusLabel.setText("Syncing match history...");
            updateMatchesButton.setDisable(true);
            updateMatchesButton.setText("Syncing...");
            
            // Get the UserMatchService - create it if it doesn't exist
            // In a real app this would be injected via Spring or dependency injection
            var steamApiService = getSteamApiService();
            if (steamApiService == null) {
                logger.error("Cannot access SteamApiService");
                showAlert(Alert.AlertType.ERROR, "Error", "Steam API service not available");
                resetUpdateMatchesButton();
                return;
            }
            
            // Lookup method using reflection (would be directly available with proper DI)
            var userMatchService = userService.getSteamApiService().getUserMatchService();
            
            // Start the sync process asynchronously
            executorService.submit(() -> {
                try {
                    // Trigger the match history sync
                    var syncFuture = userMatchService.startMatchHistorySync(steamId);
                    
                    // Handle completion
                    syncFuture.thenAccept(matchCount -> {
                        Platform.runLater(() -> {
                            // Update UI when sync completes
                            if (matchCount > 0) {
                                statusLabel.setText("Added " + matchCount + " new matches to history");
                                
                                // Reload player profile to show updated matches
                                clearPlayerProfileData();
                                loadPlayerProfile();
                            } else {
                                statusLabel.setText("No new matches found");
                            }
                            resetUpdateMatchesButton();
                        });
                    }).exceptionally(ex -> {
                        Platform.runLater(() -> {
                            logger.error("Match history sync failed", ex);
                            statusLabel.setText("Match history sync failed");
                            showAlert(Alert.AlertType.ERROR, "Sync Failed", 
                                    "Failed to sync match history: " + ex.getMessage());
                            resetUpdateMatchesButton();
                        });
                        return null;
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        logger.error("Failed to start match history sync", e);
                        statusLabel.setText("Failed to start match history sync");
                        showAlert(Alert.AlertType.ERROR, "Error", 
                                "Failed to start match history sync: " + e.getMessage());
                        resetUpdateMatchesButton();
                    });
                }
            });
        } catch (Exception e) {
            logger.error("Error in onUpdateMatches", e);
            statusLabel.setText("Error updating matches");
            showAlert(Alert.AlertType.ERROR, "Error", "An error occurred: " + e.getMessage());
            resetUpdateMatchesButton();
        }
    }
    
    /**
     * Reset the Update Matches button to its original state
     */
    private void resetUpdateMatchesButton() {
        Platform.runLater(() -> {
            updateMatchesButton.setDisable(false);
            updateMatchesButton.setText("Update Matches");
        });
    }
    
    /**
     * Clears all player profile data to prepare for refresh
     */
    private void clearPlayerProfileData() {
        // Create new empty observable lists and set them to the tables
        // This avoids UnsupportedOperationException when trying to modify filtered/sorted views
        heroStatsTable.setItems(FXCollections.observableArrayList());
        
        // Clear match history table
        recentMatchesTable.setItems(FXCollections.observableArrayList());
        
        // Reset placeholders
        heroStatsTable.setPlaceholder(new Label("Loading hero statistics..."));
        recentMatchesTable.setPlaceholder(new Label("Loading match history..."));
        
        // Reset account info
        accountLevelLabel.setText("Loading...");
        
        // Re-setup the filters with empty collections to avoid filter errors
        // when the items are later populated
        setupHeroStatsFilter();
    }
    
    /**
     * Handler for the Connect to Game button in the Live Assistant tab.
     */
    @FXML
    private void onConnectToGame() {
        statusLabel.setText("Attempting to connect to Dota 2...");
        
        // Simulate connection attempt
        executorService.submit(() -> {
            // Simulate network delay
            try {
                Thread.sleep(2000);
                
                // Simulate failed connection (would be real in production)
                Platform.runLater(() -> {
                    gameConnectionStatus.setText("Connection Failed");
                    gameConnectionStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: #ff4d4d;");
                    
                    showAlert(Alert.AlertType.INFORMATION, "Connection Status", 
                            "This feature is not yet implemented.\n\n" +
                            "In the future, this will connect to the Dota 2 Game Coordinator " +
                            "and provide real-time assistance during drafts.");
                    
                    statusLabel.setText("Failed to connect to Dota 2");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Connection attempt interrupted", e);
            }
        });
    }
    
    /**
     * Handler for the Update Player Stats menu item.
     */
    @FXML
    private void onUpdatePlayerStats() {
        boolean isLoggedIn = userStatusContainerController != null && 
                             userStatusContainerController.getUserService().isLoggedIn();
                             
        if (!isLoggedIn) {
            showAlert(Alert.AlertType.INFORMATION, "Login Required", 
                    "You need to log in with Steam to update player statistics.");
            return;
        }
        
        // Switch to player profile tab
        switchToPlayerProfile();
        
        // Refresh the profile
        onRefreshProfile();
    }
    
    /**
     * Handler for the Save Draft menu item.
     */
    @FXML
    private void onSaveDraft() {
        if (!draftInProgress.get() && draftEngine.getTeamPicks(Team.RADIANT).isEmpty() 
                && draftEngine.getTeamPicks(Team.DIRE).isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Nothing to Save", 
                    "There is no active draft to save.");
            return;
        }
        
        // This would normally open a save dialog and save the draft to a file
        showAlert(Alert.AlertType.INFORMATION, "Feature Coming Soon", 
                "The ability to save drafts will be available in a future update.");
    }
    
    /**
     * Handler for the Load Draft menu item.
     */
    @FXML
    private void onLoadDraft() {
        // This would normally open a load dialog and load a draft from a file
        showAlert(Alert.AlertType.INFORMATION, "Feature Coming Soon", 
                "The ability to load saved drafts will be available in a future update.");
    }
    
    /**
     * Debug method to force a ban action regardless of turn state
     */
    @FXML
    private void onForceBanHero() {
        if (selectedHero == null) {
            logger.warn("Cannot force ban - no hero selected");
            return;
        }
        
        try {
            logger.info("Force banning hero: {}", selectedHero.getLocalizedName());
            boolean success = draftEngine.banHero(selectedHero);
            
            if (success) {
                // Add entry to timeline
                DraftPhase currentPhase = draftEngine.getCurrentPhase();
                Team playerTeam = mapSideComboBox.getValue().equals("Radiant") ? Team.RADIANT : Team.DIRE;
                String teamName = playerTeam == Team.RADIANT ? "Radiant" : "Dire";
                
                String phaseStr = "";
                switch (currentPhase) {
                    case CM_BAN_1: phaseStr = "Ban Phase 1"; break;
                    case CM_PICK_1: phaseStr = "Pick Phase 1"; break;
                    case CM_BAN_2: phaseStr = "Ban Phase 2"; break;
                    case CM_PICK_2: phaseStr = "Pick Phase 2"; break;
                    case CM_BAN_3: phaseStr = "Ban Phase 3"; break;
                    case CM_PICK_3: phaseStr = "Pick Phase 3"; break;
                    default: phaseStr = currentPhase.toString();
                }
                
                draftTimeline.add(String.format("FORCE BAN: %s banned %s", teamName, selectedHero.getLocalizedName()));
                
                // Clear selection
                heroGridView.clearSelection();
                selectedHero = null;
                
                updateTeamDisplays();
                updateDraftStatus();
            }
        } catch (Exception e) {
            logger.error("Error during force ban", e);
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to ban hero: " + e.getMessage());
        }
    }
    
    /**
     * Debug method to force a pick action regardless of turn state
     */
    @FXML
    private void onForcePickHero() {
        if (selectedHero == null) {
            logger.warn("Cannot force pick - no hero selected");
            return;
        }
        
        try {
            logger.info("Force picking hero: {}", selectedHero.getLocalizedName());
            boolean success = draftEngine.selectHero(selectedHero);
            
            if (success) {
                // Add entry to timeline
                DraftPhase currentPhase = draftEngine.getCurrentPhase();
                Team playerTeam = mapSideComboBox.getValue().equals("Radiant") ? Team.RADIANT : Team.DIRE;
                String teamName = playerTeam == Team.RADIANT ? "Radiant" : "Dire";
                
                draftTimeline.add(String.format("FORCE PICK: %s picked %s", teamName, selectedHero.getLocalizedName()));
                
                // Clear selection
                heroGridView.clearSelection();
                selectedHero = null;
                
                updateTeamDisplays();
                updateDraftStatus();
            }
        } catch (Exception e) {
            logger.error("Error during force pick", e);
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to pick hero: " + e.getMessage());
        }
    }
    
    /**
     * Updates the player turn property in the DraftEngine to match
     * the current state based on player's team selection.
     *
     * @param playerTeam The team the player has selected (RADIANT or DIRE)
     */
    private void updatePlayerTurnProperty(Team playerTeam) {
        // Skip this method if we're not in a draft
        if (!draftInProgress.get()) {
            return;
        }
        
        // Get the current team's turn and determine if it's the player's turn
        Team currentTeam = draftEngine.getCurrentTeam();
        boolean isPlayerTurn = currentTeam == playerTeam;
        Team aiTeam = playerTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
        
        // Use the new setter method to directly update the playerTurnProperty
        if (draftEngine instanceof CaptainsModeDraftEngine) {
            ((CaptainsModeDraftEngine)draftEngine).setPlayerTurn(isPlayerTurn);
            
            // Determine the current phase for better logging
            DraftPhase currentPhase = draftEngine.getCurrentPhase();
            boolean isPick = currentPhase.toString().contains("PICK");
            String actionType = isPick ? "pick" : "ban";
            String teamTurn = isPlayerTurn ? "PLAYER" : "AI";
            
            logger.info("Updated playerTurnProperty: currentTeam={} ({}), playerTeam={}, isPlayerTurn={}, phase={}, action={}",
                       currentTeam == Team.RADIANT ? "Radiant" : "Dire",
                       teamTurn,
                       playerTeam == Team.RADIANT ? "Radiant" : "Dire",
                       isPlayerTurn,
                       currentPhase,
                       actionType);
                       
            // Highlight the active team as a visual cue
            highlightCurrentTeam(currentTeam);
                       
            // Also update the status label for clearer feedback
            if (isPlayerTurn) {
                statusLabel.setText("YOUR TURN to " + actionType + 
                    " (Turn " + (draftEngine.getCurrentTurnIndex() + 1) + " of 24)");
            } else if (!draftEngine.isDraftComplete()) {
                String teamName = currentTeam == Team.RADIANT ? "Radiant" : "Dire";
                statusLabel.setText("Waiting for " + teamName + " to " + actionType + 
                    " (Turn " + (draftEngine.getCurrentTurnIndex() + 1) + " of 24)");
            }
        } else {
            logger.warn("Cannot update playerTurnProperty - draft engine is not CaptainsModeDraftEngine");
        }
    }
    
    private static class HeroListCell extends ListCell<HeroRecommendation> {
        @Override
        protected void updateItem(HeroRecommendation recommendation, boolean empty) {
            super.updateItem(recommendation, empty);
            
            if (empty || recommendation == null) {
                setText(null);
                setGraphic(null);
            } else {
                // Get the hero object from the recommendation
                Hero hero = recommendation.getHero();
                
                // Create a more informative cell with hero name, primary attribute, and reasoning
                VBox mainContainer = new VBox(3);
                mainContainer.setPadding(new Insets(5));
                
                HBox topRow = new HBox(5);
                topRow.setAlignment(Pos.CENTER_LEFT);
                
                // Add a small icon or indicator based on primary attribute
                Label attributeIndicator = new Label();
                attributeIndicator.setPrefWidth(20);
                attributeIndicator.setPrefHeight(20);
                
                // Set style class based on hero's primary attribute
                String attributeClass = "attr-unknown";
                if (hero.getPrimaryAttribute() != null) {
                    switch (hero.getPrimaryAttribute()) {
                        case "str": attributeClass = "attr-str"; break;
                        case "agi": attributeClass = "attr-agi"; break;
                        case "int": attributeClass = "attr-int"; break;
                    }
                }
                attributeIndicator.getStyleClass().add(attributeClass);
                
                // Create the hero name label
                Label nameLabel = new Label(hero.getLocalizedName());
                nameLabel.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                
                // Add score indicator (percentage)
                String scoreText = String.format("%.0f%%", recommendation.getScore() * 100);
                Label scoreLabel = new Label(scoreText);
                scoreLabel.getStyleClass().add("recommendation-score");
                
                topRow.getChildren().addAll(attributeIndicator, nameLabel, scoreLabel);
                
                // Create a VBox for the detailed info
                VBox infoContainer = new VBox(2);
                
                // Add roles if available
                if (hero.getRoles() != null && !hero.getRoles().isEmpty()) {
                    String roles = String.join(", ", hero.getRoles());
                    if (roles.length() > 30) {
                        roles = roles.substring(0, 28) + "...";
                    }
                    Label rolesLabel = new Label(roles);
                    rolesLabel.getStyleClass().add("hero-roles");
                    rolesLabel.setFont(Font.font(null, FontWeight.LIGHT, 10));
                    rolesLabel.setOpacity(0.7);
                    infoContainer.getChildren().add(rolesLabel);
                }
                
                // Add win rate info if available
                if (recommendation.getWinRate() >= 0) {
                    Label winRateLabel = new Label("Win rate: " + recommendation.getWinRateFormatted());
                    winRateLabel.setFont(Font.font(null, FontWeight.NORMAL, 11));
                    infoContainer.getChildren().add(winRateLabel);
                }
                
                // Add reasoning text
                String reasoning = recommendation.getReasoningFormatted();
                if (reasoning != null && !reasoning.isEmpty()) {
                    Label reasoningLabel = new Label(reasoning);
                    reasoningLabel.setWrapText(true);
                    reasoningLabel.setMaxWidth(Double.MAX_VALUE);
                    reasoningLabel.setPrefWidth(Control.USE_COMPUTED_SIZE);
                    reasoningLabel.setFont(Font.font(null, FontWeight.NORMAL, 11));
                    reasoningLabel.getStyleClass().add("recommendation-reasoning");
                    infoContainer.getChildren().add(reasoningLabel);
                }
                
                // Add all containers together
                mainContainer.getChildren().addAll(topRow, infoContainer);
                mainContainer.getStyleClass().add("recommendation-cell");
                
                setGraphic(mainContainer);
                setText(null);
            }
        }
    }
}