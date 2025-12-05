package com.dota2assistant.domain.recommendation;

import com.dota2assistant.domain.draft.DraftState;
import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.Team;

import java.util.Comparator;
import java.util.List;

/**
 * Core recommendation engine that scores heroes based on multiple factors.
 */
public class RecommendationEngine {
    
    private static final double SYNERGY_WEIGHT = 0.25;
    private static final double COUNTER_WEIGHT = 0.30;
    private static final double ROLE_WEIGHT = 0.25;
    private static final double META_WEIGHT = 0.20;
    
    private final SynergyScorer synergyScorer;
    private final CounterScorer counterScorer;
    private final RoleScorer roleScorer;
    
    public RecommendationEngine(SynergyScorer synergyScorer, CounterScorer counterScorer, RoleScorer roleScorer) {
        this.synergyScorer = synergyScorer;
        this.counterScorer = counterScorer;
        this.roleScorer = roleScorer;
    }
    
    /**
     * Get hero recommendations for a team.
     * @param state Current draft state
     * @param forTeam Team to recommend for
     * @param count Number of recommendations
     * @return Sorted list of recommendations (highest score first)
     */
    public List<Recommendation> getRecommendations(DraftState state, Team forTeam, int count) {
        List<Hero> allies = forTeam == Team.RADIANT ? state.radiantPicks() : state.direPicks();
        List<Hero> enemies = forTeam == Team.RADIANT ? state.direPicks() : state.radiantPicks();
        
        return state.availableHeroes().stream()
            .map(hero -> scoreHero(hero, allies, enemies, state))
            .sorted(Comparator.comparingDouble(Recommendation::score).reversed())
            .limit(count)
            .toList();
    }
    
    private Recommendation scoreHero(Hero hero, List<Hero> allies, List<Hero> enemies, DraftState state) {
        ScoreComponent synergy = synergyScorer.score(hero, allies);
        ScoreComponent counter = counterScorer.score(hero, enemies);
        ScoreComponent role = roleScorer.score(hero, allies, state.phase());
        ScoreComponent meta = ScoreComponent.meta(0.5, "Meta scoring not implemented");
        
        double totalScore = 
            SYNERGY_WEIGHT * synergy.value() +
            COUNTER_WEIGHT * counter.value() +
            ROLE_WEIGHT * role.value() +
            META_WEIGHT * meta.value();
        
        List<ScoreComponent> reasons = List.of(synergy, counter, role, meta);
        return Recommendation.of(hero, totalScore, reasons);
    }
}

