package com.dota2assistant.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a Dota 2 hero.
 * This is an immutable record - all modifications return new instances.
 */
public record Hero(
    int id,
    String name,               // Internal name (e.g., "antimage")
    String localizedName,      // Display name (e.g., "Anti-Mage")
    Attribute primaryAttribute,
    AttackType attackType,
    List<String> roles,        // ["Carry", "Escape", "Nuker"]
    Map<Integer, Double> positions, // Position -> frequency (1-5)
    HeroAttributes attributes,
    String imageUrl,
    String iconUrl,
    List<Ability> abilities
) {
    /**
     * Creates a hero with default/empty abilities.
     * Useful for loading from database where abilities are loaded separately.
     */
    public Hero withAbilities(List<Ability> abilities) {
        return new Hero(id, name, localizedName, primaryAttribute, attackType,
            roles, positions, attributes, imageUrl, iconUrl, abilities);
    }
    
    /**
     * Checks if this hero has a specific role.
     */
    public boolean hasRole(String role) {
        return roles.stream().anyMatch(r -> r.equalsIgnoreCase(role));
    }
    
    /**
     * Gets the primary position (highest frequency).
     */
    public int primaryPosition() {
        return positions.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(3); // Default to position 3 (mid)
    }
}

