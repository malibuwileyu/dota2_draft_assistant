package com.dota2assistant.domain.recommendation;

import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.repository.SynergyRepository;

import java.util.List;

/**
 * Scores heroes based on synergy with allies.
 */
public class SynergyScorer {
    
    private final SynergyRepository synergyRepository;
    
    public SynergyScorer(SynergyRepository synergyRepository) {
        this.synergyRepository = synergyRepository;
    }
    
    /**
     * Calculate synergy score for a hero with current allies.
     * @param hero Candidate hero
     * @param allies Current allied heroes
     * @return Score component (0.0-1.0, higher is better synergy)
     */
    public ScoreComponent score(Hero hero, List<Hero> allies) {
        if (allies.isEmpty()) {
            return ScoreComponent.synergy(0.5, "No allies yet");
        }
        
        List<Integer> allyIds = allies.stream().map(Hero::id).toList();
        double avgSynergy = synergyRepository.calculateAverageSynergy(hero.id(), allyIds);
        
        String description = formatDescription(avgSynergy, allies);
        return ScoreComponent.synergy(avgSynergy, description);
    }
    
    private String formatDescription(double score, List<Hero> allies) {
        if (score >= 0.7) {
            return "Strong synergy with " + allies.size() + " allies";
        } else if (score >= 0.5) {
            return "Neutral synergy with team";
        } else {
            return "Weak synergy with current composition";
        }
    }
}

