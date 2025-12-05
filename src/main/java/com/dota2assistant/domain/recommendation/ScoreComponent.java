package com.dota2assistant.domain.recommendation;

/**
 * A single component of a hero's recommendation score.
 */
public record ScoreComponent(
    String type,
    double value,
    String description
) {
    public static final String SYNERGY = "synergy";
    public static final String COUNTER = "counter";
    public static final String ROLE = "role";
    public static final String META = "meta";
    public static final String PERSONAL = "personal";
    
    public static ScoreComponent synergy(double value, String description) {
        return new ScoreComponent(SYNERGY, value, description);
    }
    
    public static ScoreComponent counter(double value, String description) {
        return new ScoreComponent(COUNTER, value, description);
    }
    
    public static ScoreComponent role(double value, String description) {
        return new ScoreComponent(ROLE, value, description);
    }
    
    public static ScoreComponent meta(double value, String description) {
        return new ScoreComponent(META, value, description);
    }
    
    public static ScoreComponent personal(double value, String description) {
        return new ScoreComponent(PERSONAL, value, description);
    }
}
