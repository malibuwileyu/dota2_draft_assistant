package com.dota2assistant.ui;

import com.dota2assistant.domain.draft.*;
import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.Team;
import com.dota2assistant.domain.repository.HeroRepository;
import com.dota2assistant.infrastructure.api.BackendApiClient;
import com.dota2assistant.ui.components.DraftSetupPanel;
import com.dota2assistant.ui.components.DraftTower;
import com.dota2assistant.ui.components.HeroGrid;
import com.dota2assistant.ui.components.RecommendationsPanel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Practice Draft view - simulate Captain's Mode drafts offline.
 * Supports VS AI (random opponent) or Full Manual (control both sides).
 * Includes AI-powered hero recommendations from backend.
 */
public class PracticeDraftView extends StackPane {
    
    private final HeroRepository heroRepository;
    private final BackendApiClient backendClient;
    private final CaptainsModeDraft draftEngine;
    private final Random random = new Random();
    private DraftState state;
    
    // User's settings
    private Team userSide = Team.RADIANT;      // What user SEES as their team
    private boolean userFirstPick = true;
    private boolean vsAi = true;
    
    // Engine mapping: First Pick = Radiant (A track), Second Pick = Dire (B track)
    private Team userEngineTeam() {
        return userFirstPick ? Team.RADIANT : Team.DIRE;
    }
    
    // UI components
    private final BorderPane draftPane = new BorderPane();
    private final DraftSetupPanel setupPanel = new DraftSetupPanel();
    private final HeroGrid heroGrid = new HeroGrid();
    private final DraftTower draftTower = new DraftTower();
    private final RecommendationsPanel recommendationsPanel = new RecommendationsPanel();
    private final Label sideLabel = new Label();
    private final Button undoButton = new Button("↩ Undo");
    private final Button resetButton = new Button("⟳ New Draft");
    
    public PracticeDraftView(HeroRepository heroRepository, BackendApiClient backendClient) {
        this.heroRepository = heroRepository;
        this.backendClient = backendClient;
        this.draftEngine = new CaptainsModeDraft();
        
        setupDraftPane();
        setupSetupPanel();
        showSetup();
    }
    
    private void setupDraftPane() {
        draftPane.setStyle("-fx-background-color: #0a0e14;");
        
        // Side indicator at top
        sideLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        sideLabel.setPadding(new Insets(10));
        
        HBox topBar = new HBox(sideLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(5, 15, 5, 15));
        topBar.setStyle("-fx-background-color: #111827;");
        draftPane.setTop(topBar);
        
        // Recommendations panel on left
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(10));
        leftPanel.getChildren().add(recommendationsPanel);
        draftPane.setLeft(leftPanel);
        
        // Hero grid fills center
        ScrollPane heroScroll = new ScrollPane(heroGrid);
        heroScroll.setFitToHeight(true);
        heroScroll.setFitToWidth(true);
        heroScroll.setStyle("-fx-background: #0a0e14; -fx-background-color: #0a0e14;");
        draftPane.setCenter(heroScroll);
        
        // Draft tower + controls on right
        VBox rightPanel = new VBox(15);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(draftTower, Priority.ALWAYS); // Let tower fill available height
        rightPanel.getChildren().addAll(draftTower, createControls());
        draftPane.setRight(rightPanel);
        
