package com.dota2assistant.data.repository;

import com.dota2assistant.data.model.Ability;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.InnateAbility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class HeroAbilitiesRepository {
    private static final Logger logger = LoggerFactory.getLogger(HeroAbilitiesRepository.class);
    
    private final ObjectMapper objectMapper;
    private final Map<Integer, List<Ability>> heroAbilities = new HashMap<>();
    private final Map<Integer, List<InnateAbility>> heroInnateAbilities = new HashMap<>();
    private final Map<String, List<HeroSynergy>> heroSynergies = new HashMap<>();
    private final Map<String, List<HeroCounter>> heroCounters = new HashMap<>();
    
    public HeroAbilitiesRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadHeroAbilitiesData();
    }
    
    /**
     * Load hero abilities data from JSON files
     */
    private void loadHeroAbilitiesData() {
        try {
            // First try to load the combined file
            Path combinedPath = Paths.get("src/main/resources/data/abilities/hero_abilities.json");
            if (Files.exists(combinedPath)) {
                logger.debug("Loading hero abilities from combined file: {}", combinedPath);
                loadCombinedHeroAbilities(combinedPath);
            } else {
                // If combined file doesn't exist, try individual hero files
                logger.debug("Combined hero abilities file not found, trying individual hero files");
                loadIndividualHeroAbilities();
            }
        } catch (Exception e) {
            logger.error("Failed to load hero abilities data", e);
        }
    }
    
    private void loadCombinedHeroAbilities(Path filePath) throws IOException {
        HeroAbilitiesContainer container = objectMapper.readValue(filePath.toFile(), HeroAbilitiesContainer.class);
        
        if (container != null && container.getHeroes() != null) {
            for (HeroAbilitiesData heroData : container.getHeroes()) {
                int heroId = heroData.getId();
                
                // Store abilities
                if (heroData.getAbilities() != null) {
                    heroAbilities.put(heroId, heroData.getAbilities());
                }
                
                // Store innate abilities
                if (heroData.getInnateAbilities() != null) {
                    heroInnateAbilities.put(heroId, heroData.getInnateAbilities());
                }
                
                // Store synergies
                if (heroData.getSynergies() != null) {
                    String heroKey = String.valueOf(heroId);
                    heroSynergies.put(heroKey, heroData.getSynergies());
                }
                
                // Store counters
                if (heroData.getCounters() != null) {
                    String heroKey = String.valueOf(heroId);
                    heroCounters.put(heroKey, heroData.getCounters());
                }
            }
            
            logger.debug("Loaded abilities for {} heroes from combined file", container.getHeroes().size());
        }
    }
    
    private void loadIndividualHeroAbilities() throws IOException {
        Path abilitiesDir = Paths.get("src/main/resources/data/abilities");
        if (!Files.exists(abilitiesDir) || !Files.isDirectory(abilitiesDir)) {
            logger.warn("Hero abilities directory not found: {}", abilitiesDir);
            return;
        }
        
        List<Path> abilityFiles = Files.list(abilitiesDir)
                .filter(path -> path.toString().endsWith("_abilities.json"))
                .collect(Collectors.toList());
        
        for (Path file : abilityFiles) {
            try {
                // First try to read as container with heroes array
                HeroAbilitiesContainer container = objectMapper.readValue(file.toFile(), HeroAbilitiesContainer.class);
                
                if (container != null && container.getHeroes() != null && !container.getHeroes().isEmpty()) {
                    // Process container format
                    HeroAbilitiesData heroData = container.getHeroes().get(0);
                    int heroId = heroData.getId();
                    
                    // Store abilities
                    if (heroData.getAbilities() != null) {
                        heroAbilities.put(heroId, heroData.getAbilities());
                    }
                    
                    // Store innate abilities
                    if (heroData.getInnateAbilities() != null) {
                        heroInnateAbilities.put(heroId, heroData.getInnateAbilities());
                    }
                    
                    // Store synergies
                    if (heroData.getSynergies() != null) {
                        String heroKey = String.valueOf(heroId);
                        heroSynergies.put(heroKey, heroData.getSynergies());
                    }
                    
                    // Store counters
                    if (heroData.getCounters() != null) {
                        String heroKey = String.valueOf(heroId);
                        heroCounters.put(heroKey, heroData.getCounters());
                    }
                    
                    logger.debug("Loaded abilities data for hero: {}", heroData.getLocalizedName());
                } else {
                    // Fallback to direct format
                    HeroAbilitiesData heroData = objectMapper.readValue(file.toFile(), HeroAbilitiesData.class);
                    int heroId = heroData.getId();
                    
                    // Store abilities
                    if (heroData.getAbilities() != null) {
                        heroAbilities.put(heroId, heroData.getAbilities());
                    }
                    
                    // Store innate abilities
                    if (heroData.getInnateAbilities() != null) {
                        heroInnateAbilities.put(heroId, heroData.getInnateAbilities());
                    }
                    
                    // Store synergies
                    if (heroData.getSynergies() != null) {
                        String heroKey = String.valueOf(heroId);
                        heroSynergies.put(heroKey, heroData.getSynergies());
                    }
                    
                    // Store counters
                    if (heroData.getCounters() != null) {
                        String heroKey = String.valueOf(heroId);
                        heroCounters.put(heroKey, heroData.getCounters());
                    }
                    
                    logger.debug("Loaded abilities data for hero: {}", heroData.getLocalizedName());
                }
            } catch (Exception e) {
                logger.error("Failed to load hero abilities from file: {}", file, e);
            }
        }
    }
    
    /**
     * Get abilities for a specific hero
     */
    public List<Ability> getHeroAbilities(int heroId) {
        return heroAbilities.getOrDefault(heroId, Collections.emptyList());
    }
    
    /**
     * Get innate abilities for a specific hero
     */
    public List<InnateAbility> getHeroInnateAbilities(int heroId) {
        return heroInnateAbilities.getOrDefault(heroId, Collections.emptyList());
    }
    
    /**
     * Get synergies for a specific hero
     */
    public List<HeroSynergy> getHeroSynergies(int heroId) {
        String heroKey = String.valueOf(heroId);
        return heroSynergies.getOrDefault(heroKey, Collections.emptyList());
    }
    
    /**
     * Get counters for a specific hero
     */
    public List<HeroCounter> getHeroCounters(int heroId) {
        String heroKey = String.valueOf(heroId);
        return heroCounters.getOrDefault(heroKey, Collections.emptyList());
    }
    
    /**
     * Enhance hero objects with their ability data
     */
    public void enhanceHeroesWithAbilities(List<Hero> heroes) {
        for (Hero hero : heroes) {
            int heroId = hero.getId();
            
            // Add abilities
            List<Ability> abilities = getHeroAbilities(heroId);
            if (abilities != null && !abilities.isEmpty()) {
                hero.setAbilities(abilities);
            }
            
            // Add innate abilities
            List<InnateAbility> innateAbilities = getHeroInnateAbilities(heroId);
            if (innateAbilities != null && !innateAbilities.isEmpty()) {
                hero.setInnateAbilities(innateAbilities);
            }
            
            // Add synergies and counters
            List<HeroSynergy> synergies = getHeroSynergies(heroId);
            if (synergies != null) {
                for (HeroSynergy synergy : synergies) {
                    hero.addSynergy(synergy.getHeroId(), synergy.getScore());
                }
            }
            
            List<HeroCounter> counters = getHeroCounters(heroId);
            if (counters != null) {
                for (HeroCounter counter : counters) {
                    hero.addCounter(counter.getHeroId(), counter.getScore());
                }
            }
        }
    }
    
    // Inner classes for JSON deserialization
    
    private static class HeroAbilitiesContainer {
        private List<HeroAbilitiesData> heroes;
        
        public List<HeroAbilitiesData> getHeroes() {
            return heroes;
        }
        
        public void setHeroes(List<HeroAbilitiesData> heroes) {
            this.heroes = heroes;
        }
    }
    
    private static class HeroAbilitiesData {
        private int id;
        private String name;
        @JsonProperty("localized_name")
        private String localizedName;
        private List<Ability> abilities;
        @JsonProperty("innate_abilities")
        private List<InnateAbility> innateAbilities;
        private List<HeroSynergy> synergies;
        private List<HeroCounter> counters;
        
        public int getId() {
            return id;
        }
        
        public void setId(int id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getLocalizedName() {
            return localizedName;
        }
        
        public void setLocalizedName(String localizedName) {
            this.localizedName = localizedName;
        }
        
        public List<Ability> getAbilities() {
            return abilities;
        }
        
        public void setAbilities(List<Ability> abilities) {
            this.abilities = abilities;
        }
        
        public List<InnateAbility> getInnateAbilities() {
            return innateAbilities;
        }
        
        public void setInnateAbilities(List<InnateAbility> innateAbilities) {
            this.innateAbilities = innateAbilities;
        }
        
        public List<HeroSynergy> getSynergies() {
            return synergies;
        }
        
        public void setSynergies(List<HeroSynergy> synergies) {
            this.synergies = synergies;
        }
        
        public List<HeroCounter> getCounters() {
            return counters;
        }
        
        public void setCounters(List<HeroCounter> counters) {
            this.counters = counters;
        }
    }
    
    public static class HeroSynergy {
        @JsonProperty("hero_id")
        private int heroId;
        private double score;
        private String reason;
        
        public int getHeroId() {
            return heroId;
        }
        
        public void setHeroId(int heroId) {
            this.heroId = heroId;
        }
        
        public double getScore() {
            return score;
        }
        
        public void setScore(double score) {
            this.score = score;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
    
    public static class HeroCounter {
        @JsonProperty("hero_id")
        private int heroId;
        private double score;
        private String reason;
        
        public int getHeroId() {
            return heroId;
        }
        
        public void setHeroId(int heroId) {
            this.heroId = heroId;
        }
        
        public double getScore() {
            return score;
        }
        
        public void setScore(double score) {
            this.score = score;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}