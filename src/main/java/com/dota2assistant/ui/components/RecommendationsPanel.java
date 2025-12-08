package com.dota2assistant.ui.components;

import com.dota2assistant.infrastructure.api.BackendApiClient;
import com.dota2assistant.infrastructure.api.BackendApiClient.HeroRecommendation;
import com.dota2assistant.infrastructure.api.BackendApiClient.RecommendationResponse;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Panel displaying AI-powered hero recommendations during draft.
 * Shows top 5 recommended heroes with scores and optional explanations.
 */
public class RecommendationsPanel extends VBox {
    
    private static final int MAX_RECOMMENDATIONS = 5;
    private static final Map<String, Image> iconCache = new ConcurrentHashMap<>();
    
    private final VBox recommendationsList = new VBox(8);
    private final Label statusLabel = new Label("Waiting for draft...");
    private final ProgressIndicator loadingSpinner = new ProgressIndicator();
    private final Label winProbLabel = new Label();
    
    public RecommendationsPanel() {
        setSpacing(10);
        setPadding(new Insets(15));
        setMinWidth(220);
        setMaxWidth(250);
        setStyle("-fx-background-color: #1a1f2e; -fx-background-radius: 8;");
        
        // Header
        Label header = new Label("✨ Recommendations");
        header.setFont(Font.font("System", FontWeight.BOLD, 14));
        header.setTextFill(Color.web("#e5e7eb"));
        
        // Status/loading
        statusLabel.setFont(Font.font("System", 11));
        statusLabel.setTextFill(Color.web("#9ca3af"));
        statusLabel.setWrapText(true);
        
        loadingSpinner.setMaxSize(20, 20);
        loadingSpinner.setVisible(false);
        
        HBox statusBox = new HBox(8, loadingSpinner, statusLabel);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        
        // Win probability
        winProbLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        winProbLabel.setVisible(false);
        
        // Recommendations list
        recommendationsList.setPadding(new Insets(5, 0, 0, 0));
        
        getChildren().addAll(header, statusBox, winProbLabel, recommendationsList);
    }
    
    /**
     * Update with new recommendations from backend.
     */
    public void update(RecommendationResponse response) {
        Platform.runLater(() -> {
            loadingSpinner.setVisible(false);
            recommendationsList.getChildren().clear();
            
            if (response == null || response.recommendations().isEmpty()) {
                statusLabel.setText("No recommendations available");
                winProbLabel.setVisible(false);
                return;
            }
            
            statusLabel.setText(String.format("Top picks (%dms)", response.computeTimeMs()));
            
            // Win probability indicator
            double winProb = response.winProbability();
            winProbLabel.setText(String.format("Win chance: %.0f%%", winProb * 100));
            winProbLabel.setTextFill(getWinProbColor(winProb));
            winProbLabel.setVisible(true);
            
            // Add top recommendations
            List<HeroRecommendation> recs = response.recommendations();
            int count = Math.min(MAX_RECOMMENDATIONS, recs.size());
            
            for (int i = 0; i < count; i++) {
                HeroRecommendation rec = recs.get(i);
                recommendationsList.getChildren().add(createRecommendationRow(rec, i + 1));
            }
        });
    }
    
    /**
     * Show loading state while fetching recommendations.
     */
    public void showLoading() {
        Platform.runLater(() -> {
            loadingSpinner.setVisible(true);
            statusLabel.setText("Analyzing draft...");
            recommendationsList.getChildren().clear();
            winProbLabel.setVisible(false);
        });
    }
    
    /**
     * Show error state.
     */
    public void showError(String message) {
        Platform.runLater(() -> {
            loadingSpinner.setVisible(false);
            statusLabel.setText(message);
            recommendationsList.getChildren().clear();
            winProbLabel.setVisible(false);
        });
    }
    
    private HBox createRecommendationRow(HeroRecommendation rec, int rank) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-background-color: #252b3b; -fx-background-radius: 6;");
        
