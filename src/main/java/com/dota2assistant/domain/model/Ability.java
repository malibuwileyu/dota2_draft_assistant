package com.dota2assistant.domain.model;

import java.util.List;

/**
 * Represents a hero ability in Dota 2.
 */
public record Ability(
    int id,
    String name,
    String description,
    String abilityType,      // "basic", "ultimate", "innate"
    String damageType,       // "physical", "magical", "pure", null
    List<Double> cooldown,   // Cooldown per level
    List<Integer> manaCost   // Mana cost per level
) {
    /**
     * Creates a basic ability for testing.
     */
    public static Ability basic(int id, String name, String description) {
        return new Ability(id, name, description, "basic", "magical",
            List.of(10.0, 9.0, 8.0, 7.0), List.of(100, 110, 120, 130));
    }
}

