package com.dota2assistant.core.analysis;

import com.dota2assistant.data.model.Hero;

import java.util.List;

public interface AnalysisEngine {
    
    /**
     * Calculates the strength of a team based on its heroes.
     *
     * @param teamPicks The heroes picked by the team
     * @return A value between 0 and 1 representing the team's strength
     */
    double calculateTeamStrength(List<Hero> teamPicks);
    
    /**
     * Analyzes a draft and provides insights about both teams.
     *
     * @param radiantPicks The heroes picked by the Radiant team
     * @param direPicks The heroes picked by the Dire team
     * @return A string containing the analysis
     */
    String analyzeDraft(List<Hero> radiantPicks, List<Hero> direPicks);
    
    /**
     * Provides a summary of the draft.
     *
     * @param radiantPicks The heroes picked by the Radiant team
     * @param direPicks The heroes picked by the Dire team
     * @return A summary of the draft
     */
    String getDraftSummary(List<Hero> radiantPicks, List<Hero> direPicks);
    
    /**
     * Identifies synergies within a team.
     *
     * @param teamPicks The heroes picked by the team
     * @return A list of synergy descriptions
     */
    List<String> identifyTeamSynergies(List<Hero> teamPicks);
    
    /**
     * Identifies counters between two teams.
     *
     * @param team1Picks The heroes picked by the first team
     * @param team2Picks The heroes picked by the second team
     * @return A list of counter descriptions
     */
    List<String> identifyCounters(List<Hero> team1Picks, List<Hero> team2Picks);
    
    /**
     * Analyzes the damage types of a team.
     *
     * @param teamPicks The heroes picked by the team
     * @return A description of the team's damage types
     */
    String analyzeDamageTypes(List<Hero> teamPicks);
    
    /**
     * Analyzes the timing windows of a team.
     *
     * @param teamPicks The heroes picked by the team
     * @return A description of the team's timing windows
     */
    String analyzeTimingWindows(List<Hero> teamPicks);
    
    /**
     * Predicts the win probability for each team.
     *
     * @param radiantPicks The heroes picked by the Radiant team
     * @param direPicks The heroes picked by the Dire team
     * @return A value between 0 and 1 representing Radiant's win probability
     */
    double predictWinProbability(List<Hero> radiantPicks, List<Hero> direPicks);
    
    /**
     * Calculates synergy score between a hero and a team
     * 
     * @param hero The hero to evaluate
     * @param team The team to calculate synergy with
     * @return A value between 0 and 1 representing synergy score
     */
    default double calculateSynergy(Hero hero, List<Hero> team) {
        return 0.6; // Default implementation
    }
    
    /**
     * Calculates counter score between a hero and an enemy team
     * 
     * @param hero The hero to evaluate
     * @param enemyTeam The opposing team to calculate counter effectiveness against
     * @return A value between 0 and 1 representing counter effectiveness
     */
    default double calculateCounter(Hero hero, List<Hero> enemyTeam) {
        return 0.6; // Default implementation
    }
    
    /**
     * Calculates synergy score between two specific heroes
     * 
     * @param hero1 First hero
     * @param hero2 Second hero
     * @return A value between 0 and 1 representing synergy score
     */
    default double calculateHeroSynergy(Hero hero1, Hero hero2) {
        if (hero1 == null || hero2 == null) {
            return 0.5;
        }
        
        // Generate values between 0.4 and 0.8 based on hero ids
        // This is just for demonstration until real data is used
        int combinedId = hero1.getId() + hero2.getId();
        return 0.4 + (combinedId % 5) * 0.1;
    }
    
    /**
     * Calculates counter effectiveness of one hero against another
     * 
     * @param hero1 Hero doing the countering
     * @param hero2 Hero being countered
     * @return A value between 0 and 1 representing counter effectiveness
     */
    default double calculateHeroCounter(Hero hero1, Hero hero2) {
        if (hero1 == null || hero2 == null) {
            return 0.5;
        }
        
        // Generate values between 0.4 and 0.8 based on hero ids
        // This is just for demonstration until real data is used
        int factor = (hero1.getId() * 3) % hero2.getId();
        if (factor == 0) factor = 1;
        return 0.4 + (factor % 5) * 0.1;
    }
    
    /**
     * Gets win rate for a hero
     * 
     * @param hero The hero to get win rate for
     * @return Win rate between 0 and 1, or -1 if no data available
     */
    default double getHeroWinRate(Hero hero) {
        // Return a more varied win rate based on hero id for testing
        if (hero == null || hero.getId() <= 0) {
            return 0.5;
        }
        
        // Generate a win rate between 45% and 55% based on hero id
        // This is just for demonstration purposes until real data is used
        return 0.45 + (hero.getId() % 10) * 0.01;
    }
    
    /**
     * Gets pick count for a hero
     * 
     * @param hero The hero to get pick count for
     * @return Number of times the hero was picked in analyzed matches
     */
    default int getHeroPickCount(Hero hero) {
        return 10; // Default implementation
    }
}