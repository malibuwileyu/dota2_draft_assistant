package com.dota2assistant.ui.components;

import com.dota2assistant.domain.model.Hero;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Grid of hero buttons for selection.
 */
public class HeroGrid extends ScrollPane {
    
    private final FlowPane grid = new FlowPane(5, 5);
    private Consumer<Hero> onHeroClick = h -> {};
    private List<Hero> allHeroes = List.of();
    
    public HeroGrid() {
        setFitToWidth(true);
        setStyle("-fx-background: #0a0e14; -fx-background-color: #0a0e14;");
        grid.setStyle("-fx-background-color: #0a0e14;");
        grid.setPadding(new Insets(10));
        grid.setAlignment(Pos.TOP_LEFT);
        setContent(grid);
    }
    
    public void setOnHeroClick(Consumer<Hero> handler) {
        this.onHeroClick = handler;
    }
    
    public void setAllHeroes(List<Hero> heroes) {
        this.allHeroes = heroes;
    }
    
    public void update(List<Hero> availableHeroes) {
        Set<Integer> availableIds = availableHeroes.stream()
            .map(Hero::id).collect(Collectors.toSet());
        
        grid.getChildren().clear();
        
        for (Hero hero : allHeroes) {
            boolean available = availableIds.contains(hero.id());
            Button btn = createHeroButton(hero, available);
            grid.getChildren().add(btn);
        }
    }
    
    private Button createHeroButton(Hero hero, boolean available) {
        Button btn = new Button(hero.localizedName());
        btn.setPrefWidth(110);
        btn.setPrefHeight(40);
        
        if (available) {
            btn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: white; -fx-cursor: hand;");
            btn.setOnAction(e -> onHeroClick.accept(hero));
            btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #334155; -fx-text-fill: white;"));
            btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: white;"));
        } else {
            btn.setStyle("-fx-background-color: #0f172a; -fx-text-fill: #4b5563;");
            btn.setDisable(true);
        }
        
        return btn;
    }
}

