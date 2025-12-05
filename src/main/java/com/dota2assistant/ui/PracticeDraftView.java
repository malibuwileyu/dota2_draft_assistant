package com.dota2assistant.ui;

import com.dota2assistant.domain.draft.*;
import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.Team;
import com.dota2assistant.domain.repository.HeroRepository;
import com.dota2assistant.ui.components.DraftTower;
import com.dota2assistant.ui.components.HeroGrid;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * Practice Draft view - simulate Captain's Mode drafts offline.
 * For learning draft strategy and experimenting with picks/bans.
 */
public class PracticeDraftView extends BorderPane {
    
    private final HeroRepository heroRepository;
    private final CaptainsModeDraft draftEngine;
    private DraftState state;
    
    private final HeroGrid heroGrid = new HeroGrid();
    private final DraftTower draftTower = new DraftTower();
    private final Button undoButton = new Button("↩ Undo");
    private final Button resetButton = new Button("⟳ New Draft");
    
    public PracticeDraftView(HeroRepository heroRepository) {
        this.heroRepository = heroRepository;
        this.draftEngine = new CaptainsModeDraft();
        setupUI();
        initDraft();
    }
    
    private void setupUI() {
        setStyle("-fx-background-color: #0a0e14;");
        
        // Hero grid fills center
        ScrollPane heroScroll = new ScrollPane(heroGrid);
        heroScroll.setFitToHeight(true);
        heroScroll.setFitToWidth(true);
        heroScroll.setStyle("-fx-background: #0a0e14; -fx-background-color: #0a0e14;");
        setCenter(heroScroll);
        
        // Draft tower + controls on right
        VBox rightPanel = new VBox(15);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setAlignment(Pos.TOP_CENTER);
        rightPanel.getChildren().addAll(draftTower, createControls());
        setRight(rightPanel);
        
        heroGrid.setOnHeroClick(this::handleHeroClick);
    }
    
    private HBox createControls() {
        styleButton(undoButton, "#374151", "#4b5563");
        styleButton(resetButton, "#1e40af", "#2563eb");
        undoButton.setOnAction(e -> handleUndo());
        resetButton.setOnAction(e -> initDraft());
        
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
        
        heroGrid.update(state.availableHeroes());
        draftTower.update(state.radiantPicks(), state.direPicks(),
                         state.radiantBans(), state.direBans(),
                         phase, team, state.turnIndex());
        
        undoButton.setDisable(state.history().isEmpty());
    }
}
