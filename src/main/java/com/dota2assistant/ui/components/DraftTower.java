package com.dota2assistant.ui.components;

import com.dota2assistant.domain.draft.DraftPhase;
import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.Team;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

/**
 * Staggered draft tower using VBox/HBox layout.
 */
public class DraftTower extends VBox {
    
    private static final double CROSS_STAGGER = 0.6;
    private static final double SAME_STAGGER = 1.0;
    private static final double SAME_GAP = 3;
    private static final double ASPECT = 1.8;
    private static final double SLOT_SCALE = 0.92; // Scale down individual slot size
    
    private final VBox slotsBox = new VBox(0);
    private final List<DraftSlot> slots = new ArrayList<>();
    private final List<HBox> rows = new ArrayList<>();
    private final Label phaseLabel = new Label();
    private final Label turnLabel = new Label();
    private final Label leftLabel = new Label("RAD");
    private final Label rightLabel = new Label("DIRE");
    private boolean flipped = false; // true = Dire on left, Radiant on right
    
    public DraftTower() {
        setupUI();
        buildSlots();
        heightProperty().addListener((obs, old, h) -> rescale());
        
        // Force rescale after window is shown
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((wo, oldW, newW) -> {
                    if (newW != null) {
                        newW.setOnShown(e -> javafx.application.Platform.runLater(this::rescale));
                    }
                });
            }
        });
    }
    
    private void setupUI() {
        phaseLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        phaseLabel.setTextFill(Color.WHITE);
        turnLabel.setFont(Font.font("System", 12));
        
        VBox header = new VBox(3, phaseLabel, turnLabel);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(8, 0, 8, 0));
        
        leftLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
        leftLabel.setMinWidth(50);
        leftLabel.setAlignment(Pos.CENTER);
        
        rightLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
        rightLabel.setMinWidth(50);
        rightLabel.setAlignment(Pos.CENTER);
        
        updateLabelColors();
        
        HBox teamLabels = new HBox(4, leftLabel, rightLabel);
        teamLabels.setAlignment(Pos.CENTER);
        teamLabels.setPadding(new Insets(0, 0, 5, 0));
        
        ScrollPane scroll = new ScrollPane(slotsBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        
        slotsBox.setAlignment(Pos.TOP_CENTER);
        
        setSpacing(0);
        setPadding(new Insets(8));
        setAlignment(Pos.TOP_CENTER);
        setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");
        setMinWidth(140);
        setPrefWidth(150);
        getChildren().addAll(header, teamLabels, scroll);
    }
    
    private void buildSlots() {
        for (int i = 0; i < SEQUENCE.size(); i++) {
            var turn = SEQUENCE.get(i);
            DraftSlot slot = new DraftSlot(turn.isPick);
            slots.add(slot);
            
            HBox row = new HBox(4);
            row.setAlignment(Pos.CENTER);
            
            Region spacer = new Region();
            
            // Determine if this slot goes on left or right
            // When flipped, Radiant goes right and Dire goes left
            boolean onLeft = flipped ? !turn.isRadiant : turn.isRadiant;
            
            if (onLeft) {
                row.getChildren().addAll(slot, spacer);
                HBox.setHgrow(spacer, Priority.ALWAYS);
            } else {
                row.getChildren().addAll(spacer, slot);
                HBox.setHgrow(spacer, Priority.ALWAYS);
            }
            
            rows.add(row);
            slotsBox.getChildren().add(row);
        }
    }
    
    private void rescale() {
        double available = getHeight() - 80;
        if (available <= 0) return;
        
        double totalUnits = calculateTotalUnits();
        int sameCount = countSameSideTransitions();
        double totalGaps = sameCount * SAME_GAP;
        
        // Calculate layout height (full size for spacing math)
        double layoutH = (available - totalGaps) / totalUnits;
        layoutH = Math.max(16, Math.min(36, layoutH));
        
        // Scale down the actual slot visual size
        double slotH = layoutH * SLOT_SCALE;
        double slotW = slotH * ASPECT;
        
        for (int i = 0; i < slots.size(); i++) {
            DraftSlot slot = slots.get(i);
            slot.resize(slotW, slotH);
            
            // Set row spacing based on layout height (not scaled slot height)
            if (i < SEQUENCE.size() - 1) {
                boolean currLeft = flipped ? !SEQUENCE.get(i).isRadiant : SEQUENCE.get(i).isRadiant;
                boolean nextLeft = flipped ? !SEQUENCE.get(i + 1).isRadiant : SEQUENCE.get(i + 1).isRadiant;
                boolean nextSameSide = currLeft == nextLeft;
                double margin = nextSameSide ? -layoutH * (1 - SAME_STAGGER) + SAME_GAP : -layoutH * (1 - CROSS_STAGGER);
                VBox.setMargin(rows.get(i + 1), new Insets(margin, 0, 0, 0));
            }
        }
    }
    
    private double calculateTotalUnits() {
        double units = 1.0;
        for (int i = 0; i < SEQUENCE.size() - 1; i++) {
            boolean currLeft = flipped ? !SEQUENCE.get(i).isRadiant : SEQUENCE.get(i).isRadiant;
            boolean nextLeft = flipped ? !SEQUENCE.get(i + 1).isRadiant : SEQUENCE.get(i + 1).isRadiant;
            boolean sameSide = currLeft == nextLeft;
            units += sameSide ? SAME_STAGGER : CROSS_STAGGER;
        }
        return units;
    }
    
    private int countSameSideTransitions() {
        int count = 0;
        for (int i = 0; i < SEQUENCE.size() - 1; i++) {
            boolean currLeft = flipped ? !SEQUENCE.get(i).isRadiant : SEQUENCE.get(i).isRadiant;
            boolean nextLeft = flipped ? !SEQUENCE.get(i + 1).isRadiant : SEQUENCE.get(i + 1).isRadiant;
            if (currLeft == nextLeft) count++;
        }
        return count;
    }
    
    public void update(List<Hero> rPicks, List<Hero> dPicks, List<Hero> rBans, List<Hero> dBans,
                       DraftPhase phase, Team team, int turnIndex) {
        phaseLabel.setText(formatPhase(phase));
        if (team != null) {
            turnLabel.setText(team + "'s Turn");
            turnLabel.setTextFill(team == Team.RADIANT ? Color.web("#22c55e") : Color.web("#ef4444"));
        } else {
            turnLabel.setText("Complete!");
            turnLabel.setTextFill(Color.GOLD);
        }
        
        slots.forEach(DraftSlot::clear);
        int rBan = 0, dBan = 0, rPick = 0, dPick = 0;
        
        for (int i = 0; i < SEQUENCE.size(); i++) {
            var turn = SEQUENCE.get(i);
            Hero hero = null;
            
            if (turn.isPick) {
                if (turn.isRadiant && rPick < rPicks.size()) hero = rPicks.get(rPick++);
                else if (!turn.isRadiant && dPick < dPicks.size()) hero = dPicks.get(dPick++);
            } else {
                if (turn.isRadiant && rBan < rBans.size()) hero = rBans.get(rBan++);
                else if (!turn.isRadiant && dBan < dBans.size()) hero = dBans.get(dBan++);
            }
            
            if (hero != null) slots.get(i).setHero(hero);
            else if (i == turnIndex && team != null) slots.get(i).setActive(true);
        }
    }
    
    /**
     * Configure display based on user's side and pick order.
     * User's side always appears on the LEFT of the tower.
     * - Radiant First Pick: no flip (RAD left, DIRE right)
     * - Radiant Second Pick: flip (DIRE left, RAD right) 
     * - Dire First Pick: flip (DIRE left, RAD right)
     * - Dire Second Pick: no flip (RAD left, DIRE right)
     */
    public void setUserSide(Team side, boolean userFirstPick) {
        // Flip when Dire is the first-pick team
        this.flipped = (side == Team.DIRE && userFirstPick) || (side == Team.RADIANT && !userFirstPick);
        updateLabelColors();
        rebuildSlots();
    }
    
    private void updateLabelColors() {
        // Labels always stay RAD left, DIRE right - only tower slots flip
        leftLabel.setText("RAD");
        leftLabel.setTextFill(Color.web("#22c55e"));
        rightLabel.setText("DIRE");
        rightLabel.setTextFill(Color.web("#ef4444"));
    }
    
    private void rebuildSlots() {
        slotsBox.getChildren().clear();
        slots.clear();
        rows.clear();
        buildSlots();
        // Delay rescale until after layout pass
        javafx.application.Platform.runLater(this::rescale);
    }
    
    private String formatPhase(DraftPhase phase) {
        return switch (phase) {
            case BAN_1 -> "BAN 1";
            case PICK_1 -> "PICK 1";
            case BAN_2 -> "BAN 2";
            case PICK_2 -> "PICK 2";
            case BAN_3 -> "BAN 3";
            case PICK_3 -> "PICK 3";
            case COMPLETED -> "DONE";
        };
    }
    
    private static final List<TurnDef> SEQUENCE = List.of(
        new TurnDef(true, false), new TurnDef(false, false), new TurnDef(false, false),
        new TurnDef(true, false), new TurnDef(false, false), new TurnDef(false, false),
        new TurnDef(true, false),
        new TurnDef(true, true), new TurnDef(false, true),
        new TurnDef(true, false), new TurnDef(true, false), new TurnDef(false, false),
        new TurnDef(false, true), new TurnDef(true, true), new TurnDef(true, true),
        new TurnDef(false, true), new TurnDef(false, true), new TurnDef(true, true),
        new TurnDef(true, false), new TurnDef(false, false),
        new TurnDef(false, false), new TurnDef(true, false),
        new TurnDef(true, true), new TurnDef(false, true)
    );
    
    private record TurnDef(boolean isRadiant, boolean isPick) {}
}
