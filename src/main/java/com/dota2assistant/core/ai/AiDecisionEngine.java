package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Hero;

import java.util.List;
import java.util.Map;

public interface AiDecisionEngine {
    
    /**
     * Suggests a hero to pick based on the current draft state.
     *
     * @param radiantPicks The heroes picked by the Radiant team
     * @param direPicks The heroes picked by the Dire team
     * @param bannedHeroes The banned heroes
     * @return The suggested hero to pick
     */
    Hero suggestPick(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes);
    
    /**
     * Suggests multiple heroes to pick based on the current draft state.
     *
     * @param radiantPicks The heroes picked by the Radiant team
     * @param direPicks The heroes picked by the Dire team
     * @param bannedHeroes The banned heroes
     * @param count The number of hero suggestions to return
     * @return A list of suggested heroes to pick
     */
    List<Hero> suggestPicks(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes, int count);
    
    /**
     * Suggests a hero to ban based on the current draft state.
     *
     * @param radiantPicks The heroes picked by the Radiant team
     * @param direPicks The heroes picked by the Dire team
     * @param bannedHeroes The banned heroes
     * @return The suggested hero to ban
     */
    Hero suggestBan(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes);
    
    /**
     * Suggests multiple heroes to ban based on the current draft state.
     *
     * @param radiantPicks The heroes picked by the Radiant team
     * @param direPicks The heroes picked by the Dire team
     * @param bannedHeroes The banned heroes
     * @param count The number of ban suggestions to return
     * @return A list of suggested heroes to ban
     */
    List<Hero> suggestBans(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes, int count);
    
    /**
     * Get detailed reasoning for a hero recommendation
     * 
     * @param hero The hero to explain
     * @param radiantPicks Current Radiant picks
     * @param direPicks Current Dire picks
     * @return A map of reasoning categories and their explanations
     */
    default Map<String, String> getHeroRecommendationReasoning(Hero hero, List<Hero> radiantPicks, List<Hero> direPicks) {
        return Map.of("Default", "No detailed reasoning available for this hero recommendation");
    }
    
    /**
     * Set the difficulty level of the AI
     * 
     * @param difficultyLevel A value between 0.0 (random) and 1.0 (optimal)
     */
    default void setDifficultyLevel(double difficultyLevel) {
        // Default implementation does nothing
    }
    
    /**
     * Gets all available heroes (not picked or banned)
     * 
     * @return List of available heroes
     */
    default List<Hero> getAvailableHeroes() {
        // Default implementation returns empty list
        return List.of();
    }
}