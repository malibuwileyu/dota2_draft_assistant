package com.dota2assistant.data.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Test for Ability JSON deserialization.
 */
public class AbilityDeserializationTest {
    
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
        @com.fasterxml.jackson.annotation.JsonProperty("localized_name")
        private String localizedName;
        private List<Ability> abilities;
        
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
    }

    @Test
    public void testAxeAbilitiesDeserialization() throws IOException {
        // Create ObjectMapper with the same configuration as in AppConfig
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Load the axe abilities JSON file
        File file = Paths.get("src/main/resources/data/abilities/axe_abilities.json").toFile();
        assertTrue(file.exists(), "Axe abilities file should exist");
        
        // Deserialize the JSON to object
        HeroAbilitiesContainer container = objectMapper.readValue(file, HeroAbilitiesContainer.class);
        
        // Validate the container
        assertNotNull(container, "Container should not be null");
        assertNotNull(container.getHeroes(), "Heroes list should not be null");
        assertFalse(container.getHeroes().isEmpty(), "Heroes list should not be empty");
        
        // Validate the hero data
        HeroAbilitiesData heroData = container.getHeroes().get(0);
        assertEquals(2, heroData.getId(), "Axe ID should be 2");
        assertEquals("axe", heroData.getName(), "Hero name should be axe");
        assertEquals("Axe", heroData.getLocalizedName(), "Localized name should be Axe");
        
        // Validate the abilities
        assertNotNull(heroData.getAbilities(), "Abilities list should not be null");
        assertEquals(4, heroData.getAbilities().size(), "Axe should have 4 abilities");
        
        // Check the first ability
        Ability berserkerCall = heroData.getAbilities().get(0);
        assertEquals("Berserker's Call", berserkerCall.getName(), "First ability should be Berserker's Call");
        assertEquals("none", berserkerCall.getDamageType(), "Damage type should be none");
        assertNotNull(berserkerCall.getSpecialValues(), "Special values should not be null");
        assertTrue(berserkerCall.getSpecialValues().containsKey("radius"), "Special values should contain radius");
        
        // Test that we can access the special values
        Map<String, Object> specialValues = berserkerCall.getSpecialValues();
        assertTrue(specialValues.size() > 0, "Should have special values");
        
        // Successful test output
        System.out.println("AbilityDeserializationTest passed successfully!");
        System.out.println("Axe abilities were correctly deserialized with special_values and damage_type fields.");
    }
}