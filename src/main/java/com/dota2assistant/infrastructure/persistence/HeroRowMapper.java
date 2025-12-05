package com.dota2assistant.infrastructure.persistence;

import com.dota2assistant.domain.model.Attribute;
import com.dota2assistant.domain.model.AttackType;
import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.model.HeroAttributes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps database rows to Hero domain objects.
 */
class HeroRowMapper {
    
    private final ObjectMapper objectMapper;
    
    HeroRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    Hero mapRow(ResultSet rs) throws SQLException {
        try {
            String attrJson = rs.getString("attributes");
            HeroAttributes attrs = attrJson != null && !attrJson.isBlank()
                ? objectMapper.readValue(attrJson, HeroAttributes.class)
                : HeroAttributes.defaults();
                
            return new Hero(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("localized_name"),
                Attribute.valueOf(rs.getString("primary_attr")),
                AttackType.valueOf(rs.getString("attack_type")),
                parseRoles(rs.getString("roles")),
                parsePositions(rs.getString("positions")),
                attrs,
                rs.getString("image_url"),
                rs.getString("icon_url"),
                List.of()
            );
        } catch (JsonProcessingException e) {
            throw new RepositoryException("Failed to parse hero JSON", e);
        }
    }
    
    private List<String> parseRoles(String rolesJson) throws JsonProcessingException {
        if (rolesJson == null || rolesJson.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(rolesJson, new TypeReference<>() {});
    }
    
    private Map<Integer, Double> parsePositions(String posJson) throws JsonProcessingException {
        if (posJson == null || posJson.isBlank()) {
            return Map.of();
        }
        Map<String, Double> stringMap = objectMapper.readValue(posJson, new TypeReference<>() {});
        return stringMap.entrySet().stream()
            .collect(Collectors.toMap(
                e -> Integer.parseInt(e.getKey()),
                Map.Entry::getValue
            ));
    }
}

