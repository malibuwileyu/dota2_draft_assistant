package com.dota2assistant.ui.controller;

import com.dota2assistant.core.ai.AiDecisionEngine;
import com.dota2assistant.core.analysis.AnalysisEngine;
import com.dota2assistant.core.analysis.HeroRecommendation;
import com.dota2assistant.core.draft.DraftEngine;
import com.dota2assistant.core.draft.DraftMode;
import com.dota2assistant.core.draft.DraftPhase;
import com.dota2assistant.core.draft.Team;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.ui.component.HeroCell;
import com.dota2assistant.ui.component.HeroGridView;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
    
    private ObservableList<Hero> allHeroes = FXCollections.observableArrayList();
    private ObservableList<Hero> filteredHeroes = FXCollections.observableArrayList();
    private ObservableList<HeroRecommendation> recommendedPicks = FXCollections.observableArrayList();
    private ObservableList<HeroRecommendation> recommendedBans = FXCollections.observableArrayList();
    private ObservableList<String> draftTimeline = FXCollections.observableArrayList();
    
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
    private TextField heroSearchField;
    
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
    
    @FXML
    private Button actionButton;
    
    @FXML
    private ProgressBar radiantWinBar;
    
    @FXML
    private ProgressBar winProgressBackground;
    
    @FXML
    private Label winPercentageLabel;
    
    public MainController(DraftEngine draftEngine, 
                          AiDecisionEngine aiEngine, 
                          AnalysisEngine analysisEngine,
                          ExecutorService executorService,
                          ScheduledExecutorService timerService) {
        this.draftEngine = draftEngine;
        this.aiEngine = aiEngine;
        this.analysisEngine = analysisEngine;
        this.executorService = executorService;
        this.timerService = timerService;
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupComboBoxes();
        setupListViews();
        setupHeroGrid();
        setupListeners();
        applyCssClasses();
        setupWinProbabilityBar();
        resetUI();
        loadHeroes();
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
                
                // Determine if it's pick or ban phase
                boolean isPick = currentPhase.toString().contains("PICK");
                
                // Update action button immediately
                updateActionButton(isPlayerTurn, isPick);
            }
        });
        
        // Initial update
        selectedHeroProperty.set(selectedHero);
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
            updateTeamDisplays();
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
            draftTimeline.add("Draft started - " + mode + 
                             (timedMode ? " (Timed)" : "") +
                             " - Player: " + playerSide + " | AI: " + aiSide +
                             " - Radiant has first pick");
            
            // Determine player and AI teams based on side selection
            Team playerTeam = isRadiant ? Team.RADIANT : Team.DIRE;
            Team aiTeam = playerTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
            
            // In our implementation, Radiant is always first pick in the sequence
            // So if player wants first pick, they must be Radiant, otherwise they're Dire
            if (isFirstPick && !isRadiant) {
                logger.warn("First pick selected but player is on Dire - this may cause unexpected behavior");
            } else if (!isFirstPick && isRadiant) {
                logger.warn("Second pick selected but player is on Radiant - this may cause unexpected behavior");
            }
            
            // Determine if player gets to make the first move - Radiant always starts in our implementation
            boolean isPlayerFirst = isRadiant; // If player is Radiant, they go first
            
            logger.info("Starting draft - Player team: {}, AI team: {}, First pick: Radiant, Player starts: {}",
                       playerTeam == Team.RADIANT ? "Radiant" : "Dire", 
                       aiTeam == Team.RADIANT ? "Radiant" : "Dire",
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
        
        // Determine which team the player is on based on side selection
        Team playerTeam = mapSideComboBox.getValue().equals("Radiant") ? Team.RADIANT : Team.DIRE;
        Team aiTeam = playerTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
        
        // Add detailed debugging information
        int turnIndex = draftEngine.getCurrentTurnIndex();
        DraftPhase phase = draftEngine.getCurrentPhase();
        Team currentTeam = draftEngine.getCurrentTeam();
        
        logger.info("===== AI MOVE CALLED =====");
        logger.info("Current turn: {} of 24", turnIndex + 1);  // Fixed count to 24 instead of 25
        logger.info("Current phase: {}", phase);
        logger.info("Current team: {} ({})", currentTeam, currentTeam == Team.RADIANT ? "Radiant" : "Dire");
        logger.info("Player team: {} ({})", playerTeam, playerTeam == Team.RADIANT ? "Radiant" : "Dire");
        logger.info("AI team: {} ({})", aiTeam, aiTeam == Team.RADIANT ? "Radiant" : "Dire");
        logger.info("Is it AI's turn? {}", currentTeam == aiTeam);
        logger.info("=========================");
        
        // Verify that it's actually the AI's turn
        if (currentTeam != aiTeam) {
            logger.error("AI move was called but it's not AI's turn! Current team: {}, AI team: {}", 
                      currentTeam, aiTeam);
            statusLabel.setText("Your turn to " + 
                (draftEngine.getCurrentPhase().toString().contains("PICK") ? "pick" : "ban"));
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
        
        // Attribution of bans according to the Captains Mode draft sequence
        // Ban Phase 1: ABBABBA (7 bans) - Radiant gets 1st, 4th, 7th
        // Ban Phase 2: AAB (3 bans) - Radiant gets 1st, 2nd
        // Ban Phase 3: ABBA (4 bans) - Radiant gets 1st, 4th
        // 
        // Round numbers to ban indices (0-based):
        // Ban Phase 1: 0=R, 1=D, 2=D, 3=R, 4=D, 5=D, 6=R 
        // Ban Phase 2: 7,8=R, 9=D
        // Ban Phase 3: 10=R, 11,12=D, 13=R
        //
        // Use sets to track the indices for each team
        List<Integer> radiantBanIndices = Arrays.asList(0, 3, 6, 7, 8, 10, 13);
        
        // Log all bans for debugging
        logger.info("==== BAN ATTRIBUTION DEBUG ====");
        logger.info("Total bans: {}", bannedHeroes.size());
        for (int i = 0; i < bannedHeroes.size(); i++) {
            Hero hero = bannedHeroes.get(i);
            logger.info("Ban #{}: {} - Should go to {}", 
                       i, 
                       hero.getLocalizedName(),
                       radiantBanIndices.contains(i) ? "RADIANT" : "DIRE");
        }
        
        for (int i = 0; i < bannedHeroes.size(); i++) {
            Hero hero = bannedHeroes.get(i);
            HeroCell cell = new HeroCell(hero);
            
            // Properly attribute bans based on the draft sequence
            if (radiantBanIndices.contains(i)) {
                logger.info("Adding {} to RADIANT bans (index {})", hero.getLocalizedName(), i);
                radiantBansPane.getChildren().add(cell);
            } else {
                logger.info("Adding {} to DIRE bans (index {})", hero.getLocalizedName(), i);
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
            
            if (currentPlayerTurn) {
                statusLabel.setText(String.format("%s: YOUR TURN to %s (Turn %d of %d)", 
                                   phaseText, actionText, turnIndex + 1, totalTurns));
            } else {
                statusLabel.setText(String.format("%s: %s turn to %s (Turn %d of %d)", 
                                   phaseText, teamText, actionText, turnIndex + 1, totalTurns));
            }
            
            // Highlight the current team's section
            highlightCurrentTeam(currentTeam);
            
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
        } else if (team == Team.DIRE) {
            direPicksGrid.getParent().getStyleClass().add("active-team");
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
                    
                    double score = 0.4 * synergyScore + 0.4 * counterScore + 0.2 * (winRate > 0 ? winRate : 0.5);
                    
                    // Create recommendation with detailed metrics and reasons
                    HeroRecommendation recommendation = 
                        new HeroRecommendation(
                            hero, score, winRate, synergyScore, counterScore, pickCount, 
                            synergyReasons, counterReasons
                        );
                    
                    pickRecommendations.add(recommendation);
                }
                
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
                    
                    banRecommendations.add(new HeroRecommendation(
                        hero, score, winRate, synergyScore, counterScore, pickCount,
                        synergyReasons, counterReasons
                    ));
                }
                
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
        
        // Update the progress bar
        radiantWinBar.setProgress(radiantWinProbability);
        
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
        executorService.shutdownNow();
        timerService.shutdownNow();
        Platform.exit();
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