package com.dota2assistant.domain.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for hero synergy and counter data.
 * Synergy = how well two heroes work together on the same team.
 * Counter = how well a hero performs against another hero.
 */
public interface SynergyRepository {
    
    /**
     * Get synergy score between two heroes on the same team.
     * Score ranges from 0.0 (bad synergy) to 1.0 (excellent synergy).
     * 0.5 is neutral/no data.
     *
     * @param heroId First hero ID
     * @param allyId Second hero ID (ally)
     * @return Synergy score, or empty if no data
     */
    Optional<Double> getSynergyScore(int heroId, int allyId);
    
    /**
     * Get counter score for a hero against an enemy.
     * Score ranges from 0.0 (hero is countered) to 1.0 (hero counters enemy).
     * 0.5 is neutral/no data.
     *
     * @param heroId Hero ID
     * @param enemyId Enemy hero ID
     * @return Counter score, or empty if no data
     */
    Optional<Double> getCounterScore(int heroId, int enemyId);
    
    /**
     * Get all synergy scores for a hero with potential allies.
     *
     * @param heroId Hero ID
     * @return Map of ally hero ID to synergy score
     */
    Map<Integer, Double> getAllSynergies(int heroId);
    
    /**
     * Get all counter scores for a hero against enemies.
     *
     * @param heroId Hero ID
     * @return Map of enemy hero ID to counter score
     */
    Map<Integer, Double> getAllCounters(int heroId);
    
    /**
     * Get best synergy partners for a hero.
     *
     * @param heroId Hero ID
     * @param limit Maximum number of results
     * @return List of ally hero IDs sorted by synergy score (best first)
     */
    List<Integer> getBestSynergies(int heroId, int limit);
    
    /**
     * Get heroes that this hero counters well.
     *
     * @param heroId Hero ID
     * @param limit Maximum number of results
     * @return List of enemy hero IDs that this hero counters (best matchups first)
     */
    List<Integer> getBestCounters(int heroId, int limit);
    
    /**
     * Get heroes that counter this hero.
     *
     * @param heroId Hero ID
     * @param limit Maximum number of results
     * @return List of enemy hero IDs that counter this hero (worst matchups first)
     */
    List<Integer> getCounteredBy(int heroId, int limit);
    
    /**
     * Calculate average synergy score for a hero with a list of allies.
     *
     * @param heroId Hero ID
     * @param allyIds List of ally hero IDs
     * @return Average synergy score (0.5 if no data)
     */
    default double calculateAverageSynergy(int heroId, List<Integer> allyIds) {
        if (allyIds.isEmpty()) return 0.5;
        
        double sum = 0.0;
        int count = 0;
        for (int allyId : allyIds) {
            Optional<Double> score = getSynergyScore(heroId, allyId);
            if (score.isPresent()) {
                sum += score.get();
                count++;
            }
        }
        return count > 0 ? sum / count : 0.5;
    }
    
    /**
     * Calculate average counter score for a hero against a list of enemies.
     *
     * @param heroId Hero ID
     * @param enemyIds List of enemy hero IDs
     * @return Average counter score (0.5 if no data)
     */
    default double calculateAverageCounter(int heroId, List<Integer> enemyIds) {
        if (enemyIds.isEmpty()) return 0.5;
        
        double sum = 0.0;
        int count = 0;
        for (int enemyId : enemyIds) {
            Optional<Double> score = getCounterScore(heroId, enemyId);
            if (score.isPresent()) {
                sum += score.get();
                count++;
            }
        }
        return count > 0 ? sum / count : 0.5;
    }
}

