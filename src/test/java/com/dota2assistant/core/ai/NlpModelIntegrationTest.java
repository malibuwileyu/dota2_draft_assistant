package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Ability;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NlpModelIntegrationTest {

    @Mock
    private HeroRepository heroRepository;
    
    @Mock
    private NlpAbilityAnalyzer abilityAnalyzer;
    
    private NlpModelIntegration nlpModel;
    
    private Hero testHero1;
    private Hero testHero2;
    private List<Hero> testHeroes;
    
    @BeforeEach
    void setUp() {
        // Create test heroes with abilities
        testHero1 = createTestHero(1, "Test Hero 1", createTestAbilities("stun", "damage", "mobility"));
        testHero2 = createTestHero(2, "Test Hero 2", createTestAbilities("silence", "magic immunity", "heal"));
        
        testHeroes = new ArrayList<>();
        testHeroes.add(testHero1);
        testHeroes.add(testHero2);
        
        // Setup repository mocks
        when(heroRepository.getAllHeroes()).thenReturn(testHeroes);
        when(heroRepository.getHeroById(1)).thenReturn(testHero1);
        when(heroRepository.getHeroById(2)).thenReturn(testHero2);
        
        // Setup ability analyzer mocks
        Map<String, Double> hero1Features = Map.of(
            "stun_score", 0.8,
            "mobility_score", 0.7,
            "magical_damage", 0.6,
            "physical_damage", 0.4,
            "aoe_impact", 0.5
        );
        when(abilityAnalyzer.createHeroFeatureVector(testHero1)).thenReturn(hero1Features);
        
        Map<String, Double> hero2Features = Map.of(
            "stun_score", 0.2,
            "silence_score", 0.9,
            "magical_damage", 0.3,
            "physical_damage", 0.3,
            "sustain_score", 0.8
        );
        when(abilityAnalyzer.createHeroFeatureVector(testHero2)).thenReturn(hero2Features);
        
        // Mock similarity computation
        when(abilityAnalyzer.computeHeroSimilarity(testHero1, testHero2)).thenReturn(0.35);
        when(abilityAnalyzer.computeHeroSimilarity(testHero1, testHero1)).thenReturn(1.0);
        when(abilityAnalyzer.computeHeroSimilarity(testHero2, testHero2)).thenReturn(1.0);
        
        // Mock synergies
        List<NlpAbilityAnalyzer.AbilitySynergy> synergies = new ArrayList<>();
        Ability mockAbility1 = mock(Ability.class);
        Ability mockAbility2 = mock(Ability.class);
        synergies.add(new NlpAbilityAnalyzer.AbilitySynergy(
            mockAbility1, mockAbility2, "Combo", "Strong stun into silence combo", 0.75
        ));
        when(abilityAnalyzer.findPotentialSynergies(any(), any())).thenReturn(synergies);
        
        // Initialize service under test
        nlpModel = new NlpModelIntegration(heroRepository, abilityAnalyzer);
    }
    
    @Test
    void testGetHeroFeatureVector() {
        // Test getting feature vector
        Map<String, Double> features = nlpModel.getHeroFeatureVector(testHero1.getId());
        
        // Verify features
        assertNotNull(features);
        assertEquals(0.8, features.get("stun_score"));
        assertEquals(0.7, features.get("mobility_score"));
        assertEquals(0.6, features.get("magical_damage"));
        assertEquals(0.4, features.get("physical_damage"));
        assertEquals(0.5, features.get("aoe_impact"));
    }
    
    @Test
    void testGetHeroSimilarity() {
        // Test hero similarity
        double similarity = nlpModel.getHeroSimilarity(testHero1.getId(), testHero2.getId());
        
        // Verify similarity score
        assertEquals(0.35, similarity);
    }
    
    @Test
    void testFindAbilitySynergies() {
        // Test finding synergies
        List<NlpAbilityAnalyzer.AbilitySynergy> synergies = 
            nlpModel.findAbilitySynergies(testHero1.getId(), testHero2.getId());
        
        // Verify synergies
        assertNotNull(synergies);
        assertEquals(1, synergies.size());
        assertEquals(0.75, synergies.get(0).getScore());
        assertEquals("Strong stun into silence combo", synergies.get(0).getDescription());
    }
    
    @Test
    void testGenerateRecommendationExplanation() {
        // Setup allies and enemies
        List<Hero> allies = List.of(testHero2);
        List<Hero> enemies = List.of(testHero1);
        
        // Test generating an explanation
        String explanation = nlpModel.generateRecommendationExplanation(testHero1, enemies, allies);
        
        // Verify explanation contains expected elements
        assertNotNull(explanation);
        assertTrue(explanation.contains(testHero1.getLocalizedName()));
        // Additional assertions would depend on actual implementation
    }
    
    /**
     * Helper method to create a test hero
     */
    private Hero createTestHero(int id, String name, List<Ability> abilities) {
        Hero hero = new Hero();
        hero.setId(id);
        hero.setLocalizedName(name);
        hero.setAbilities(abilities);
        return hero;
    }
    
    /**
     * Helper method to create test abilities
     */
    private List<Ability> createTestAbilities(String... types) {
        List<Ability> abilities = new ArrayList<>();
        
        for (int i = 0; i < types.length; i++) {
            Ability ability = new Ability();
            ability.setName("Test Ability " + (i + 1));
            
            String description = "This ability ";
            switch (types[i]) {
                case "stun":
                    description += "stuns enemies for 2 seconds and deals 100 damage.";
                    break;
                case "damage":
                    description += "deals 300 magical damage to enemies in a 500 radius.";
                    break;
                case "mobility":
                    description += "allows the hero to blink 1200 units in any direction.";
                    break;
                case "silence":
                    description += "silences enemies for 5 seconds in a 400 radius.";
                    break;
                case "magic immunity":
                    description += "grants magic immunity for 6 seconds.";
                    break;
                case "heal":
                    description += "heals allies for 200 health over time.";
                    break;
                default:
                    description += "has various effects.";
            }
            
            ability.setDescription(description);
            abilities.add(ability);
        }
        
        return abilities;
    }
}