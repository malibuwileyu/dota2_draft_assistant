package com.dota2assistant.domain.repository;

import com.dota2assistant.domain.model.Attribute;
import com.dota2assistant.domain.model.Hero;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for hero data access.
 * Domain layer defines the interface; infrastructure layer provides implementation.
 */
public interface HeroRepository {
    
    /**
     * Find all heroes.
     * @return List of all heroes, sorted by localized name
     */
    List<Hero> findAll();
    
    /**
     * Find a hero by ID.
     * @param id Hero ID
     * @return Hero if found
     */
    Optional<Hero> findById(int id);
    
    /**
     * Find a hero by internal name (e.g., "npc_dota_hero_antimage").
     * @param name Internal hero name
     * @return Hero if found
     */
    Optional<Hero> findByName(String name);
    
    /**
     * Find heroes by primary attribute.
     * @param attribute Primary attribute to filter by
     * @return List of heroes with the given attribute
     */
    List<Hero> findByAttribute(Attribute attribute);
    
    /**
     * Find heroes that have a specific role.
     * @param role Role name (e.g., "Carry", "Support")
     * @return List of heroes with the given role
     */
    List<Hero> findByRole(String role);
    
    /**
     * Search heroes by localized name (case-insensitive partial match).
     * @param query Search query
     * @return List of matching heroes
     */
    List<Hero> search(String query);
    
    /**
     * Get the total count of heroes.
     * @return Number of heroes in the repository
     */
    int count();
}

