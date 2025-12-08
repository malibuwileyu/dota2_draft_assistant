package com.dota2assistant.domain.recommendation;

import com.dota2assistant.domain.draft.DraftPhase;
import com.dota2assistant.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Role Scorer")
class RoleScorerTest {
    
    private RoleScorer scorer;
    
    @BeforeEach
    void setUp() {
        scorer = new RoleScorer();
    }
    
    @Test
    @DisplayName("returns neutral score for first pick")
    void score_firstPick_returnsNeutral() {
        Hero hero = createHeroWithRoles(1, "Test Hero", List.of("Carry", "Nuker"));
        
        ScoreComponent result = scorer.score(hero, List.of(), DraftPhase.PICK_1);
        
        assertThat(result.type()).isEqualTo(ScoreComponent.ROLE);
        assertThat(result.value()).isEqualTo(0.5);
        assertThat(result.description()).contains("First pick");
    }
    
    @Test
    @DisplayName("high score when filling missing roles")
    void score_fillsMissingRoles_returnsHighScore() {
        Hero support = createHeroWithRoles(1, "Support", List.of("Support", "Disabler"));
        Hero carry = createHeroWithRoles(2, "Carry", List.of("Carry"));
        
        // Team has only a carry, missing support
        ScoreComponent result = scorer.score(support, List.of(carry), DraftPhase.PICK_1);
        
        assertThat(result.value()).isGreaterThan(0.3);
        assertThat(result.description()).contains("Fills");
    }
    
    @Test
    @DisplayName("lower score when duplicating roles")
    void score_duplicateRole_returnsLowerScore() {
        Hero carry1 = createHeroWithRoles(1, "Carry 1", List.of("Carry"));
        Hero carry2 = createHeroWithRoles(2, "Carry 2", List.of("Carry"));
        
        ScoreComponent result = scorer.score(carry2, List.of(carry1), DraftPhase.PICK_2);
        
        // Late draft duplicate carry gets penalized
        assertThat(result.description()).contains("Duplicate core role");
    }
    
    @Test
    @DisplayName("duplicate carry not penalized in early draft")
    void score_duplicateCarryEarlyDraft_noPenalty() {
        Hero carry1 = createHeroWithRoles(1, "Carry 1", List.of("Carry"));
        Hero carry2 = createHeroWithRoles(2, "Carry 2", List.of("Carry"));
        
        ScoreComponent earlyResult = scorer.score(carry2, List.of(carry1), DraftPhase.PICK_1);
        ScoreComponent lateResult = scorer.score(carry2, List.of(carry1), DraftPhase.PICK_3);
        
        // Early draft allows more flexibility
        assertThat(earlyResult.value()).isGreaterThanOrEqualTo(lateResult.value());
    }
    
    @ParameterizedTest
    @EnumSource(value = DraftPhase.class, names = {"PICK_2", "PICK_3"})
    @DisplayName("duplicate carry penalized in late draft phases")
    void score_duplicateCarryLateDraft_penalized(DraftPhase phase) {
        Hero carry1 = createHeroWithRoles(1, "Carry 1", List.of("Carry"));
        Hero carry2 = createHeroWithRoles(2, "Carry 2", List.of("Carry"));
        
        ScoreComponent result = scorer.score(carry2, List.of(carry1), phase);
        
        assertThat(result.description()).contains("Duplicate");
    }
    
    @Test
    @DisplayName("hero filling all missing roles gets high score")
    void score_fillsAllMissingRoles_maxScore() {
        // Team with only a carry needs all other roles
        Hero carry = createHeroWithRoles(1, "Carry", List.of("Carry"));
        
        // Hero fills multiple missing roles
        Hero versatile = createHeroWithRoles(2, "Versatile", 
            List.of("Nuker", "Initiator", "Disabler", "Support"));
        
        ScoreComponent result = scorer.score(versatile, List.of(carry), DraftPhase.PICK_1);
        
        assertThat(result.value()).isGreaterThan(0.5);
    }
    
    @Test
    @DisplayName("returns team complete message when all roles filled")
    void score_allRolesFilled_returnsComplete() {
        // Full team with all core roles covered
        List<Hero> fullTeam = List.of(
            createHeroWithRoles(1, "Carry", List.of("Carry")),
            createHeroWithRoles(2, "Nuker", List.of("Nuker")),
            createHeroWithRoles(3, "Initiator", List.of("Initiator")),
            createHeroWithRoles(4, "Disabler", List.of("Disabler")),
            createHeroWithRoles(5, "Support", List.of("Support"))
        );
        
        // This hero doesn't fill any missing role
        Hero anyHero = createHeroWithRoles(6, "Any Hero", List.of("Escape"));
        
        ScoreComponent result = scorer.score(anyHero, fullTeam, DraftPhase.PICK_3);
        
        // When all roles are already covered
        assertThat(result.value()).isEqualTo(0.5); // Neutral
    }
    
    @Test
    @DisplayName("handles hero with no matching core roles")
    void score_heroWithNoMatchingRoles_lowScore() {
        Hero carry = createHeroWithRoles(1, "Carry", List.of("Carry"));
        // Hero with non-core roles only
        Hero jungler = createHeroWithRoles(2, "Jungler", List.of("Escape", "Durable"));
        
        ScoreComponent result = scorer.score(jungler, List.of(carry), DraftPhase.PICK_1);
        
        assertThat(result.description()).contains("Doesn't fill");
    }
    
    @Test
    @DisplayName("score is capped at 1.0")
    void score_neverExceedsOne() {
        // Hero that fills every role
        Hero godlike = createHeroWithRoles(1, "Godlike", 
            List.of("Carry", "Nuker", "Initiator", "Disabler", "Support"));
        
        // Empty team means all roles missing
        ScoreComponent result = scorer.score(godlike, List.of(), DraftPhase.PICK_1);
        
        assertThat(result.value()).isLessThanOrEqualTo(1.0);
    }
    
    private Hero createHeroWithRoles(int id, String name, List<String> roles) {
        return new Hero(
            id, "npc_dota_hero_" + name.toLowerCase().replace(" ", "_"), name,
            Attribute.STRENGTH, AttackType.MELEE,
            roles, Map.of(1, 0.5),
            HeroAttributes.defaults(), "", "", List.of()
        );
    }
}

