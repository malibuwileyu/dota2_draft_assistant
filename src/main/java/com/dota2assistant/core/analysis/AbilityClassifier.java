package com.dota2assistant.core.analysis;

import com.dota2assistant.data.model.Ability;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.InnateAbility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class for classifying hero abilities into functional categories.
 * This helps identify teamfight abilities, control abilities, burst damage, etc.
 */
@Component
public class AbilityClassifier {
    
    private static final Logger logger = LoggerFactory.getLogger(AbilityClassifier.class);
    
    // Regex patterns for ability type detection
    private static final Pattern TEAMFIGHT_PATTERN = Pattern.compile(
            "(?i)(area|aoe|around|nearby|radius|surround|all enemies|all units|within|cone|zone|circle|all heroes).*" +
            "(stun|slow|silence|root|immobilize|damage|control|freeze|fear|taunt|disarm|sleep)");
    
    private static final Pattern AOE_CONTROL_PATTERN = Pattern.compile(
            "(?i)(area|aoe|around|nearby|radius|surround|all enemies|all units|within|cone|zone|circle|all heroes).*" +
            "(stun|root|silence|immobilize|freeze|fear|taunt|disarm|sleep)");
    
    private static final Pattern AOE_DAMAGE_PATTERN = Pattern.compile(
            "(?i)(area|aoe|around|nearby|radius|surround|all enemies|all units|within|cone|zone|circle|all heroes).*" +
            "(damage|destroy|burn|explode|blast|nuke)");
    
    private static final Pattern INITIATION_PATTERN = Pattern.compile(
            "(?i)(blink|jump|leap|teleport|charge|dash)");
    
    private static final Pattern SAVE_PATTERN = Pattern.compile(
            "(?i)(heal|save|restore|protect|shield|barrier|immune|invulnerable|health|life|armor|defense|ward)");
    
    private static final Pattern MOBILITY_PATTERN = Pattern.compile(
            "(?i)(blink|jump|leap|teleport|charge|dash|move speed|mobility|haste|swift|accelerate)");
    
    private static final Pattern VISION_PATTERN = Pattern.compile(
            "(?i)(vision|sight|reveal|detect|scout|invisible|invisibility|see)");
    
    private static final Pattern BURST_DAMAGE_PATTERN = Pattern.compile(
            "(?i)(burst|nuke|instant|high damage|massive damage)");
    
    private static final Pattern SUSTAINED_DAMAGE_PATTERN = Pattern.compile(
            "(?i)(over time|dot|periodic|duration|tick|per second|dps|sustained)");
    
    private static final Pattern BKB_PIERCE_PATTERN = Pattern.compile(
            "(?i)(pierces|through|spell immunity|avatar|black king bar|bkb)");
    
    // Known teamfight ultimates (hardcoded for reliability)
    private static final Set<String> KNOWN_TEAMFIGHT_ULTS = new HashSet<>(Arrays.asList(
            "ravage",           // Tidehunter
            "black hole",       // Enigma
            "reverse polarity", // Magnus
            "firestorm",        // Underlord
            "pit of malice",    // Underlord
            "chaotic offering", // Warlock
            "echo slam",        // Earthshaker
            "sonic wave",       // Queen of Pain
            "wall of replica",  // Dark Seer
            "vacuum",           // Dark Seer
            "chronosphere",     // Faceless Void
            "dream coil",       // Puck
            "overgrowth",       // Treant Protector
            "static storm",     // Disruptor
            "rolling thunder",  // Pangolier
            "epicenter",        // Sand King
            "freezing field",   // Crystal Maiden
            "supernova",        // Phoenix
            "winter's curse",   // Winter Wyvern
            "egg",              // Phoenix
            "stampede",         // Centaur Warrunner
            "call down",        // Gyrocopter
            "rp",               // Magnus (shorthand)
            "roar"              // Beastmaster
    ));
    
    /**
     * Classify a hero's abilities into different functional categories
     * @param hero The hero to classify abilities for
     * @return A map of ability classifications with scores
     */
    public HeroAbilityProfile classifyHeroAbilities(Hero hero) {
        if (hero == null) {
            return new HeroAbilityProfile();
        }
        
        HeroAbilityProfile profile = new HeroAbilityProfile();
        
        // Check regular abilities
        if (hero.getAbilities() != null) {
            for (Ability ability : hero.getAbilities()) {
                classifyAbility(ability, profile);
            }
        }
        
        // Check innate abilities
        if (hero.getInnateAbilities() != null) {
            for (InnateAbility ability : hero.getInnateAbilities()) {
                classifyInnateAbility(ability, profile);
            }
        }
        
        // Add inherent hero characteristics
        addInherentCharacteristics(hero, profile);
        
        return profile;
    }
    
