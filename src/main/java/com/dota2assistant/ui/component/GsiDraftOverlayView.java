package com.dota2assistant.ui.component;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.PlayerHeroPerformance;
import com.dota2assistant.gsi.GsiDraftRecommendationService;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * UI component for displaying the GSI draft overlay.
 * Shows live draft state and hero recommendations.
 */
@Component
public class GsiDraftOverlayView extends BorderPane {
    
    private final GsiDraftRecommendationService gsiDraftRecommendationService;
    private final BooleanProperty gsiSetupComplete = new SimpleBooleanProperty(false);
    
    // UI components
    private final VBox draftStateContainer = new VBox(10);
    private final HBox teamPicks = new HBox(5);
    private final FlowPane recommendationsContainer = new FlowPane(10, 10);
    private final VBox personalRecommendationsContainer = new VBox(5);
    
    @Autowired
    public GsiDraftOverlayView(GsiDraftRecommendationService gsiDraftRecommendationService) {
        this.gsiDraftRecommendationService = gsiDraftRecommendationService;
        initialize();
    }
    
    /**
     * Initializes the GSI draft overlay.
     */
    private void initialize() {
        setPadding(new Insets(20));
        
        // Setup draft state display
        Label draftStatusLabel = new Label("Draft Status");
        draftStatusLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        
        // Team picks display
        teamPicks.setAlignment(Pos.CENTER);
        teamPicks.setPadding(new Insets(10));
        
        // Create containers for each team's picks
        HBox radiantPicksBox = new HBox(5);
        HBox direPicksBox = new HBox(5);
        
        Label vsLabel = new Label("VS");
        vsLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        vsLabel.setPadding(new Insets(0, 15, 0, 15));
        
        teamPicks.getChildren().addAll(radiantPicksBox, vsLabel, direPicksBox);
        
        // Recommendations container
        Label recommendationsLabel = new Label("Recommended Heroes");
        recommendationsLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        recommendationsContainer.setPadding(new Insets(10));
        recommendationsContainer.setPrefWrapLength(600);
        
        // Personal recommendations container
        Label personalRecsLabel = new Label("Your Best Heroes");
        personalRecsLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        ListView<PlayerHeroPerformance> personalRecsList = new ListView<>();
        personalRecsList.setCellFactory(lv -> new PlayerHeroPerformanceCell());
        personalRecsList.setPrefHeight(200);
        
        personalRecommendationsContainer.getChildren().addAll(personalRecsLabel, personalRecsList);
        
        // GSI status info
        TextFlow gsiStatusFlow = new TextFlow();
        Text gsiStatusText = new Text("GSI Status: ");
        gsiStatusText.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        Text gsiStatusValue = new Text("Not Connected");
        gsiStatusValue.setFill(Color.RED);
        gsiStatusValue.setFont(Font.font("System", 12));
        
        gsiStatusFlow.getChildren().addAll(gsiStatusText, gsiStatusValue);
        
        // Add all components to the draft state container
        draftStateContainer.getChildren().addAll(
                draftStatusLabel, 
                teamPicks,
                recommendationsLabel, 
                recommendationsContainer,
                personalRecommendationsContainer,
                gsiStatusFlow
        );
        
        setCenter(draftStateContainer);
        
        // Bind to the GSI draft state
        bindDraftState();
        
        // Update GSI status text based on draft active state
        gsiDraftRecommendationService.draftActiveProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                gsiStatusValue.setText("Connected (Draft Active)");
                gsiStatusValue.setFill(Color.GREEN);
                gsiSetupComplete.set(true);
            } else {
                gsiStatusValue.setText("Connected (Waiting for Draft)");
                gsiStatusValue.setFill(Color.ORANGE);
                gsiSetupComplete.set(true);
            }
        });
    }
    
    /**
     * Binds the UI to the GSI draft state.
     */
    private void bindDraftState() {
        // Bind team picks
        HBox radiantPicksBox = (HBox) teamPicks.getChildren().get(0);
        radiantPicksBox.getChildren().clear();
        
        // Use listener for radiant picks to update UI
        gsiDraftRecommendationService.radiantPicksProperty().addListener((obs, oldList, newList) -> {
            updateTeamPicksDisplay(radiantPicksBox, newList);
        });
        
        HBox direPicksBox = (HBox) teamPicks.getChildren().get(2);
        direPicksBox.getChildren().clear();
        
        // Use listener for dire picks to update UI
        gsiDraftRecommendationService.direPicksProperty().addListener((obs, oldList, newList) -> {
            updateTeamPicksDisplay(direPicksBox, newList);
        });
        
        // Bind recommended heroes
        gsiDraftRecommendationService.recommendedHeroesProperty().addListener((obs, oldList, newList) -> {
            updateRecommendationsDisplay(recommendationsContainer, newList);
        });
        
        // Bind personal recommendations
        ListView<PlayerHeroPerformance> personalRecsList = (ListView<PlayerHeroPerformance>)
                personalRecommendationsContainer.getChildren().get(1);
        
        Bindings.bindContent(
                personalRecsList.getItems(), 
                gsiDraftRecommendationService.personalRecommendationsProperty()
        );
    }
    
    /**
     * Updates the team picks display.
     * 
     * @param container The container to update
     * @param heroes The list of heroes to display
     */
    private void updateTeamPicksDisplay(HBox container, List<Hero> heroes) {
        container.getChildren().clear();
        
        for (Hero hero : heroes) {
            ImageView heroImage = createHeroImageView(hero);
            container.getChildren().add(heroImage);
        }
        
        // Add placeholder images for remaining picks (up to 5)
        int remainingPicks = 5 - heroes.size();
        for (int i = 0; i < remainingPicks; i++) {
            ImageView placeholder = createPlaceholderImageView();
            container.getChildren().add(placeholder);
        }
    }
    
    /**
     * Updates the recommendations display.
     * 
     * @param container The container to update
     * @param heroes The list of heroes to display
     */
    private void updateRecommendationsDisplay(FlowPane container, List<Hero> heroes) {
        container.getChildren().clear();
        
        for (Hero hero : heroes) {
            VBox heroBox = new VBox(2);
            heroBox.setAlignment(Pos.CENTER);
            
            ImageView heroImage = createHeroImageView(hero);
            Label heroName = new Label(hero.getLocalizedName());
            heroName.setMaxWidth(60);
            heroName.setWrapText(true);
            heroName.setAlignment(Pos.CENTER);
            heroName.setStyle("-fx-font-size: 10px;");
            
            heroBox.getChildren().addAll(heroImage, heroName);
            container.getChildren().add(heroBox);
        }
    }
    
    /**
     * Creates a hero image view.
     * 
     * @param hero The hero to display
     * @return An ImageView for the hero
     */
    private ImageView createHeroImageView(Hero hero) {
        String imagePath = "/images/heroes/" + hero.getId() + ".png";
        Image heroImage = new Image(getClass().getResourceAsStream(imagePath), 
                                  64, 36, true, true);
        ImageView imageView = new ImageView(heroImage);
        return imageView;
    }
    
    /**
     * Creates a placeholder image view.
     * 
     * @return A placeholder ImageView
     */
    private ImageView createPlaceholderImageView() {
        String imagePath = "/images/placeholder.png";
        Image placeholderImage = new Image(getClass().getResourceAsStream(imagePath), 
                                        64, 36, true, true);
        ImageView imageView = new ImageView(placeholderImage);
        return imageView;
    }
    
    /**
     * Custom cell for displaying player hero performance.
     */
    private static class PlayerHeroPerformanceCell extends javafx.scene.control.ListCell<PlayerHeroPerformance> {
        private final HBox container = new HBox(10);
        private final ImageView heroImage = new ImageView();
        private final VBox detailsBox = new VBox(2);
        private final Label heroName = new Label();
        private final Label statsLabel = new Label();
        
        public PlayerHeroPerformanceCell() {
            heroImage.setFitWidth(48);
            heroImage.setFitHeight(27);
            heroName.setFont(Font.font("System", FontWeight.BOLD, 12));
            statsLabel.setFont(Font.font("System", 11));
            
            detailsBox.getChildren().addAll(heroName, statsLabel);
            container.getChildren().addAll(heroImage, detailsBox);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(detailsBox, Priority.ALWAYS);
        }
        
        @Override
        protected void updateItem(PlayerHeroPerformance item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
            } else {
                heroName.setText(item.getHero().getLocalizedName());
                statsLabel.setText(String.format("Matches: %d | Win Rate: %s", 
                                              item.getMatches(), 
                                              item.getWinRateFormatted()));
                
                // Load hero image
                String imagePath = "/images/heroes/" + item.getHero().getId() + ".png";
                heroImage.setImage(new Image(getClass().getResourceAsStream(imagePath)));
                
                setGraphic(container);
            }
        }
    }
    
    public BooleanProperty gsiSetupCompleteProperty() {
        return gsiSetupComplete;
    }
    
    public boolean isGsiSetupComplete() {
        return gsiSetupComplete.get();
    }
}