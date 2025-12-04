package com.dota2assistant.domain.recommendation;

/**
 * A component of a recommendation score with reasoning.
 */
public record ScoreComponent(
    String type,          // "synergy", "counter", "role", "meta", "personal"
    double value,         // 0.0 to 1.0
    String description    // Human-readable reason
) {
    /**
     * Creates a synergy score component.
     */
    public static ScoreComponent synergy(double value, String description) {
        return new ScoreComponent("synergy", value, description);
    }
    
    /**
     * Creates a counter score component.
     */
    public static ScoreComponent counter(double value, String description) {
        return new ScoreComponent("counter", value, description);
    }
    
    /**
     * Creates a role score component.
     */
    public static ScoreComponent role(double value, String description) {
        return new ScoreComponent("role", value, description);
    }
    
    /**
     * Creates a meta score component.
     */
    public static ScoreComponent meta(double value, String description) {
        return new ScoreComponent("meta", value, description);
    }
}

