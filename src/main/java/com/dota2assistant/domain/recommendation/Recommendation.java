package com.dota2assistant.domain.recommendation;

import com.dota2assistant.domain.model.Hero;
import java.util.List;

/**
 * A hero recommendation with score and reasoning.
 */
public record Recommendation(
    Hero hero,
    double score,                   // 0.0 to 1.0
    List<ScoreComponent> reasons,   // Breakdown of score components
    String llmExplanation           // AI-generated explanation (may be null)
) {
    /**
     * Creates a recommendation without LLM explanation.
     */
    public static Recommendation of(Hero hero, double score, List<ScoreComponent> reasons) {
        return new Recommendation(hero, score, reasons, null);
    }
    
    /**
     * Returns a new recommendation with the LLM explanation added.
     */
    public Recommendation withExplanation(String explanation) {
        return new Recommendation(hero, score, reasons, explanation);
    }
    
    /**
     * Gets the score as a percentage string.
     */
    public String scoreAsPercent() {
        return String.format("%.0f%%", score * 100);
    }
}

