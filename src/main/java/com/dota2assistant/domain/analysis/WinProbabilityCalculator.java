package com.dota2assistant.domain.analysis;

import com.dota2assistant.domain.draft.DraftState;
import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.repository.SynergyRepository;

import java.util.List;

/**
 * Calculates win probability based on draft composition.
 */
public class WinProbabilityCalculator {
    
    private final SynergyRepository synergyRepository;
    
    public WinProbabilityCalculator(SynergyRepository synergyRepository) {
        this.synergyRepository = synergyRepository;
    }
    
    /**
     * Calculate win probability for Radiant.
     * @param state Current draft state
     * @return Probability 0.0-1.0 (0.5 = even)
     */
    public double calculateRadiantWinProbability(DraftState state) {
        if (state.radiantPicks().isEmpty() && state.direPicks().isEmpty()) {
            return 0.5;
        }
        
        double radiantStrength = calculateTeamStrength(state.radiantPicks(), state.direPicks());
        double direStrength = calculateTeamStrength(state.direPicks(), state.radiantPicks());
        
        // Convert to probability using logistic function
        double diff = radiantStrength - direStrength;
        return 1.0 / (1.0 + Math.exp(-diff * 2));
    }
    
    /**
     * Calculate team strength based on synergy and counter matchups.
     */
    private double calculateTeamStrength(List<Hero> team, List<Hero> enemies) {
        if (team.isEmpty()) return 0.5;
        
        double synergyScore = calculateInternalSynergy(team);
        double counterScore = calculateCounterAdvantage(team, enemies);
        
        return 0.5 * synergyScore + 0.5 * counterScore;
    }
    
    private double calculateInternalSynergy(List<Hero> team) {
        if (team.size() < 2) return 0.5;
        
        double totalSynergy = 0;
        int pairs = 0;
        
        for (int i = 0; i < team.size(); i++) {
            for (int j = i + 1; j < team.size(); j++) {
                double synergy = synergyRepository
                    .getSynergyScore(team.get(i).id(), team.get(j).id())
                    .orElse(0.5);
                totalSynergy += synergy;
                pairs++;
            }
        }
        
        return pairs > 0 ? totalSynergy / pairs : 0.5;
    }
    
    private double calculateCounterAdvantage(List<Hero> team, List<Hero> enemies) {
        if (team.isEmpty() || enemies.isEmpty()) return 0.5;
        
        double totalCounter = 0;
        int matchups = 0;
        
        for (Hero ally : team) {
            for (Hero enemy : enemies) {
                double counter = synergyRepository
                    .getCounterScore(ally.id(), enemy.id())
                    .orElse(0.5);
                totalCounter += counter;
                matchups++;
            }
        }
        
        return matchups > 0 ? totalCounter / matchups : 0.5;
    }
}

