package com.dota2assistant.core.ai;

import com.dota2assistant.core.analysis.DraftAnalysisService;
import com.dota2assistant.core.analysis.HeroRecommendation;
import com.dota2assistant.core.draft.DraftState;
import com.dota2assistant.core.draft.Team;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced AI decision engine that uses professional match data for decisions.
 * This implementation leverages the draft analysis service which processes
 * real professional match data from OpenDota API.
 */
@Service
public class ProMatchDataAiDecisionEngine implements AiDecisionEngine {

    private static final Logger logger = LoggerFactory.getLogger(ProMatchDataAiDecisionEngine.class);
    
    private final HeroRepository heroRepository;
    private final DraftAnalysisService draftAnalysisService;
    private final Random random = new Random();
    
    // AI difficulty level from 0.0 (random) to 1.0 (optimal)
    private double difficultyLevel = 0.8;
    
    /**
     * Constructor for the ProMatchDataAiDecisionEngine
     * 
     * @param heroRepository The repository for hero data
     * @param draftAnalysisService The service for analyzing draft data
     */
    @Autowired
    public ProMatchDataAiDecisionEngine(HeroRepository heroRepository, DraftAnalysisService draftAnalysisService) {
        this.heroRepository = heroRepository;
        this.draftAnalysisService = draftAnalysisService;
    }
    
    /**
     * Sets the difficulty level of the AI
     * 
     * @param difficultyLevel A value between 0.0 (random) and 1.0 (optimal)
     */
    public void setDifficultyLevel(double difficultyLevel) {
        if (difficultyLevel < 0.0 || difficultyLevel > 1.0) {
            throw new IllegalArgumentException("Difficulty level must be between 0.0 and 1.0");
        }
        this.difficultyLevel = difficultyLevel;
    }

    @Override
    public Hero suggestPick(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes) {
        // This implementation assumes AI plays as Dire team
        
        // If difficultyLevel is very low, make a random pick
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return null;
        }
        
        if (difficultyLevel < 0.2 || random.nextDouble() > difficultyLevel) {
            return available.get(random.nextInt(available.size()));
        }
        
        // Convert to DraftState
        DraftState draftState = convertToDraftState(radiantPicks, direPicks, bannedHeroes);
        
        // Get recommendations from analysis service
        List<HeroRecommendation> recommendations = 
                draftAnalysisService.getPickRecommendations(draftState, Team.DIRE, 10);
        
        if (recommendations.isEmpty()) {
            // Fallback to random selection if no recommendations
            logger.warn("No pick recommendations available, using random selection");
            return available.get(random.nextInt(available.size()));
        }
        
        // Choose one of the top heroes based on difficulty level
        int maxIndex = Math.min(5, recommendations.size()) - 1;
        int chosenIndex = (int) (maxIndex * (1.0 - difficultyLevel));
        
        Hero chosen = recommendations.get(chosenIndex).getHero();
        logger.info("AI suggests picking {} (score: {})", chosen.getLocalizedName(), 
                recommendations.get(chosenIndex).getScore());
                
        return chosen;
    }

    @Override
    public List<Hero> suggestPicks(List<Hero> radiantPicks, List<Hero> direPicks, 
                                List<Hero> bannedHeroes, int count) {
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Convert to DraftState
        DraftState draftState = convertToDraftState(radiantPicks, direPicks, bannedHeroes);
        
        // Get recommendations from analysis service
        List<HeroRecommendation> recommendations = 
                draftAnalysisService.getPickRecommendations(draftState, Team.DIRE, count);
        
        if (recommendations.isEmpty()) {
            // Fallback to random selection if no recommendations
            logger.warn("No pick recommendations available, using random selection");
            Collections.shuffle(available);
            return available.stream().limit(count).collect(Collectors.toList());
        }
        
        // Extract heroes from recommendations
        return recommendations.stream()
                .map(HeroRecommendation::getHero)
                .collect(Collectors.toList());
    }

    @Override
    public Hero suggestBan(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes) {
        // This implementation assumes AI plays as Dire team
        
        // If difficultyLevel is very low, make a random ban
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return null;
        }
        
        if (difficultyLevel < 0.2 || random.nextDouble() > difficultyLevel) {
            return available.get(random.nextInt(available.size()));
        }
        
        // Convert to DraftState
        DraftState draftState = convertToDraftState(radiantPicks, direPicks, bannedHeroes);
        
        // Get recommendations from analysis service
        List<HeroRecommendation> recommendations = 
                draftAnalysisService.getBanRecommendations(draftState, Team.DIRE, 10);
        
        if (recommendations.isEmpty()) {
            // Fallback to random selection if no recommendations
            logger.warn("No ban recommendations available, using random selection");
            return available.get(random.nextInt(available.size()));
        }
        
        // Choose one of the top heroes based on difficulty level
        int maxIndex = Math.min(5, recommendations.size()) - 1;
        int chosenIndex = (int) (maxIndex * (1.0 - difficultyLevel));
        
        Hero chosen = recommendations.get(chosenIndex).getHero();
        logger.info("AI suggests banning {} (score: {})", chosen.getLocalizedName(), 
                recommendations.get(chosenIndex).getScore());
                
        return chosen;
    }

    @Override
    public List<Hero> suggestBans(List<Hero> radiantPicks, List<Hero> direPicks, 
                               List<Hero> bannedHeroes, int count) {
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Convert to DraftState
        DraftState draftState = convertToDraftState(radiantPicks, direPicks, bannedHeroes);
        
        // Get recommendations from analysis service
        List<HeroRecommendation> recommendations = 
                draftAnalysisService.getBanRecommendations(draftState, Team.DIRE, count);
        
        if (recommendations.isEmpty()) {
            // Fallback to random selection if no recommendations
            logger.warn("No ban recommendations available, using random selection");
            Collections.shuffle(available);
            return available.stream().limit(count).collect(Collectors.toList());
        }
        
        // Extract heroes from recommendations
        return recommendations.stream()
                .map(HeroRecommendation::getHero)
                .collect(Collectors.toList());
    }
    
    /**
     * Helper method to get all available heroes
     */
    private List<Hero> getAvailableHeroes(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes) {
        // Get all heroes
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        // Create a set of unavailable hero IDs
        Set<Integer> unavailableIds = new HashSet<>();
        
        for (Hero hero : radiantPicks) {
            unavailableIds.add(hero.getId());
        }
        
        for (Hero hero : direPicks) {
            unavailableIds.add(hero.getId());
        }
        
        for (Hero hero : bannedHeroes) {
            unavailableIds.add(hero.getId());
        }
        
        // Filter the heroes
        return allHeroes.stream()
                .filter(hero -> !unavailableIds.contains(hero.getId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Convert hero lists to a DraftState object
     */
    private DraftState convertToDraftState(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes) {
        DraftState draftState = new DraftState();
        
        // Add radiant picks
        for (Hero hero : radiantPicks) {
            draftState.addRadiantPick(hero);
        }
        
        // Add dire picks
        for (Hero hero : direPicks) {
            draftState.addDirePick(hero);
        }
        
        // Add bans
        for (Hero hero : bannedHeroes) {
            draftState.addBannedHero(hero);
        }
        
        return draftState;
    }
}