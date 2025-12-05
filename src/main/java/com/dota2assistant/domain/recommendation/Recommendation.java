package com.dota2assistant.domain.recommendation;

import com.dota2assistant.domain.model.Hero;

import java.util.List;

/**
 * A hero recommendation with scoring breakdown.
 */
public record Recommendation(
    Hero hero,
    double score,
    List<ScoreComponent> reasons,
    String llmExplanation
) {
    /**
     * Creates a recommendation without LLM explanation.
     */
    public static Recommendation of(Hero hero, double score, List<ScoreComponent> reasons) {
        return new Recommendation(hero, score, List.copyOf(reasons), null);
    }
    
    /**
     * Returns a new recommendation with the LLM explanation added.
     */
    public Recommendation withExplanation(String explanation) {
        return new Recommendation(hero, score, reasons, explanation);
    }
}
