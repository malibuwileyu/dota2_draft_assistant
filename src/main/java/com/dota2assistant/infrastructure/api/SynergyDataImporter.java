package com.dota2assistant.infrastructure.api;

import com.dota2assistant.infrastructure.persistence.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports hero synergy data from DotaBASED API into SQLite.
 * Synergy = win rate when both heroes on same team.
 */
@Component
public class SynergyDataImporter {
    
    private static final Logger log = LoggerFactory.getLogger(SynergyDataImporter.class);
    private static final int MIN_GAMES_THRESHOLD = 50;
    
    private final DataSource dataSource;
    private final DotaBasedClient dotaBasedClient;
    
    public SynergyDataImporter(DataSource dataSource, DotaBasedClient dotaBasedClient) {
        this.dataSource = dataSource;
        this.dotaBasedClient = dotaBasedClient;
    }
    
    /**
     * Import synergy data from DotaBASED.
     * @return Number of synergy records imported
     */
    public int importSynergyData() {
        log.info("Starting synergy data import from DotaBASED...");
        
        try {
            // Build hero name -> ID mapping
            Map<String, Integer> heroIds = buildHeroNameMap();
            log.info("Loaded {} heroes for mapping", heroIds.size());
            
            // Fetch synergy data
            List<DotaBasedClient.HeroPairStats> pairs = dotaBasedClient.getHeroSynergyData();
            
            // Import to database
            int imported = importPairs(pairs, heroIds);
            
            log.info("Synergy data import complete: {} records", imported);
            return imported;
        } catch (IOException | InterruptedException e) {
            throw new RepositoryException("Failed to fetch synergy data", e);
        }
    }
    
    private Map<String, Integer> buildHeroNameMap() {
        Map<String, Integer> map = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, localized_name FROM heroes")) {
            while (rs.next()) {
                map.put(rs.getString("localized_name"), rs.getInt("id"));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load hero names", e);
        }
        return map;
    }
    
    private int importPairs(List<DotaBasedClient.HeroPairStats> pairs, Map<String, Integer> heroIds) {
        String sql = """
            INSERT INTO hero_synergies (hero1_id, hero2_id, games, wins, synergy_score, updated_at)
            VALUES (?, ?, ?, ?, ?, datetime('now'))
            ON CONFLICT(hero1_id, hero2_id) DO UPDATE SET
                games = excluded.games,
                wins = excluded.wins,
                synergy_score = excluded.synergy_score,
                updated_at = datetime('now')
            """;
        
        int imported = 0;
        int skipped = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            for (DotaBasedClient.HeroPairStats pair : pairs) {
                Integer idA = heroIds.get(pair.heroA());
                Integer idB = heroIds.get(pair.heroB());
                
                if (idA == null || idB == null) {
                    skipped++;
                    continue;
                }
                
                if (pair.picks() < MIN_GAMES_THRESHOLD) {
                    skipped++;
                    continue;
                }
                
                // Ensure hero1_id < hero2_id (database constraint)
                int hero1 = Math.min(idA, idB);
                int hero2 = Math.max(idA, idB);
                
                ps.setInt(1, hero1);
                ps.setInt(2, hero2);
                ps.setInt(3, pair.picks());
                ps.setInt(4, pair.wins());
                ps.setDouble(5, pair.synergyScore());
                ps.executeUpdate();
                imported++;
            }
            
            log.info("Imported {} synergy pairs, skipped {} (missing hero or low games)", imported, skipped);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to insert synergy data", e);
        }
        
        return imported;
    }
    
    /**
     * Check if synergy data needs to be imported.
     */
    public boolean needsImport() {
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM hero_synergies")) {
            return rs.next() && rs.getInt(1) == 0;
        } catch (SQLException e) {
            return true;
        }
    }
}

