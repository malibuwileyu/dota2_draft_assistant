package com.dota2assistant.gsi;

import com.dota2assistant.core.draft.DraftRecommendationService;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.PlayerHeroPerformance;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.data.service.PlayerRecommendationService;
import com.dota2assistant.gsi.model.DraftState;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service that integrates GSI draft state with hero recommendations.
 */
@Service
public class GsiDraftRecommendationService implements GsiStateManager.DraftUpdateListener {
    private static final Logger logger = LoggerFactory.getLogger(GsiDraftRecommendationService.class);
    
    private final GsiStateManager gsiStateManager;
    private final HeroRepository heroRepository;
    private final DraftRecommendationService draftRecommendationService;
    private final PlayerRecommendationService playerRecommendationService;
    
    private final BooleanProperty draftActive = new SimpleBooleanProperty(false);
    
    // Observable lists for picks and bans (for binding to UI)
    private final ListProperty<Hero> radiantPicksProperty = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ListProperty<Hero> direPicksProperty = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ListProperty<Hero> radiantBansProperty = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ListProperty<Hero> direBansProperty = new SimpleListProperty<>(FXCollections.observableArrayList());
    
    // Recommended heroes based on the current draft state
    private final ListProperty<Hero> recommendedHeroesProperty = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ListProperty<PlayerHeroPerformance> personalRecommendationsProperty = 
            new SimpleListProperty<>(FXCollections.observableArrayList());
    
    // Current player's Steam ID (if available)
    private Long currentPlayerSteamId;
    
    @Autowired
    public GsiDraftRecommendationService(
            GsiStateManager gsiStateManager,
            HeroRepository heroRepository,
            DraftRecommendationService draftRecommendationService,
            PlayerRecommendationService playerRecommendationService) {
            
        this.gsiStateManager = gsiStateManager;
        this.heroRepository = heroRepository;
        this.draftRecommendationService = draftRecommendationService;
        this.playerRecommendationService = playerRecommendationService;
        
        // Register as a draft listener
        gsiStateManager.addDraftListener(this);
    }
    
    /**
     * Sets the current player's Steam ID for personalized recommendations.
     * 
     * @param steamId The player's Steam ID
     */
    public void setCurrentPlayerSteamId(Long steamId) {
        this.currentPlayerSteamId = steamId;
        updateRecommendations();
    }
    
    @Override
    public void onDraftStarted(DraftState draftState) {
        logger.info("Draft started!");
        
        // Update the draft active state
        Platform.runLater(() -> draftActive.set(true));
        
        // Process the initial draft state
        processDraftState(draftState);
    }
    
    @Override
    public void onDraftUpdated(DraftState draftState) {
        logger.info("Draft updated: {} picks, {} bans", 
                draftState.getAllPickedHeroIds().size(), 
                draftState.getAllBannedHeroIds().size());
                
        // Process the updated draft state
        processDraftState(draftState);
        
        // If draft is complete, mark as inactive
        if (draftState.isDraftComplete()) {
            Platform.runLater(() -> draftActive.set(false));
        }
    }
    
    /**
     * Processes the draft state and updates recommendations.
     * 
     * @param draftState The current draft state
     */
    private void processDraftState(DraftState draftState) {
        // Convert hero IDs to Hero objects
        List<Hero> radiantPicks = convertHeroIds(draftState.getRadiantPicks().stream()
                .map(pick -> pick.getHeroId())
                .collect(Collectors.toList()));
                
        List<Hero> direPicks = convertHeroIds(draftState.getDirePicks().stream()
                .map(pick -> pick.getHeroId())
                .collect(Collectors.toList()));
                
        List<Hero> radiantBans = convertHeroIds(draftState.getRadiantBans().stream()
                .map(ban -> ban.getHeroId())
                .collect(Collectors.toList()));
                
        List<Hero> direBans = convertHeroIds(draftState.getDireBans().stream()
                .map(ban -> ban.getHeroId())
                .collect(Collectors.toList()));
        
        // Update the observable lists on the JavaFX thread
        Platform.runLater(() -> {
            radiantPicksProperty.setAll(radiantPicks);
            direPicksProperty.setAll(direPicks);
            radiantBansProperty.setAll(radiantBans);
            direBansProperty.setAll(direBans);
        });
        
        // Update recommendations
        updateRecommendations();
    }
    
