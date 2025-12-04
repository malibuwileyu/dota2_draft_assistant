package com.dota2assistant.domain.model;

/**
 * Base attributes and stats for a hero.
 * All values represent base stats at level 1.
 */
public record HeroAttributes(
    double baseStrength,
    double baseAgility,
    double baseIntelligence,
    double strengthGain,
    double agilityGain,
    double intelligenceGain,
    int moveSpeed,
    double armor,
    int attackDamageMin,
    int attackDamageMax,
    int attackRange,
    double attackRate
) {
    /**
     * Creates default attributes for testing.
     */
    public static HeroAttributes defaults() {
        return new HeroAttributes(20, 20, 20, 2.0, 2.0, 2.0, 300, 0, 50, 60, 150, 1.7);
    }
}