    /**
     * Classify a single ability
     * @param ability The ability to classify
     * @param profile The profile to update with classification
     */
    private void classifyAbility(Ability ability, HeroAbilityProfile profile) {
        if (ability == null || ability.getDescription() == null) {
            return;
        }
        
        String name = ability.getName().toLowerCase();
        String description = ability.getDescription().toLowerCase();
        String type = ability.getType();
        
        // Check known teamfight ultimates
        for (String teamfightUlt : KNOWN_TEAMFIGHT_ULTS) {
            if (name.contains(teamfightUlt)) {
                profile.teamfightScore += 1.0;
                profile.teamfightAbilities.add(ability.getName());
                break;
            }
        }
        
        // Check for teamfight abilities using regex
        if (TEAMFIGHT_PATTERN.matcher(description).find()) {
            profile.teamfightScore += 0.7;
            profile.teamfightAbilities.add(ability.getName());
        }
        
        // Check for AoE control
        if (AOE_CONTROL_PATTERN.matcher(description).find()) {
            profile.controlScore += 0.8;
            profile.controlAbilities.add(ability.getName());
        }
        // Check for single-target control
        else if (description.matches("(?i).*(stun|root|silence|hex|disable|immobilize).*")) {
            profile.controlScore += 0.4;
            profile.controlAbilities.add(ability.getName());
        }
        
        // Check for AoE damage
        if (AOE_DAMAGE_PATTERN.matcher(description).find()) {
            profile.damageScore += 0.6;
            if (BURST_DAMAGE_PATTERN.matcher(description).find()) {
                profile.burstDamageScore += 0.7;
                profile.burstAbilities.add(ability.getName());
            } else if (SUSTAINED_DAMAGE_PATTERN.matcher(description).find()) {
                profile.sustainedDamageScore += 0.7;
                profile.sustainedDamageAbilities.add(ability.getName());
            } else {
                // If not specified, assume it's somewhat balanced
                profile.burstDamageScore += 0.3;
                profile.sustainedDamageScore += 0.3;
            }
        }
        
        // Check for initiation
        if (INITIATION_PATTERN.matcher(description).find()) {
            profile.initiationScore += 0.7;
            profile.initiationAbilities.add(ability.getName());
        }
        
        // Check for save abilities
        if (SAVE_PATTERN.matcher(description).find()) {
            profile.saveScore += 0.8;
            profile.saveAbilities.add(ability.getName());
        }
        
        // Check for mobility
        if (MOBILITY_PATTERN.matcher(description).find()) {
            profile.mobilityScore += 0.7;
            profile.mobilityAbilities.add(ability.getName());
        }
        
        // Check for vision
        if (VISION_PATTERN.matcher(description).find()) {
            profile.visionScore += 0.8;
            profile.visionAbilities.add(ability.getName());
        }
        
        // Check for BKB piercing
        if (BKB_PIERCE_PATTERN.matcher(description).find()) {
            profile.bkbPierceScore += 0.9;
            profile.bkbPiercingAbilities.add(ability.getName());
        }
    }
    
    /**
     * Classify an innate ability
     * @param ability The innate ability to classify
     * @param profile The profile to update with classification
     */
    private void classifyInnateAbility(InnateAbility ability, HeroAbilityProfile profile) {
        if (ability == null || ability.getDescription() == null) {
            return;
        }
        
        String name = ability.getName().toLowerCase();
        String description = ability.getDescription().toLowerCase();
        
        // Check for teamfight contribution from innate abilities
        if (TEAMFIGHT_PATTERN.matcher(description).find()) {
            profile.teamfightScore += 0.3;  // Lower weight for innate abilities
        }
        
        // Check for other classifications with lower weights
        if (description.matches("(?i).*(stun|root|silence|hex|disable).*")) {
            profile.controlScore += 0.2;
        }
        
        if (description.matches("(?i).*(damage|nuke|burst).*")) {
            profile.damageScore += 0.2;
        }
        
        if (MOBILITY_PATTERN.matcher(description).find()) {
            profile.mobilityScore += 0.3;
        }
    }
    
    /**
     * Add inherent characteristics based on hero attributes and other properties
     * @param hero The hero to analyze
     * @param profile The profile to update
     */
    private void addInherentCharacteristics(Hero hero, HeroAbilityProfile profile) {
        // Example: Medusa is inherently a teamfight hero due to Split Shot and Stone Gaze
        if (hero.getLocalizedName().equalsIgnoreCase("Medusa")) {
            profile.teamfightScore += 0.6;
            profile.teamfightAbilities.add("Split Shot + Stone Gaze (Natural teamfight presence)");
        }
        
        // Heroes with naturally high mobility
        if (hero.getLocalizedName().equalsIgnoreCase("Weaver") || 
            hero.getLocalizedName().equalsIgnoreCase("Storm Spirit") ||
            hero.getLocalizedName().equalsIgnoreCase("Ember Spirit")) {
            profile.mobilityScore += 0.4;
        }
        
        // Heroes with naturally high sustained damage
        if (hero.getLocalizedName().equalsIgnoreCase("Luna") || 
            hero.getLocalizedName().equalsIgnoreCase("Gyrocopter") ||
            hero.getLocalizedName().equalsIgnoreCase("Spectre")) {
            profile.sustainedDamageScore += 0.3;
        }
    }
    
