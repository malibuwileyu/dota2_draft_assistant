package com.dota2assistant.core.analysis;

import com.dota2assistant.data.model.Ability;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.InnateAbility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Analyzes hero ability interactions to determine synergies and counters
 * This class implements basic rule-based heuristics for ability interactions
 */
@Component
public class AbilityInteractionAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(AbilityInteractionAnalyzer.class);
    
    // Constants for different types of ability interactions
    private static final double STRONG_SYNERGY = 0.8;
    private static final double MODERATE_SYNERGY = 0.5;
    private static final double WEAK_SYNERGY = 0.3;
    
    private static final double STRONG_COUNTER = 0.8;
    private static final double MODERATE_COUNTER = 0.5;
    private static final double WEAK_COUNTER = 0.3;
    
    /**
     * Analyze potential synergy between two heroes based on their abilities
     * @param hero1 The first hero
     * @param hero2 The second hero
     * @return A synergy score and a list of reasons for the synergy
     */
    public SynergyAnalysis analyzeSynergy(Hero hero1, Hero hero2) {
        if (hero1 == null || hero2 == null) {
            return new SynergyAnalysis(0, Collections.emptyList());
        }
        
        List<String> reasons = new ArrayList<>();
        double score = 0.0;
        
        // Check for basic attribute synergies
        score += analyzeAttributeSynergy(hero1, hero2, reasons);
        
        // Analyze ability synergies
        if (hero1.getAbilities() != null && hero2.getAbilities() != null) {
            score += analyzeAbilitySynergy(hero1, hero2, reasons);
        }
        
        // Analyze innate ability synergies
        if (hero1.getInnateAbilities() != null && hero2.getInnateAbilities() != null) {
            score += analyzeInnateAbilitySynergy(hero1, hero2, reasons);
        }
        
        // Cap the score at 1.0
        score = Math.min(score, 1.0);
        
        return new SynergyAnalysis(score, reasons);
    }
    
    /**
     * Analyze potential counter relationship between two heroes based on their abilities
     * @param attackingHero The hero potentially countering
     * @param defendingHero The hero potentially being countered
     * @return A counter score and a list of reasons for the counter
     */
    public CounterAnalysis analyzeCounter(Hero attackingHero, Hero defendingHero) {
        if (attackingHero == null || defendingHero == null) {
            return new CounterAnalysis(0, Collections.emptyList());
        }
        
        List<String> reasons = new ArrayList<>();
        double score = 0.0;
        
        // Check for basic attribute counters
        score += analyzeAttributeCounter(attackingHero, defendingHero, reasons);
        
        // Analyze ability counters
        if (attackingHero.getAbilities() != null && defendingHero.getAbilities() != null) {
            score += analyzeAbilityCounter(attackingHero, defendingHero, reasons);
        }
        
        // Analyze innate ability counters
        if (attackingHero.getInnateAbilities() != null && defendingHero.getInnateAbilities() != null) {
            score += analyzeInnateAbilityCounter(attackingHero, defendingHero, reasons);
        }
        
        // Cap the score at 1.0
        score = Math.min(score, 1.0);
        
        return new CounterAnalysis(score, reasons);
    }
    
    /**
     * Analyze synergy based on hero attributes
     */
    private double analyzeAttributeSynergy(Hero hero1, Hero hero2, List<String> reasons) {
        double score = 0.0;
        
        // Check for complementary primary attributes
        if (!hero1.getPrimaryAttribute().equals(hero2.getPrimaryAttribute())) {
            score += 0.1;
            reasons.add("Diverse attribute types provide balanced team composition");
        }
        
        // Check for complementary attack types
        if (hero1.getAttackType() != null && hero2.getAttackType() != null) {
            if (!hero1.getAttackType().equals(hero2.getAttackType())) {
                score += 0.1;
                reasons.add("Mix of melee and ranged attacks provides versatility");
            }
        }
        
        // Check for role synergy
        if (hero1.getRoles() != null && hero2.getRoles() != null) {
            Set<String> rolesHero1 = new HashSet<>(hero1.getRoles());
            Set<String> rolesHero2 = new HashSet<>(hero2.getRoles());
            
            // Check for supporting pairs
            if ((rolesHero1.contains("Support") && rolesHero2.contains("Carry")) || 
                (rolesHero2.contains("Support") && rolesHero1.contains("Carry"))) {
                score += 0.2;
                reasons.add("Support and Carry combination creates strong lane synergy");
            }
            
            // Check for initiator + follow-up pairs
            if ((rolesHero1.contains("Initiator") && rolesHero2.contains("Nuker")) || 
                (rolesHero2.contains("Initiator") && rolesHero1.contains("Nuker"))) {
                score += 0.15;
                reasons.add("Initiator and Nuker combination creates good gank potential");
            }
        }
        
        return score;
    }
    
    /**
     * Analyze synergy based on hero abilities
     */
    private double analyzeAbilitySynergy(Hero hero1, Hero hero2, List<String> reasons) {
        double score = 0.0;
        
        // Examples of specific ability interactions
        for (Ability ability1 : hero1.getAbilities()) {
            for (Ability ability2 : hero2.getAbilities()) {
                // Analyze specific ability pairs that have good synergy
                score += analyzeAbilityPairSynergy(ability1, ability2, hero1, hero2, reasons);
            }
        }
        
        // General synergy patterns
        score += analyzeGeneralAbilitySynergy(hero1, hero2, reasons);
        
        return score;
    }
    
    /**
     * Analyze synergy between specific ability pairs
     */
    private double analyzeAbilityPairSynergy(Ability ability1, Ability ability2, Hero hero1, Hero hero2, List<String> reasons) {
        double score = 0.0;
        
        // Check for control + damage combos
        if (isControlAbility(ability1) && isDamageAbility(ability2)) {
            score += MODERATE_SYNERGY;
            reasons.add(hero1.getLocalizedName() + "'s " + ability1.getName() + 
                       " sets up " + hero2.getLocalizedName() + "'s " + ability2.getName() + " for effective damage");
        } 
        else if (isControlAbility(ability2) && isDamageAbility(ability1)) {
            score += MODERATE_SYNERGY;
            reasons.add(hero2.getLocalizedName() + "'s " + ability2.getName() + 
                       " sets up " + hero1.getLocalizedName() + "'s " + ability1.getName() + " for effective damage");
        }
        
        // Check for amplification + damage combos
        if (isAmplifyingAbility(ability1) && isDamageAbility(ability2)) {
            score += MODERATE_SYNERGY;
            reasons.add(hero1.getLocalizedName() + "'s " + ability1.getName() + 
                       " amplifies " + hero2.getLocalizedName() + "'s " + ability2.getName() + " damage");
        }
        else if (isAmplifyingAbility(ability2) && isDamageAbility(ability1)) {
            score += MODERATE_SYNERGY;
            reasons.add(hero2.getLocalizedName() + "'s " + ability2.getName() + 
                       " amplifies " + hero1.getLocalizedName() + "'s " + ability1.getName() + " damage");
        }
        
        return score;
    }
    
    /**
     * Analyze innate ability synergies
     */
    private double analyzeInnateAbilitySynergy(Hero hero1, Hero hero2, List<String> reasons) {
        double score = 0.0;
        
        // Example patterns for innate ability synergies
        for (InnateAbility ability1 : hero1.getInnateAbilities()) {
            for (InnateAbility ability2 : hero2.getInnateAbilities()) {
                // Add specific innate ability interactions here
                
                // Example: Persecutor (Anti-Mage) synergizes with mana-burning abilities
                if (ability1.getName().equalsIgnoreCase("Persecutor") && 
                    ability2.getDescription().toLowerCase().contains("mana")) {
                    score += MODERATE_SYNERGY;
                    reasons.add(hero1.getLocalizedName() + "'s " + ability1.getName() + 
                               " enhances effects of " + hero2.getLocalizedName() + "'s mana manipulation");
                }
                else if (ability2.getName().equalsIgnoreCase("Persecutor") && 
                         ability1.getDescription().toLowerCase().contains("mana")) {
                    score += MODERATE_SYNERGY;
                    reasons.add(hero2.getLocalizedName() + "'s " + ability2.getName() + 
                               " enhances effects of " + hero1.getLocalizedName() + "'s mana manipulation");
                }
            }
        }
        
        return score;
    }
    
    /**
     * Analyze counter based on hero attributes
     */
    private double analyzeAttributeCounter(Hero attackingHero, Hero defendingHero, List<String> reasons) {
        double score = 0.0;
        
        // Example: Agility heroes generally counter Intelligence heroes due to higher physical damage
        if (attackingHero.getPrimaryAttribute().equals("agi") && defendingHero.getPrimaryAttribute().equals("int")) {
            score += 0.1;
            reasons.add("Agility heroes often counter Intelligence heroes with high physical damage");
        }
        
        // Example: Strength heroes with high HP pools counter burst damage heroes
        if (attackingHero.getPrimaryAttribute().equals("str") && 
            defendingHero.getAbilities().stream().anyMatch(this::isBurstDamageAbility)) {
            score += 0.15;
            reasons.add("High HP pool counters burst damage strategies");
        }
        
        // Attack type counters
        if (attackingHero.getAttackType() != null && defendingHero.getAttackType() != null) {
            if (attackingHero.getAttackType().equals("Ranged") && defendingHero.getAttackType().equals("Melee")) {
                score += 0.1;
                reasons.add("Ranged heroes can kite melee heroes");
            }
        }
        
        return score;
    }
    
    /**
     * Analyze counter based on hero abilities
     */
    private double analyzeAbilityCounter(Hero attackingHero, Hero defendingHero, List<String> reasons) {
        double score = 0.0;
        
        // Check for specific ability counter interactions
        for (Ability attackAbility : attackingHero.getAbilities()) {
            for (Ability defendAbility : defendingHero.getAbilities()) {
                score += analyzeAbilityPairCounter(attackAbility, defendAbility, 
                                                attackingHero, defendingHero, reasons);
            }
        }
        
        return score;
    }
    
    /**
     * Analyze counter interactions between specific ability pairs
     */
    private double analyzeAbilityPairCounter(Ability attackAbility, Ability defendAbility, 
                                          Hero attackingHero, Hero defendingHero, 
                                          List<String> reasons) {
        double score = 0.0;
        
        // Example: Silence abilities counter spell-reliant heroes
        if (isSilencingAbility(attackAbility) && 
            defendingHero.getAbilities().stream().allMatch(a -> a.getType().equals("active"))) {
            score += STRONG_COUNTER;
            reasons.add(attackingHero.getLocalizedName() + "'s " + attackAbility.getName() + 
                       " silences " + defendingHero.getLocalizedName() + "'s spell-reliant kit");
        }
        
        // Example: Pure damage counters high armor heroes
        if (isPureDamageAbility(attackAbility) && 
            defendingHero.getAttributes() != null && 
            defendingHero.getAttributes().getArmor() > 10) {
            score += MODERATE_COUNTER;
            reasons.add(attackingHero.getLocalizedName() + "'s pure damage bypasses " + 
                       defendingHero.getLocalizedName() + "'s high armor");
        }
        
        // Example: Break effects counter passive-reliant heroes
        if (isBreakAbility(attackAbility) && 
            defendingHero.getAbilities().stream().anyMatch(a -> a.getType().equals("passive"))) {
            score += STRONG_COUNTER;
            reasons.add(attackingHero.getLocalizedName() + "'s " + attackAbility.getName() + 
                       " disables " + defendingHero.getLocalizedName() + "'s passive abilities");
        }
        
        return score;
    }
    
    /**
     * Analyze counter based on innate abilities
     */
    private double analyzeInnateAbilityCounter(Hero attackingHero, Hero defendingHero, List<String> reasons) {
        double score = 0.0;
        
        for (InnateAbility attackInnate : attackingHero.getInnateAbilities()) {
            for (InnateAbility defendInnate : defendingHero.getInnateAbilities()) {
                // Example: Anti-Mage's Persecutor counters mana-dependent heroes
                if (attackInnate.getName().equalsIgnoreCase("Persecutor") && 
                    (defendInnate.getDescription().toLowerCase().contains("mana") || 
                     defendingHero.getAbilities().stream().anyMatch(a -> 
                        a.getManaCost() != null && a.getManaCost().length > 0 && a.getManaCost()[0] > 100))) {
                    score += STRONG_COUNTER;
                    reasons.add(attackingHero.getLocalizedName() + "'s " + attackInnate.getName() + 
                               " punishes " + defendingHero.getLocalizedName() + "'s high mana usage");
                }
            }
        }
        
        return score;
    }
    
    /**
     * Analyze general ability synergy patterns across a hero's kit
     */
    private double analyzeGeneralAbilitySynergy(Hero hero1, Hero hero2, List<String> reasons) {
        double score = 0.0;
        
        // Check for AOE disable + AOE damage combos
        boolean hero1HasAoeDisable = hero1.getAbilities().stream().anyMatch(this::isAoeControlAbility);
        boolean hero2HasAoeDisable = hero2.getAbilities().stream().anyMatch(this::isAoeControlAbility);
        boolean hero1HasAoeDamage = hero1.getAbilities().stream().anyMatch(this::isAoeDamageAbility);
        boolean hero2HasAoeDamage = hero2.getAbilities().stream().anyMatch(this::isAoeDamageAbility);
        
        if ((hero1HasAoeDisable && hero2HasAoeDamage) || (hero2HasAoeDisable && hero1HasAoeDamage)) {
            score += STRONG_SYNERGY;
            reasons.add("Area-of-effect control + area-of-effect damage creates powerful teamfight combo");
        }
        
        // Check for complementary abilities that create wombo combos
        boolean hero1HasSetupAbility = hero1.getAbilities().stream().anyMatch(this::isSetupAbility);
        boolean hero2HasSetupAbility = hero2.getAbilities().stream().anyMatch(this::isSetupAbility);
        boolean hero1HasFollowupAbility = hero1.getAbilities().stream().anyMatch(this::isFollowupAbility);
        boolean hero2HasFollowupAbility = hero2.getAbilities().stream().anyMatch(this::isFollowupAbility);
        
        if (hero1HasSetupAbility && hero2HasFollowupAbility) {
            score += STRONG_SYNERGY;
            reasons.add(hero1.getLocalizedName() + " provides setup for " + 
                       hero2.getLocalizedName() + "'s follow-up abilities");
        } else if (hero2HasSetupAbility && hero1HasFollowupAbility) {
            score += STRONG_SYNERGY;
            reasons.add(hero2.getLocalizedName() + " provides setup for " + 
                       hero1.getLocalizedName() + "'s follow-up abilities");
        }
        
        // Check for save potential
        boolean hero1HasSaveAbility = hero1.getAbilities().stream().anyMatch(this::isSaveAbility);
        boolean hero2IsHighValueTarget = isHighValueTarget(hero2);
        
        if (hero1HasSaveAbility && hero2IsHighValueTarget) {
            score += MODERATE_SYNERGY;
            reasons.add(hero1.getLocalizedName() + " can save " + 
                       hero2.getLocalizedName() + " during fights");
        }
        
        return score;
    }
    
    // Helper methods for ability classification
    
    private boolean isControlAbility(Ability ability) {
        if (ability.getDescription() == null) return false;
        String desc = ability.getDescription().toLowerCase();
        return desc.contains("stun") || desc.contains("root") || desc.contains("slow") || 
               desc.contains("trap") || desc.contains("disable") || desc.contains("immobilize");
    }
    
    private boolean isDamageAbility(Ability ability) {
        if (ability.getDescription() == null) return false;
        String desc = ability.getDescription().toLowerCase();
        return desc.contains("damage") || desc.contains("nuke") || ability.getDamageType() != null;
    }
    
    private boolean isAmplifyingAbility(Ability ability) {
        if (ability.getDescription() == null) return false;
        String desc = ability.getDescription().toLowerCase();
        return desc.contains("amplif") || desc.contains("increas") || desc.contains("bonus damage") || 
               desc.contains("reduce") || desc.contains("armor") || desc.contains("magic resistance");
    }
    
    private boolean isAoeControlAbility(Ability ability) {
        if (ability.getDescription() == null) return false;
        String desc = ability.getDescription().toLowerCase();
        return isControlAbility(ability) && (desc.contains("area") || desc.contains("aoe") || 
               desc.contains("all enemies") || desc.contains("in a radius"));
    }
    
    private boolean isAoeDamageAbility(Ability ability) {
        if (ability.getDescription() == null) return false;
        String desc = ability.getDescription().toLowerCase();
        return isDamageAbility(ability) && (desc.contains("area") || desc.contains("aoe") || 
               desc.contains("all enemies") || desc.contains("in a radius"));
    }
    
    private boolean isSetupAbility(Ability ability) {
        return isControlAbility(ability) || ability.getDescription().toLowerCase().contains("position");
    }
    
    private boolean isFollowupAbility(Ability ability) {
        return isDamageAbility(ability) || 
               ability.getDescription().toLowerCase().contains("bonus") || 
               ability.getDescription().toLowerCase().contains("amplif");
    }
    
    private boolean isSaveAbility(Ability ability) {
        if (ability.getDescription() == null) return false;
        String desc = ability.getDescription().toLowerCase();
        return desc.contains("heal") || desc.contains("save") || desc.contains("protect") || 
               desc.contains("shield") || desc.contains("barrier") || desc.contains("immune");
    }
    
    private boolean isHighValueTarget(Hero hero) {
        return hero.getRoles() != null && 
              (hero.getRoles().contains("Carry") || hero.getRoles().contains("Core"));
    }
    
    private boolean isSilencingAbility(Ability ability) {
        if (ability.getDescription() == null) return false;
        String desc = ability.getDescription().toLowerCase();
        return desc.contains("silence") || desc.contains("mute") || desc.contains("prevent casting");
    }
    
    private boolean isPureDamageAbility(Ability ability) {
        return "pure".equals(ability.getDamageType());
    }
    
    private boolean isBreakAbility(Ability ability) {
        if (ability.getDescription() == null) return false;
        String desc = ability.getDescription().toLowerCase();
        return desc.contains("break") || desc.contains("disable passive");
    }
    
    private boolean isBurstDamageAbility(Ability ability) {
        if (ability.getDescription() == null) return false;
        String desc = ability.getDescription().toLowerCase();
        return desc.contains("burst") || (desc.contains("damage") && !desc.contains("over time"));
    }
    
    /**
     * Result class for synergy analysis
     */
    public static class SynergyAnalysis {
        private final double score;
        private final List<String> reasons;
        
        public SynergyAnalysis(double score, List<String> reasons) {
            this.score = score;
            this.reasons = reasons;
        }
        
        public double getScore() {
            return score;
        }
        
        public List<String> getReasons() {
            return reasons;
        }
    }
    
    /**
     * Result class for counter analysis
     */
    public static class CounterAnalysis {
        private final double score;
        private final List<String> reasons;
        
        public CounterAnalysis(double score, List<String> reasons) {
            this.score = score;
            this.reasons = reasons;
        }
        
        public double getScore() {
            return score;
        }
        
        public List<String> getReasons() {
            return reasons;
        }
    }
}