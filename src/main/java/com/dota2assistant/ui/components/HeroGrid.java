package com.dota2assistant.ui.components;

import com.dota2assistant.domain.model.Attribute;
import com.dota2assistant.domain.model.Hero;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Grid of heroes organized by attribute columns.
 * Supports keyboard search to filter heroes by name.
 */
public class HeroGrid extends StackPane {
    
    private static final long CLEAR_THRESHOLD_MS = 3000; // ESC clears all if >3s since last key
    
    private final HBox columnsBox = new HBox();
    private final AttributeColumn strColumn = new AttributeColumn(Attribute.STRENGTH);
    private final AttributeColumn agiColumn = new AttributeColumn(Attribute.AGILITY);
    private final AttributeColumn intColumn = new AttributeColumn(Attribute.INTELLIGENCE);
    private final AttributeColumn uniColumn = new AttributeColumn(Attribute.UNIVERSAL);
    
    // Search state
    private final StringBuilder searchText = new StringBuilder();
    private final Label searchLabel = new Label();
    private long lastKeyTime = 0;
    private Timeline fadeTimer;
    
    private Consumer<Hero> onHeroClick = h -> {};
    
    public HeroGrid() {
        columnsBox.setSpacing(8);
        columnsBox.setPadding(new Insets(10));
        columnsBox.setStyle("-fx-background-color: #0a0e14;");
        columnsBox.getChildren().addAll(strColumn, agiColumn, intColumn, uniColumn);
        
        // Search label overlay
        searchLabel.setFont(Font.font("System", FontWeight.BOLD, 28));
        searchLabel.setTextFill(Color.web("#fbbf24"));
        searchLabel.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-padding: 10 20; -fx-background-radius: 8;");
        searchLabel.setVisible(false);
        StackPane.setAlignment(searchLabel, Pos.TOP_CENTER);
        StackPane.setMargin(searchLabel, new Insets(60, 0, 0, 0));
        
        getChildren().addAll(columnsBox, searchLabel);
        setStyle("-fx-background-color: #0a0e14;");
        
        // Set up keyboard handler
        setFocusTraversable(true);
        setOnKeyPressed(this::handleKeyPress);
        setOnKeyTyped(this::handleKeyTyped);
        
        // Request focus when clicked
        setOnMouseClicked(e -> requestFocus());
    }
    
    public void setOnHeroClick(Consumer<Hero> handler) {
        this.onHeroClick = handler;
        strColumn.setOnHeroClick(handler);
        agiColumn.setOnHeroClick(handler);
        intColumn.setOnHeroClick(handler);
        uniColumn.setOnHeroClick(handler);
    }
    
    public void setAllHeroes(List<Hero> heroes) {
        Map<Attribute, List<Hero>> byAttr = heroes.stream()
            .collect(Collectors.groupingBy(Hero::primaryAttribute));
        
        strColumn.setHeroes(sorted(byAttr.getOrDefault(Attribute.STRENGTH, List.of())));
        agiColumn.setHeroes(sorted(byAttr.getOrDefault(Attribute.AGILITY, List.of())));
        intColumn.setHeroes(sorted(byAttr.getOrDefault(Attribute.INTELLIGENCE, List.of())));
        uniColumn.setHeroes(sorted(byAttr.getOrDefault(Attribute.UNIVERSAL, List.of())));
    }
    
    public void update(List<Hero> availableHeroes) {
        Set<Integer> ids = availableHeroes.stream().map(Hero::id).collect(Collectors.toSet());
        strColumn.updateAvailability(ids);
        agiColumn.updateAvailability(ids);
        intColumn.updateAvailability(ids);
        uniColumn.updateAvailability(ids);
    }
    
    private List<Hero> sorted(List<Hero> heroes) {
        return heroes.stream()
            .sorted(Comparator.comparing(Hero::localizedName))
            .toList();
    }
    
    // ============ Search/Filter functionality ============
    
    private void handleKeyTyped(KeyEvent e) {
        String ch = e.getCharacter();
        if (ch.length() == 1 && Character.isLetterOrDigit(ch.charAt(0))) {
            searchText.append(ch.toLowerCase());
            lastKeyTime = System.currentTimeMillis();
            updateFilter();
            resetFadeTimer();
        }
    }
    
    private void handleKeyPress(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE) {
            long now = System.currentTimeMillis();
            if (searchText.isEmpty()) {
                return; // Nothing to clear
            }
            
            if (now - lastKeyTime < CLEAR_THRESHOLD_MS) {
                // Recent typing - remove one letter
                searchText.deleteCharAt(searchText.length() - 1);
            } else {
                // Long pause - clear all
                searchText.setLength(0);
            }
            lastKeyTime = now;
            updateFilter();
            resetFadeTimer();
            e.consume();
        } else if (e.getCode() == KeyCode.BACK_SPACE) {
            if (!searchText.isEmpty()) {
                searchText.deleteCharAt(searchText.length() - 1);
                lastKeyTime = System.currentTimeMillis();
                updateFilter();
                resetFadeTimer();
            }
            e.consume();
        }
    }
    
    private void updateFilter() {
        String text = searchText.toString();
        strColumn.applyFilter(text);
        agiColumn.applyFilter(text);
        intColumn.applyFilter(text);
        uniColumn.applyFilter(text);
        
        // Update search label
        if (text.isEmpty()) {
            searchLabel.setVisible(false);
        } else {
            searchLabel.setText(text.toUpperCase());
            searchLabel.setVisible(true);
        }
    }
    
    private void resetFadeTimer() {
        if (fadeTimer != null) {
            fadeTimer.stop();
        }
        // Auto-clear search after 5 seconds of inactivity
        fadeTimer = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            searchText.setLength(0);
            updateFilter();
        }));
        fadeTimer.play();
    }
    
    /** Clear search filter (e.g., when draft resets) */
    public void clearFilter() {
        searchText.setLength(0);
        updateFilter();
        if (fadeTimer != null) fadeTimer.stop();
    }
}
