package com.dota2assistant.ui;

import com.dota2assistant.domain.draft.*;
import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.Team;
import com.dota2assistant.domain.repository.HeroRepository;
import com.dota2assistant.ui.components.HeroGrid;
import com.dota2assistant.ui.components.TeamPanel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * Main draft view showing hero grid, team picks/bans, and phase info.
 */
public class DraftView extends BorderPane {
    
    private final HeroRepository heroRepository;
    private final CaptainsModeDraft draftEngine;
    private DraftState state;
    
    private final Label phaseLabel = new Label();
    private final Label teamLabel = new Label();
    private final TeamPanel radiantPanel = new TeamPanel("RADIANT", "#3d8c40");
    private final TeamPanel direPanel = new TeamPanel("DIRE", "#c23c2a");
    private final HeroGrid heroGrid = new HeroGrid();
    private final Button undoButton = new Button("Undo");
    private final Button resetButton = new Button("New Draft");
    
    public DraftView(HeroRepository heroRepository) {
        this.heroRepository = heroRepository;
        this.draftEngine = new CaptainsModeDraft();
        
        setupUI();
        initDraft();
    }
    
    private void setupUI() {
        setStyle("-fx-background-color: #0a0e14;");
        setPadding(new Insets(20));
        
        setTop(createHeader());
        setLeft(radiantPanel);
        setRight(direPanel);
        setCenter(heroGrid);
        setBottom(createControls());
        
        heroGrid.setOnHeroClick(this::handleHeroClick);
    }
    
    private VBox createHeader() {
        phaseLabel.setFont(Font.font("System", FontWeight.BOLD, 28));
        phaseLabel.setTextFill(Color.WHITE);
        
        teamLabel.setFont(Font.font("System", 18));
        teamLabel.setTextFill(Color.LIGHTGRAY);
        
        VBox header = new VBox(5, phaseLabel, teamLabel);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(0, 0, 20, 0));
        return header;
    }
    
    private HBox createControls() {
        undoButton.setOnAction(e -> handleUndo());
        undoButton.setStyle("-fx-background-color: #374151; -fx-text-fill: white;");
        
        resetButton.setOnAction(e -> initDraft());
        resetButton.setStyle("-fx-background-color: #1e40af; -fx-text-fill: white;");
        
        HBox controls = new HBox(15, undoButton, resetButton);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(20, 0, 0, 0));
        return controls;
    }
    
    private void initDraft() {
        List<Hero> heroes = heroRepository.findAll();
        heroGrid.setAllHeroes(heroes);
        state = draftEngine.initDraft(heroes, false);
        refreshUI();
    }
    
    private void handleHeroClick(Hero hero) {
        if (draftEngine.isComplete(state)) return;
        
        try {
            DraftPhase phase = draftEngine.getCurrentPhase(state);
            state = phase.isBanPhase() ? draftEngine.banHero(state, hero) : draftEngine.pickHero(state, hero);
            refreshUI();
        } catch (DraftValidationException | InvalidDraftPhaseException ignored) {}
    }
    
    private void handleUndo() {
        if (state.history().isEmpty()) return;
        state = draftEngine.undo(state);
        refreshUI();
    }
    
    private void refreshUI() {
        DraftPhase phase = draftEngine.getCurrentPhase(state);
        Team team = draftEngine.getCurrentTeam(state);
        
        phaseLabel.setText(formatPhase(phase));
        updateTeamLabel(team);
        
        radiantPanel.update(state.radiantPicks(), state.radiantBans());
        direPanel.update(state.direPicks(), state.direBans());
        heroGrid.update(state.availableHeroes());
        
        undoButton.setDisable(state.history().isEmpty());
    }
    
    private void updateTeamLabel(Team team) {
        if (team != null) {
            teamLabel.setText(team + "'s Turn");
            teamLabel.setTextFill(team == Team.RADIANT ? Color.web("#3d8c40") : Color.web("#c23c2a"));
        } else {
            teamLabel.setText("Draft Complete!");
            teamLabel.setTextFill(Color.GOLD);
        }
    }
    
    private String formatPhase(DraftPhase phase) {
        return switch (phase) {
            case BAN_1 -> "Ban Phase 1";
            case PICK_1 -> "Pick Phase 1";
            case BAN_2 -> "Ban Phase 2";
            case PICK_2 -> "Pick Phase 2";
            case BAN_3 -> "Ban Phase 3";
            case PICK_3 -> "Pick Phase 3";
            case COMPLETED -> "Draft Complete";
        };
    }
}
