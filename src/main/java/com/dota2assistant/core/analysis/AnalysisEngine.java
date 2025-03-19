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
}