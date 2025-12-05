package com.dota2assistant.ui.components;

import com.dota2assistant.domain.model.Hero;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * Panel displaying a team's picks and bans.
 */
public class TeamPanel extends VBox {
    
    private final String teamName;
    private final String teamColor;
    private final VBox content = new VBox(5);
    
    public TeamPanel(String teamName, String color) {
        this.teamName = teamName;
        this.teamColor = color;
        
        setupUI();
    }
    
    private void setupUI() {
        Label titleLabel = new Label(teamName);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web(teamColor));
        
        content.setMinWidth(200);
        content.setMaxWidth(200);
        content.setStyle("-fx-background-color: #151b23; -fx-background-radius: 8;");
        content.setPadding(new Insets(10));
        
        setSpacing(10);
        setPadding(new Insets(0, 10, 0, 10));
        getChildren().addAll(titleLabel, content);
    }
    
    public void update(List<Hero> picks, List<Hero> bans) {
        content.getChildren().clear();
        
        Label picksLabel = new Label("PICKS");
        picksLabel.setTextFill(Color.web("#10b981"));
        picksLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        content.getChildren().add(picksLabel);
        
        for (Hero hero : picks) {
            Label l = new Label("• " + hero.localizedName());
            l.setTextFill(Color.WHITE);
            content.getChildren().add(l);
        }
        
        content.getChildren().add(new Separator());
        
        Label bansLabel = new Label("BANS");
        bansLabel.setTextFill(Color.web("#ef4444"));
        bansLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        content.getChildren().add(bansLabel);
        
        for (Hero hero : bans) {
            Label l = new Label("✕ " + hero.localizedName());
            l.setTextFill(Color.GRAY);
            content.getChildren().add(l);
        }
    }
}

