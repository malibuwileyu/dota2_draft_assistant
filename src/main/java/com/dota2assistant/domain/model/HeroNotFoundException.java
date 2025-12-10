package com.dota2assistant.domain.model;

import com.dota2assistant.util.AppError;
import java.util.Map;

/**
 * Exception thrown when a hero cannot be found.
 */
public class HeroNotFoundException extends AppError {
    private static final long serialVersionUID = 1L;
    
    public HeroNotFoundException(int heroId) {
        super(
            "HERO_NOT_FOUND",
            "Hero not found: " + heroId,
            true,
            Map.of("heroId", heroId),
            null
        );
    }
    
    public HeroNotFoundException(String heroName) {
        super(
            "HERO_NOT_FOUND",
            "Hero not found: " + heroName,
            true,
            Map.of("heroName", heroName),
            null
        );
    }
}