    /**
     * Updates hero recommendations based on the current draft state.
     */
    private void updateRecommendations() {
        // Get the current draft state
        DraftState draftState = gsiStateManager.getDraftState();
        if (draftState == null) {
            return;
        }
        
        // Get the player's team (radiant or dire)
        boolean isRadiant = true; // Default to radiant if we can't determine
        if (gsiStateManager.getCurrentState() != null && 
            gsiStateManager.getCurrentState().getPlayerState() != null) {
            isRadiant = gsiStateManager.getCurrentState().getPlayerState().isRadiant();
        }
        
        // Get already picked heroes
        List<Hero> allyPicks = isRadiant ? 
                radiantPicksProperty.get() : 
                direPicksProperty.get();
                
        List<Hero> enemyPicks = isRadiant ? 
                direPicksProperty.get() : 
                radiantPicksProperty.get();
        
        // Get banned heroes
        List<Hero> allBans = new ArrayList<>();
        allBans.addAll(radiantBansProperty.get());
        allBans.addAll(direBansProperty.get());
        
        // Get unavailable heroes (all picked and banned heroes)
        Set<Integer> unavailableHeroIds = new HashSet<>();
        allyPicks.forEach(hero -> unavailableHeroIds.add(hero.getId()));
        enemyPicks.forEach(hero -> unavailableHeroIds.add(hero.getId()));
        allBans.forEach(hero -> unavailableHeroIds.add(hero.getId()));
        
        // Get general recommendations based on draft state
        List<Hero> generalRecommendations = draftRecommendationService.getRecommendedHeroes(
                allyPicks, 
                enemyPicks, 
                unavailableHeroIds,
                20 // Limit to 20 recommendations
        );
        
        // Get personalized recommendations if we have a Steam ID
        List<PlayerHeroPerformance> personalRecommendations = new ArrayList<>();
        if (currentPlayerSteamId != null) {
            try {
                // Get player hero performance data
                List<PlayerHeroPerformance> playerHeroes = playerRecommendationService.getRecommendedHeroes(
                        currentPlayerSteamId,
                        50,  // Get a larger number to filter down
                        true // Consider comfort picks
                );
                
                // Filter out unavailable heroes
                personalRecommendations = playerHeroes.stream()
                        .filter(perf -> !unavailableHeroIds.contains(perf.getHero().getId()))
                        .limit(10) // Limit to 10 personal recommendations
                        .collect(Collectors.toList());
            } catch (Exception e) {
                logger.error("Error getting personal recommendations", e);
            }
        }
        
        // Update the observable lists on the JavaFX thread
        final List<PlayerHeroPerformance> finalPersonalRecommendations = personalRecommendations;
        Platform.runLater(() -> {
            recommendedHeroesProperty.setAll(generalRecommendations);
            personalRecommendationsProperty.setAll(finalPersonalRecommendations);
        });
    }
    
    /**
     * Converts a list of hero IDs to a list of Hero objects.
     * 
     * @param heroIds The list of hero IDs
     * @return A list of Hero objects
     */
    private List<Hero> convertHeroIds(List<Integer> heroIds) {
        return heroIds.stream()
                .map(heroRepository::getHeroById)
                .filter(hero -> hero != null)
                .collect(Collectors.toList());
    }
    
    /**
     * @return The draft active property
     */
    public BooleanProperty draftActiveProperty() {
        return draftActive;
    }
    
    /**
     * @return true if a draft is currently active
     */
    public boolean isDraftActive() {
        return draftActive.get();
    }
    
    /**
     * @return The Radiant picks property
     */
    public ListProperty<Hero> radiantPicksProperty() {
        return radiantPicksProperty;
    }
    
    /**
     * @return The Dire picks property
     */
    public ListProperty<Hero> direPicksProperty() {
        return direPicksProperty;
    }
    
    /**
     * @return The Radiant bans property
     */
    public ListProperty<Hero> radiantBansProperty() {
        return radiantBansProperty;
    }
    
    /**
     * @return The Dire bans property
     */
    public ListProperty<Hero> direBansProperty() {
        return direBansProperty;
    }
    
    /**
     * @return The recommended heroes property
     */
    public ListProperty<Hero> recommendedHeroesProperty() {
        return recommendedHeroesProperty;
    }
    
    /**
     * @return The personal recommendations property
     */
    public ListProperty<PlayerHeroPerformance> personalRecommendationsProperty() {
        return personalRecommendationsProperty;
    }
}