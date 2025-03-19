package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Ability;
import com.dota2assistant.data.model.Hero;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NlpAbilityAnalyzerTest {

    private NlpAbilityAnalyzer abilityAnalyzer;
    
    @BeforeEach
    void setUp() {
        // Initialize the ability analyzer
        abilityAnalyzer = new NlpAbilityAnalyzer();
    }
    
    @Test
    void testExtractAbilityFeatures() {
        // Create test ability
        Ability stunAbility = new Ability();
        stunAbility.setName("Frost Nova");
        stunAbility.setDescription("Freezes an enemy unit in place, preventing it from moving and attacking, " + 
            "while dealing 150/200/250/300 magical damage and slowing its movement by 20%/30%/40%/50% " +
            "for 3/3.5/4/4.5 seconds after the stun wears off.");
        
        // Extract features
        Map<String, Object> features = abilityAnalyzer.extractAbilityFeatures(stunAbility);
        
        // Verify features
        assertNotNull(features);
        assertTrue(features.containsKey("stun"));
        assertTrue(features.containsKey("slow"));
        assertTrue(features.containsKey("damage_type"));
        assertTrue(features.containsKey("damage_values"));
        
        assertEquals(true, features.get("stun"));
        assertEquals("magical", features.get("damage_type"));
        
        @SuppressWarnings("unchecked")
        List<Integer> damageValues = (List<Integer>) features.get("damage_values");
        assertTrue(damageValues.contains(150));
        assertTrue(damageValues.contains(300));
    }
    
    @Test
    void testCreateHeroFeatureVector() {
        // Create test hero with abilities
        Hero hero = createTestHero(
            "Test Hero",
            createTestAbility("Frost Nova", "Freezes an enemy unit in place, preventing it from moving and attacking, " +
                "while dealing 150/200/250/300 magical damage."),
            createTestAbility("Arcane Bolt", "Fires a bolt of arcane energy dealing 100 magical damage plus " +
                "30% of the caster's intelligence."),
            createTestAbility("Teleport", "Teleports the hero to a target location up to 1200 units away.")
        );
        
        // Create hero feature vector
        Map<String, Double> featureVector = abilityAnalyzer.createHeroFeatureVector(hero);
        
        // Verify feature vector
        assertNotNull(featureVector);
        assertTrue(featureVector.containsKey("stun_score"));
        assertTrue(featureVector.containsKey("magical_damage"));
        assertTrue(featureVector.containsKey("mobility_score"));
        
        // Stun score should be high due to Frost Nova
        assertTrue(featureVector.get("stun_score") > 0.5);
        
        // Magical damage should be high due to two damage abilities
        assertTrue(featureVector.get("magical_damage") > 0.5);
        
        // Mobility should be non-zero due to teleport
        assertTrue(featureVector.get("mobility_score") > 0);
    }
    
    @Test
    void testComputeHeroSimilarity() {
        // Create two test heroes with different ability profiles
        Hero stunHero = createTestHero(
            "Stun Hero",
            createTestAbility("Stun", "Stuns enemies for 2 seconds."),
            createTestAbility("Magic Damage", "Deals 300 magical damage.")
        );
        
        Hero damageHero = createTestHero(
            "Damage Hero",
            createTestAbility("Physical Strike", "Deals 250 physical damage."),
            createTestAbility("Magic Burst", "Deals 400 magical damage.")
        );
        
        Hero silenceHero = createTestHero(
            "Silence Hero",
            createTestAbility("Silence", "Silences enemies for 5 seconds."),
            createTestAbility("Heal", "Heals allies for 200 health.")
        );
        
        // Compute similarities
        double similarityStunToDamage = abilityAnalyzer.computeHeroSimilarity(stunHero, damageHero);
        double similarityStunToSilence = abilityAnalyzer.computeHeroSimilarity(stunHero, silenceHero);
        double similarityDamageToSilence = abilityAnalyzer.computeHeroSimilarity(damageHero, silenceHero);
        
        // Verify similarities
        // Stun hero and damage hero should be more similar than stun hero and silence hero
        assertTrue(similarityStunToDamage > similarityStunToSilence);
        
        // Damage hero and silence hero should be least similar
        assertTrue(similarityStunToDamage > similarityDamageToSilence);
        assertTrue(similarityStunToSilence > similarityDamageToSilence);
    }
    
    @Test
    void testFindPotentialSynergies() {
        // Create heroes with synergistic abilities
        Hero stunHero = createTestHero(
            "Stun Hero",
            createTestAbility("Stun", "Stuns enemies for 2 seconds.")
        );
        
        Hero damageHero = createTestHero(
            "Damage Hero",
            createTestAbility("Magic Burst", "Channels for 2 seconds to deal 800 magical damage in an area.")
        );
        
        // Find synergies
        List<NlpAbilityAnalyzer.AbilitySynergy> synergies = abilityAnalyzer.findPotentialSynergies(stunHero, damageHero);
        
        // Verify synergies are found (stun + channeled damage is a classic synergy)
        assertNotNull(synergies);
        assertTrue(synergies.size() > 0);
        
        // The synergy description should mention both abilities
        boolean foundSynergy = false;
        for (NlpAbilityAnalyzer.AbilitySynergy synergy : synergies) {
            if (synergy.getDescription().toLowerCase().contains("stun") && 
                synergy.getDescription().toLowerCase().contains("channel")) {
                foundSynergy = true;
                break;
            }
        }
        
        assertTrue(foundSynergy, "Expected to find stun + channel synergy");
    }
    
    /**
     * Helper method to create a test hero
     */
    private Hero createTestHero(String name, Ability... abilities) {
        Hero hero = new Hero();
        hero.setLocalizedName(name);
        
        List<Ability> abilityList = new ArrayList<>();
        for (Ability ability : abilities) {
            abilityList.add(ability);
        }
        hero.setAbilities(abilityList);
        
        return hero;
    }
    
    /**
     * Helper method to create a test ability
     */
    private Ability createTestAbility(String name, String description) {
        Ability ability = new Ability();
        ability.setName(name);
        ability.setDescription(description);
        return ability;
    }
}