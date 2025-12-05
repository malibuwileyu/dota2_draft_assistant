package com.dota2assistant.domain.recommendation;

import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.draft.DraftPhase;

import java.util.*;

/**
 * Scores heroes based on team role coverage and gaps.
 */
public class RoleScorer {
    
    private static final List<String> CORE_ROLES = List.of("Carry", "Nuker", "Initiator", "Disabler", "Support");
    
    /**
     * Calculate role score based on team composition gaps.
     * @param hero Candidate hero
     * @param allies Current allied heroes
     * @param phase Current draft phase (earlier = more flexibility)
     * @return Score component (0.0-1.0, higher means fills needed role)
     */
    public ScoreComponent score(Hero hero, List<Hero> allies, DraftPhase phase) {
        if (allies.isEmpty()) {
            // First pick - any role is fine
            return ScoreComponent.role(0.5, "First pick - flexible");
        }
        
        Set<String> coveredRoles = getCoveredRoles(allies);
        Set<String> heroRoles = new HashSet<>(hero.roles());
        
        // Calculate how many missing roles this hero fills
        Set<String> missingRoles = new HashSet<>(CORE_ROLES);
        missingRoles.removeAll(coveredRoles);
        
        Set<String> fillsRoles = new HashSet<>(heroRoles);
        fillsRoles.retainAll(missingRoles);
        
        double fillScore = missingRoles.isEmpty() ? 0.5 : (double) fillsRoles.size() / missingRoles.size();
        
        // Penalize duplicate roles late in draft
        boolean hasDuplicateCore = hasDuplicateCoreRole(hero, allies);
        if (hasDuplicateCore && isLateDraft(phase)) {
            fillScore *= 0.7;
        }
        
        String description = formatDescription(fillsRoles, missingRoles, hasDuplicateCore);
        return ScoreComponent.role(Math.min(1.0, fillScore), description);
    }
    
    private Set<String> getCoveredRoles(List<Hero> heroes) {
        Set<String> covered = new HashSet<>();
        for (Hero h : heroes) {
            covered.addAll(h.roles());
        }
        return covered;
    }
    
    private boolean hasDuplicateCoreRole(Hero hero, List<Hero> allies) {
        for (Hero ally : allies) {
            if (ally.roles().contains("Carry") && hero.roles().contains("Carry")) return true;
        }
        return false;
    }
    
    private boolean isLateDraft(DraftPhase phase) {
        return phase == DraftPhase.PICK_2 || phase == DraftPhase.PICK_3;
    }
    
    private String formatDescription(Set<String> fills, Set<String> missing, boolean duplicate) {
        if (fills.isEmpty() && missing.isEmpty()) return "Team roles complete";
        if (!fills.isEmpty()) return "Fills: " + String.join(", ", fills);
        if (duplicate) return "Duplicate core role";
        return "Doesn't fill missing roles";
    }
}

