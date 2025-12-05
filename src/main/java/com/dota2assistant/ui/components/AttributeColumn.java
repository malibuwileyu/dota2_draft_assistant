package com.dota2assistant.ui.components;

import com.dota2assistant.domain.model.Attribute;
import com.dota2assistant.domain.model.Hero;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A column of heroes filtered by attribute, displayed in a 2-column grid.
 */
public class AttributeColumn extends VBox {
    
    private static final int COLS = 2;
    private static final double COLUMN_WIDTH = COLS * 76 + 12;
    
    private final Attribute attribute;
    private final GridPane heroGrid = new GridPane();
    private final List<HeroButton> heroButtons = new ArrayList<>();
    private Consumer<Hero> onHeroClick = h -> {};
    
    public AttributeColumn(Attribute attribute) {
        this.attribute = attribute;
        setupUI();
    }
    
    private void setupUI() {
        String color = getColorForAttribute(attribute);
        
        Label header = new Label(attribute.name());
        header.setFont(Font.font("System", FontWeight.BOLD, 13));
        header.setTextFill(Color.web(color));
        header.setPadding(new Insets(8));
        header.setAlignment(Pos.CENTER);
        header.setMaxWidth(Double.MAX_VALUE);
        header.setStyle("-fx-background-color: " + color + "33; -fx-background-radius: 6 6 0 0;");
        
        heroGrid.setHgap(4);
        heroGrid.setVgap(4);
        heroGrid.setPadding(new Insets(6));
        heroGrid.setAlignment(Pos.TOP_CENTER);
        
        ScrollPane scroll = new ScrollPane(heroGrid);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: #0f172a; -fx-background-color: #0f172a;");
        scroll.setPannable(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        
        setSpacing(0);
        setAlignment(Pos.TOP_CENTER);
        setStyle("-fx-background-color: #0f172a; -fx-background-radius: 6;");
        setPrefWidth(COLUMN_WIDTH);
        setMinWidth(COLUMN_WIDTH);
        getChildren().addAll(header, scroll);
    }
    
    public void setHeroes(List<Hero> heroes) {
        heroButtons.clear();
        heroGrid.getChildren().clear();
        
        int row = 0, col = 0;
        for (Hero hero : heroes) {
            HeroButton btn = new HeroButton(hero);
            btn.setOnHeroClick(onHeroClick);
            heroButtons.add(btn);
            heroGrid.add(btn, col, row);
            
            col++;
            if (col >= COLS) {
                col = 0;
                row++;
            }
        }
    }
    
    public void updateAvailability(Set<Integer> availableIds) {
        for (HeroButton btn : heroButtons) {
            btn.setAvailable(availableIds.contains(btn.getHero().id()));
        }
    }
    
    public void setOnHeroClick(Consumer<Hero> handler) {
        this.onHeroClick = handler;
        for (HeroButton btn : heroButtons) {
            btn.setOnHeroClick(handler);
        }
    }
    
    private String getColorForAttribute(Attribute attr) {
        return switch (attr) {
            case STRENGTH -> "#ef4444";
            case AGILITY -> "#22c55e";
            case INTELLIGENCE -> "#3b82f6";
            case UNIVERSAL -> "#a855f7";
        };
    }
}