        // Rank indicator
        Label rankLabel = new Label("#" + rank);
        rankLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        rankLabel.setTextFill(getRankColor(rank));
        rankLabel.setMinWidth(25);
        
        // Hero icon
        ImageView icon = new ImageView();
        icon.setFitWidth(28);
        icon.setFitHeight(28);
        icon.setPreserveRatio(true);
        loadIcon(rec.iconUrl(), icon);
        
        // Hero name and score
        VBox info = new VBox(2);
        Label nameLabel = new Label(rec.heroName());
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        nameLabel.setTextFill(Color.web("#e5e7eb"));
        
        Label scoreLabel = new Label(String.format("%.0f%%", rec.totalScore() * 100));
        scoreLabel.setFont(Font.font("System", 10));
        scoreLabel.setTextFill(getScoreColor(rec.totalScore()));
        
        info.getChildren().addAll(nameLabel, scoreLabel);
        HBox.setHgrow(info, Priority.ALWAYS);
        
        row.getChildren().addAll(rankLabel, icon, info);
        
        // Tooltip with breakdown
        Tooltip tooltip = createBreakdownTooltip(rec);
        Tooltip.install(row, tooltip);
        
        // Hover effect
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #323a4d; -fx-background-radius: 6; -fx-cursor: hand;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color: #252b3b; -fx-background-radius: 6;"));
        
        return row;
    }
    
    private Tooltip createBreakdownTooltip(HeroRecommendation rec) {
        StringBuilder sb = new StringBuilder();
        sb.append(rec.heroName()).append("\n");
        sb.append("─────────────────\n");
        
        for (var breakdown : rec.breakdown()) {
            String emoji = switch (breakdown.type()) {
                case "synergy" -> "🤝";
                case "counter" -> "⚔️";
                case "role" -> "🎭";
                case "personal" -> "⭐";
                default -> "•";
            };
            sb.append(String.format("%s %s: %.0f%%\n", emoji, 
                capitalize(breakdown.type()), breakdown.value() * 100));
            sb.append("   ").append(breakdown.description()).append("\n");
        }
        
        if (rec.explanation() != null && !rec.explanation().isEmpty()) {
            sb.append("\n💡 ").append(rec.explanation());
        }
        
        Tooltip tooltip = new Tooltip(sb.toString());
        tooltip.setFont(Font.font("System", 11));
        tooltip.setShowDelay(Duration.millis(200));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(300);
        return tooltip;
    }
    
    private void loadIcon(String url, ImageView view) {
        if (url == null || url.isEmpty()) return;
        
        // Clean up URL (remove trailing ?)
        String cleanUrl = url.endsWith("?") ? url.substring(0, url.length() - 1) : url;
        
        Image cached = iconCache.get(cleanUrl);
        if (cached != null) {
            view.setImage(cached);
            return;
        }
        
        // Load async
        new Thread(() -> {
            try {
                Image img = new Image(cleanUrl, 28, 28, true, true);
                iconCache.put(cleanUrl, img);
                Platform.runLater(() -> view.setImage(img));
            } catch (Exception e) {
                // Ignore - just won't show icon
            }
        }).start();
    }
    
    private Color getRankColor(int rank) {
        return switch (rank) {
            case 1 -> Color.web("#fbbf24"); // Gold
            case 2 -> Color.web("#9ca3af"); // Silver
            case 3 -> Color.web("#cd7f32"); // Bronze
            default -> Color.web("#6b7280");
        };
    }
    
    private Color getScoreColor(double score) {
        if (score >= 0.7) return Color.web("#22c55e"); // Green
        if (score >= 0.55) return Color.web("#84cc16"); // Light green
        if (score >= 0.45) return Color.web("#eab308"); // Yellow
        return Color.web("#f97316"); // Orange
    }
    
    private Color getWinProbColor(double prob) {
        if (prob >= 0.6) return Color.web("#22c55e");
        if (prob >= 0.5) return Color.web("#84cc16");
        if (prob >= 0.4) return Color.web("#eab308");
        return Color.web("#ef4444");
    }
    
    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}