    /**
     * Container class for hero ability classifications
     */
    public static class HeroAbilityProfile {
        private double teamfightScore = 0.0;
        private double controlScore = 0.0;
        private double damageScore = 0.0;
        private double burstDamageScore = 0.0;
        private double sustainedDamageScore = 0.0;
        private double initiationScore = 0.0;
        private double saveScore = 0.0;
        private double mobilityScore = 0.0;
        private double visionScore = 0.0;
        private double bkbPierceScore = 0.0;
        
        private List<String> teamfightAbilities = new ArrayList<>();
        private List<String> controlAbilities = new ArrayList<>();
        private List<String> burstAbilities = new ArrayList<>();
        private List<String> sustainedDamageAbilities = new ArrayList<>();
        private List<String> initiationAbilities = new ArrayList<>();
        private List<String> saveAbilities = new ArrayList<>();
        private List<String> mobilityAbilities = new ArrayList<>();
        private List<String> visionAbilities = new ArrayList<>();
        private List<String> bkbPiercingAbilities = new ArrayList<>();
        
        public double getTeamfightScore() {
            return teamfightScore;
        }
        
        public double getControlScore() {
            return controlScore;
        }
        
        public double getDamageScore() {
            return damageScore;
        }
        
        public double getBurstDamageScore() {
            return burstDamageScore;
        }
        
        public double getSustainedDamageScore() {
            return sustainedDamageScore;
        }
        
        public double getInitiationScore() {
            return initiationScore;
        }
        
        public double getSaveScore() {
            return saveScore;
        }
        
        public double getMobilityScore() {
            return mobilityScore;
        }
        
        public double getVisionScore() {
            return visionScore;
        }
        
        public double getBkbPierceScore() {
            return bkbPierceScore;
        }
        
        public List<String> getTeamfightAbilities() {
            return teamfightAbilities;
        }
        
        public List<String> getControlAbilities() {
            return controlAbilities;
        }
        
        public List<String> getBurstAbilities() {
            return burstAbilities;
        }
        
        public List<String> getSustainedDamageAbilities() {
            return sustainedDamageAbilities;
        }
        
        public List<String> getInitiationAbilities() {
            return initiationAbilities;
        }
        
        public List<String> getSaveAbilities() {
            return saveAbilities;
        }
        
        public List<String> getMobilityAbilities() {
            return mobilityAbilities;
        }
        
        public List<String> getVisionAbilities() {
            return visionAbilities;
        }
        
        public List<String> getBkbPiercingAbilities() {
            return bkbPiercingAbilities;
        }
        
        /**
         * Check if hero has significant teamfight capability
         */
        public boolean hasSignificantTeamfight() {
            return teamfightScore >= 0.7 || !teamfightAbilities.isEmpty();
        }
        
        /**
         * Check if hero has significant control capability
         */
        public boolean hasSignificantControl() {
            return controlScore >= 0.7 || controlAbilities.size() >= 2;
        }
        
        /**
         * Get a summary of the hero's key strengths
         */
        public String getStrengthsSummary() {
            List<String> strengths = new ArrayList<>();
            
            if (teamfightScore >= 0.7) strengths.add("Teamfight");
            if (controlScore >= 0.7) strengths.add("Control");
            if (burstDamageScore >= 0.7) strengths.add("Burst Damage");
            if (sustainedDamageScore >= 0.7) strengths.add("Sustained Damage");
            if (initiationScore >= 0.7) strengths.add("Initiation");
            if (saveScore >= 0.7) strengths.add("Save");
            if (mobilityScore >= 0.7) strengths.add("Mobility");
            if (visionScore >= 0.7) strengths.add("Vision");
            if (bkbPierceScore >= 0.7) strengths.add("BKB Pierce");
            
            if (strengths.isEmpty()) {
                // Find top 2 strengths if none are above threshold
                double[] scores = {
                    teamfightScore, controlScore, burstDamageScore, sustainedDamageScore,
                    initiationScore, saveScore, mobilityScore, visionScore, bkbPierceScore
                };
                String[] names = {
                    "Teamfight", "Control", "Burst Damage", "Sustained Damage", 
                    "Initiation", "Save", "Mobility", "Vision", "BKB Pierce"
                };
                
                // Find highest scores
                int highest1 = 0;
                int highest2 = -1;
                for (int i = 1; i < scores.length; i++) {
                    if (scores[i] > scores[highest1]) {
                        highest2 = highest1;
                        highest1 = i;
                    } else if (highest2 == -1 || scores[i] > scores[highest2]) {
                        highest2 = i;
                    }
                }
                
                strengths.add(names[highest1]);
                if (highest2 != -1 && scores[highest2] > 0.3) {
                    strengths.add(names[highest2]);
                }
            }
            
            return String.join(", ", strengths);
        }
    }
}