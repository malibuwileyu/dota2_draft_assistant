package com.dota2assistant.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Imports hero data from bundled JSON files into SQLite.
 */
@Component
public class HeroDataImporter {
    
    private static final Logger log = LoggerFactory.getLogger(HeroDataImporter.class);
    private static final String HEROES_JSON = "/data/heroes.json";
    
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final HeroJsonParser parser;
    
    public HeroDataImporter(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.parser = new HeroJsonParser(objectMapper);
    }
    
    /**
     * Imports all heroes from the bundled JSON file.
     * @return Number of heroes imported
     */
    public int importHeroes() {
        log.info("Starting hero data import from {}", HEROES_JSON);
        
        try (InputStream is = getClass().getResourceAsStream(HEROES_JSON)) {
            if (is == null) {
                throw new IOException("Hero data file not found: " + HEROES_JSON);
            }
            
            List<JsonNode> heroes = objectMapper.readValue(is, new TypeReference<>() {});
            
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    for (JsonNode hero : heroes) {
                        insertHero(conn, hero);
                    }
                    conn.commit();
                    log.info("Successfully imported {} heroes", heroes.size());
                    return heroes.size();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            }
        } catch (IOException | SQLException e) {
            throw new RepositoryException("Failed to import hero data", e);
        }
    }
    
    private void insertHero(Connection conn, JsonNode hero) throws SQLException, IOException {
        String sql = """
            INSERT INTO heroes (id, name, localized_name, primary_attr, attack_type, 
                               roles, positions, attributes, image_url, icon_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name, localized_name = excluded.localized_name,
                primary_attr = excluded.primary_attr, attack_type = excluded.attack_type,
                roles = excluded.roles, positions = excluded.positions,
                attributes = excluded.attributes, image_url = excluded.image_url,
                icon_url = excluded.icon_url, updated_at = datetime('now')
            """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hero.get("id").asInt());
            ps.setString(2, hero.get("name").asText());
            ps.setString(3, hero.get("localized_name").asText());
            ps.setString(4, parser.mapAttribute(hero.get("primary_attr").asText()).name());
            ps.setString(5, parser.mapAttackType(hero.get("attack_type").asText()).name());
            ps.setString(6, objectMapper.writeValueAsString(hero.get("roles")));
            ps.setString(7, parser.buildPositionsJson(hero));
            ps.setString(8, parser.buildAttributesJson(hero));
            ps.setString(9, hero.has("img") ? hero.get("img").asText() : null);
            ps.setString(10, hero.has("icon") ? hero.get("icon").asText() : null);
            ps.executeUpdate();
        }
    }
    
    /**
     * Checks if hero data needs to be imported (empty table).
     */
    public boolean needsImport() {
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM heroes")) {
            return rs.next() && rs.getInt(1) == 0;
        } catch (SQLException e) {
            return true;
        }
    }
}
