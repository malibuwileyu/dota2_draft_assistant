package com.dota2assistant.infrastructure.persistence;

import com.dota2assistant.domain.model.Attribute;
import com.dota2assistant.domain.model.AttackType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses hero JSON data and converts it to database-ready format.
 */
public class HeroJsonParser {
    
    private final ObjectMapper objectMapper;
    
    public HeroJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    public Attribute mapAttribute(String attr) {
        return switch (attr.toLowerCase()) {
            case "str", "strength" -> Attribute.STRENGTH;
            case "agi", "agility" -> Attribute.AGILITY;
            case "int", "intelligence" -> Attribute.INTELLIGENCE;
            case "all", "universal" -> Attribute.UNIVERSAL;
            default -> Attribute.UNIVERSAL;
        };
    }
    
    public AttackType mapAttackType(String type) {
        return switch (type.toLowerCase()) {
            case "melee" -> AttackType.MELEE;
            case "ranged" -> AttackType.RANGED;
            default -> AttackType.MELEE;
        };
    }
    
    public String buildPositionsJson(JsonNode hero) throws IOException {
        Map<String, Double> positions = new LinkedHashMap<>();
        int totalPicks = 0;
        int[] picksByPos = new int[6];
        
        for (int pos = 1; pos <= 5; pos++) {
            String pickKey = pos + "_pick";
            if (hero.has(pickKey)) {
                int picks = hero.get(pickKey).asInt();
                picksByPos[pos] = picks;
                totalPicks += picks;
            }
        }
        
        if (totalPicks > 0) {
            for (int pos = 1; pos <= 5; pos++) {
                double rate = (double) picksByPos[pos] / totalPicks;
                if (rate > 0.1) {
                    positions.put(String.valueOf(pos), Math.round(rate * 100) / 100.0);
                }
            }
        }
        
        return objectMapper.writeValueAsString(positions);
    }
    
    public String buildAttributesJson(JsonNode hero) throws IOException {
        Map<String, Object> attrs = new LinkedHashMap<>();
        
        addIfPresent(attrs, "baseStrength", hero, "base_str");
        addIfPresent(attrs, "baseAgility", hero, "base_agi");
        addIfPresent(attrs, "baseIntelligence", hero, "base_int");
        addIfPresent(attrs, "strGain", hero, "str_gain");
        addIfPresent(attrs, "agiGain", hero, "agi_gain");
        addIfPresent(attrs, "intGain", hero, "int_gain");
        addIfPresent(attrs, "baseHealth", hero, "base_health");
        addIfPresent(attrs, "baseHealthRegen", hero, "base_health_regen");
        addIfPresent(attrs, "baseMana", hero, "base_mana");
        addIfPresent(attrs, "baseManaRegen", hero, "base_mana_regen");
        addIfPresent(attrs, "baseArmor", hero, "base_armor");
        addIfPresent(attrs, "baseMagicResist", hero, "base_mr");
        addIfPresent(attrs, "baseAttackMin", hero, "base_attack_min");
        addIfPresent(attrs, "baseAttackMax", hero, "base_attack_max");
        addIfPresent(attrs, "attackRange", hero, "attack_range");
        addIfPresent(attrs, "moveSpeed", hero, "move_speed");
        
        return objectMapper.writeValueAsString(attrs);
    }
    
    private void addIfPresent(Map<String, Object> target, String key, JsonNode source, String sourceKey) {
        if (source.has(sourceKey) && !source.get(sourceKey).isNull()) {
            JsonNode value = source.get(sourceKey);
            if (value.isInt()) {
                target.put(key, value.asInt());
            } else if (value.isDouble() || value.isFloat()) {
                target.put(key, value.asDouble());
            } else {
                target.put(key, value.asText());
            }
        }
    }
}

