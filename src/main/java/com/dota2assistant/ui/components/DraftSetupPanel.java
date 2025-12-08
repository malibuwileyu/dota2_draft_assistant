package com.dota2assistant.ui.components;

import com.dota2assistant.domain.model.Team;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Setup panel for selecting side, pick order, and mode before practice draft.
 */
public class DraftSetupPanel extends VBox {
    
    private final ToggleGroup sideGroup = new ToggleGroup();
    private final ToggleGroup orderGroup = new ToggleGroup();
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final ToggleButton radiantBtn;
    private final ToggleButton direBtn;
    private final ToggleButton firstPickBtn;
    private final ToggleButton secondPickBtn;
    private final ToggleButton vsAiBtn;
    private final ToggleButton manualBtn;
    private final Button startBtn;
    
    private DraftSetupCallback onStart = (t, f, a) -> {};
    
    @FunctionalInterface
    public interface DraftSetupCallback {
        void onStart(Team side, boolean firstPick, boolean vsAi);
    }
    
    public DraftSetupPanel() {
        // Title
        Label title = new Label("PRACTICE DRAFT SETUP");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.WHITE);
        
        Label subtitle = new Label("Select your side, pick order, and mode");
        subtitle.setFont(Font.font("System", 14));
        subtitle.setTextFill(Color.LIGHTGRAY);
        
        // Side selection
        Label sideLabel = new Label("YOUR SIDE");
        sideLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        sideLabel.setTextFill(Color.web("#94a3b8"));
        
        radiantBtn = createToggle("RADIANT", "#22c55e", "#14532d");
        direBtn = createToggle("DIRE", "#ef4444", "#7f1d1d");
        radiantBtn.setToggleGroup(sideGroup);
        direBtn.setToggleGroup(sideGroup);
        radiantBtn.setSelected(true);
        preventDeselection(sideGroup);
        
        HBox sideBox = new HBox(15, radiantBtn, direBtn);
        sideBox.setAlignment(Pos.CENTER);
        
        // Pick order selection
        Label orderLabel = new Label("PICK ORDER");
        orderLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        orderLabel.setTextFill(Color.web("#94a3b8"));
        
        firstPickBtn = createToggle("FIRST PICK", "#3b82f6", "#1e40af");
        secondPickBtn = createToggle("SECOND PICK", "#8b5cf6", "#5b21b6");
        firstPickBtn.setToggleGroup(orderGroup);
        secondPickBtn.setToggleGroup(orderGroup);
        firstPickBtn.setSelected(true);
        preventDeselection(orderGroup);
        
        HBox orderBox = new HBox(15, firstPickBtn, secondPickBtn);
        orderBox.setAlignment(Pos.CENTER);
        
        // Mode selection
        Label modeLabel = new Label("OPPONENT MODE");
        modeLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        modeLabel.setTextFill(Color.web("#94a3b8"));
        
        vsAiBtn = createToggle("VS AI", "#f59e0b", "#92400e");
        manualBtn = createToggle("FULL MANUAL", "#6b7280", "#374151");
        vsAiBtn.setToggleGroup(modeGroup);
        manualBtn.setToggleGroup(modeGroup);
        vsAiBtn.setSelected(true);
        preventDeselection(modeGroup);
        
        HBox modeBox = new HBox(15, vsAiBtn, manualBtn);
        modeBox.setAlignment(Pos.CENTER);
        
        // Info text
        Label info = new Label("VS AI = Opponent picks/bans automatically\nFull Manual = You control both sides");
        info.setFont(Font.font("System", 12));
        info.setTextFill(Color.web("#64748b"));
        info.setStyle("-fx-text-alignment: center;");
        
        // Start button
        startBtn = new Button("START DRAFT");
        startBtn.setFont(Font.font("System", FontWeight.BOLD, 16));
        startBtn.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; " +
                         "-fx-background-radius: 8; -fx-padding: 12 40; -fx-cursor: hand;");
        startBtn.setOnMouseEntered(e -> startBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; " +
                         "-fx-background-radius: 8; -fx-padding: 12 40; -fx-cursor: hand;"));
        startBtn.setOnMouseExited(e -> startBtn.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; " +
                         "-fx-background-radius: 8; -fx-padding: 12 40; -fx-cursor: hand;"));
        startBtn.setOnAction(e -> {
            Team side = radiantBtn.isSelected() ? Team.RADIANT : Team.DIRE;
            boolean firstPick = firstPickBtn.isSelected();
            boolean vsAi = vsAiBtn.isSelected();
            onStart.onStart(side, firstPick, vsAi);
        });
        
        // Layout
        setSpacing(18);
        setPadding(new Insets(35));
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: #111827; -fx-background-radius: 12;");
        setMaxWidth(450);
        setMaxHeight(520);
        
        VBox sideSection = new VBox(8, sideLabel, sideBox);
        sideSection.setAlignment(Pos.CENTER);
        
        VBox orderSection = new VBox(8, orderLabel, orderBox);
        orderSection.setAlignment(Pos.CENTER);
        
        VBox modeSection = new VBox(8, modeLabel, modeBox);
        modeSection.setAlignment(Pos.CENTER);
        
        getChildren().addAll(title, subtitle, sideSection, orderSection, modeSection, info, startBtn);
    }
    
    private ToggleButton createToggle(String text, String color, String selectedBg) {
        ToggleButton btn = new ToggleButton(text);
        btn.setFont(Font.font("System", FontWeight.BOLD, 14));
        btn.setPrefWidth(140);
        btn.setPrefHeight(42);
        
        String baseStyle = "-fx-background-color: #1e293b; -fx-text-fill: " + color + "; " +
                          "-fx-background-radius: 8; -fx-border-color: " + color + "; " +
                          "-fx-border-radius: 8; -fx-border-width: 2; -fx-cursor: hand;";
        String selectedStyle = "-fx-background-color: " + selectedBg + "; -fx-text-fill: white; " +
                              "-fx-background-radius: 8; -fx-border-color: " + color + "; " +
                              "-fx-border-radius: 8; -fx-border-width: 2; -fx-cursor: hand;";
        
        btn.setStyle(baseStyle);
        btn.selectedProperty().addListener((obs, old, selected) -> 
            btn.setStyle(selected ? selectedStyle : baseStyle));
        
        return btn;
    }
    
    public void setOnStart(DraftSetupCallback handler) {
        this.onStart = handler;
    }
    
    /** Prevents a ToggleGroup from having no selection */
    private void preventDeselection(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
    }
}
