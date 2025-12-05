package com.dota2assistant.domain.recommendation;

import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.repository.SynergyRepository;

import java.util.List;

/**
 * Scores heroes based on how well they counter enemies.
 */
public class CounterScorer {
    
    private final SynergyRepository synergyRepository;
    
    public CounterScorer(SynergyRepository synergyRepository) {
        this.synergyRepository = synergyRepository;
    }
    
    /**
     * Calculate counter score for a hero against enemies.
     * @param hero Candidate hero
     * @param enemies Current enemy heroes
     * @return Score component (0.0-1.0, higher means hero counters enemies better)
     */
    public ScoreComponent score(Hero hero, List<Hero> enemies) {
        if (enemies.isEmpty()) {
            return ScoreComponent.counter(0.5, "No enemies yet");
        }
        
        List<Integer> enemyIds = enemies.stream().map(Hero::id).toList();
        double avgCounter = synergyRepository.calculateAverageCounter(hero.id(), enemyIds);
        
        String description = formatDescription(avgCounter, enemies);
        return ScoreComponent.counter(avgCounter, description);
    }
    
    private String formatDescription(double score, List<Hero> enemies) {
        if (score >= 0.7) {
            return "Strong counter to " + enemies.size() + " enemies";
        } else if (score >= 0.5) {
            return "Neutral matchup against enemies";
        } else {
            return "Countered by enemy picks";
        }
    }
}