        heroGrid.setOnHeroClick(this::handleHeroClick);
    }
    
    private void setupSetupPanel() {
        setupPanel.setOnStart((side, firstPick, ai) -> {
            this.userSide = side;
            this.userFirstPick = firstPick;
            this.vsAi = ai;
            startDraft();
        });
    }
    
    private HBox createControls() {
        styleButton(undoButton, "#374151", "#4b5563");
        styleButton(resetButton, "#1e40af", "#2563eb");
        undoButton.setOnAction(e -> handleUndo());
        resetButton.setOnAction(e -> showSetup());
        
        HBox box = new HBox(10, undoButton, resetButton);
        box.setAlignment(Pos.CENTER);
        return box;
    }
    
    private void styleButton(Button btn, String bg, String hover) {
        btn.setFont(Font.font("System", FontWeight.BOLD, 11));
        String base = "-fx-background-color: %s; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 6 12;";
        btn.setStyle(base.formatted(bg));
        btn.setOnMouseEntered(e -> btn.setStyle(base.formatted(hover)));
        btn.setOnMouseExited(e -> btn.setStyle(base.formatted(bg)));
    }
    
    private void showSetup() {
        getChildren().clear();
        VBox overlay = new VBox(setupPanel);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: #0a0e14;");
        getChildren().add(overlay);
    }
    
    private void startDraft() {
        getChildren().clear();
        getChildren().add(draftPane);
        
        // Update side label
        String sideText = userSide == Team.RADIANT ? "RADIANT" : "DIRE";
        String pickText = userFirstPick ? "First Pick" : "Second Pick";
        String modeText = vsAi ? "vs AI" : "Manual";
        sideLabel.setText("You: " + sideText + " (" + pickText + ") - " + modeText);
        sideLabel.setTextFill(userSide == Team.RADIANT ? Color.web("#22c55e") : Color.web("#ef4444"));
        
        // Set user side on draft tower (determines which side is on left)
        draftTower.setUserSide(userSide, userFirstPick);
        
        initDraft();
    }
    
    private void initDraft() {
        List<Hero> heroes = heroRepository.findAll();
        heroGrid.setAllHeroes(heroes);
        heroGrid.clearFilter();
        state = draftEngine.initDraft(heroes, false);
        refreshUI();
        checkAiTurn();
        
        // Focus hero grid for keyboard search
        Platform.runLater(() -> heroGrid.requestFocus());
    }
    
    private void handleHeroClick(Hero hero) {
        if (draftEngine.isComplete(state)) return;
        
        // In AI mode, only allow user to pick for their own side
        if (vsAi) {
            Team currentTeam = draftEngine.getCurrentTeam(state);
            if (currentTeam != userEngineTeam()) return; // Not user's turn
        }
        
        try {
            DraftPhase phase = draftEngine.getCurrentPhase(state);
            state = phase.isBanPhase() ? draftEngine.banHero(state, hero) : draftEngine.pickHero(state, hero);
            refreshUI();
            checkAiTurn();
        } catch (DraftValidationException | InvalidDraftPhaseException ignored) {}
    }
    
    private void handleUndo() {
        if (state.history().isEmpty()) return;
        state = draftEngine.undo(state);
        
        // In AI mode, also undo the AI's move if it was AI's turn before
        if (vsAi && !state.history().isEmpty()) {
            Team currentTeam = draftEngine.getCurrentTeam(state);
            if (currentTeam != userEngineTeam()) {
                state = draftEngine.undo(state);
            }
        }
        
        refreshUI();
    }
    
    private void checkAiTurn() {
        if (!vsAi || draftEngine.isComplete(state)) return;
        
        Team currentTeam = draftEngine.getCurrentTeam(state);
        if (currentTeam != userEngineTeam()) {
            // AI's turn - make a random pick/ban after a short delay
            PauseTransition delay = new PauseTransition(Duration.millis(400));
            delay.setOnFinished(e -> makeAiMove());
            delay.play();
        }
    }
    
    private void makeAiMove() {
        if (draftEngine.isComplete(state)) return;
        
        List<Hero> available = state.availableHeroes();
        if (available.isEmpty()) return;
        
        // Pick a random available hero
        Hero pick = available.get(random.nextInt(available.size()));
        
        try {
            DraftPhase phase = draftEngine.getCurrentPhase(state);
            state = phase.isBanPhase() ? draftEngine.banHero(state, pick) : draftEngine.pickHero(state, pick);
            refreshUI();
            checkAiTurn(); // Check if AI has another consecutive turn
        } catch (DraftValidationException | InvalidDraftPhaseException ignored) {}
    }
    
    private void refreshUI() {
        DraftPhase phase = draftEngine.getCurrentPhase(state);
        Team currentTeam = draftEngine.getCurrentTeam(state);
        
        heroGrid.update(state.availableHeroes());
        draftTower.update(state.radiantPicks(), state.direPicks(),
                         state.radiantBans(), state.direBans(),
                         phase, currentTeam, state.turnIndex());
        
        undoButton.setDisable(state.history().isEmpty());
        
        // Fetch recommendations if it's user's turn (or always in manual mode)
        if (!draftEngine.isComplete(state)) {
            Team engineCurrentTeam = draftEngine.getCurrentTeam(state);
            if (!vsAi || engineCurrentTeam == userEngineTeam()) {
                fetchRecommendations();
            }
        }
    }
    
    private void fetchRecommendations() {
        recommendationsPanel.showLoading();
        
        // Determine ally/enemy based on user's team mapping
        List<Integer> allyPicks;
        List<Integer> enemyPicks;
        
        if (userEngineTeam() == Team.RADIANT) {
            allyPicks = state.radiantPicks().stream().map(Hero::id).collect(Collectors.toList());
            enemyPicks = state.direPicks().stream().map(Hero::id).collect(Collectors.toList());
        } else {
            allyPicks = state.direPicks().stream().map(Hero::id).collect(Collectors.toList());
            enemyPicks = state.radiantPicks().stream().map(Hero::id).collect(Collectors.toList());
        }
        
        // All banned heroes (both teams)
        List<Integer> bannedHeroes = state.radiantBans().stream().map(Hero::id).collect(Collectors.toList());
        bannedHeroes.addAll(state.direBans().stream().map(Hero::id).toList());
        
        DraftPhase phase = draftEngine.getCurrentPhase(state);
        String phaseStr = phase.isBanPhase() ? "ban" : "pick";
        
        backendClient.getRecommendations(allyPicks, enemyPicks, bannedHeroes, phaseStr, false)
            .thenAccept(response -> Platform.runLater(() -> recommendationsPanel.update(response)))
            .exceptionally(e -> {
                Platform.runLater(() -> recommendationsPanel.showError("Failed to load recommendations"));
                return null;
            });
    }
}
