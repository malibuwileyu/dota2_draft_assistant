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
import java.util.ArrayList;
import java.util.List;

/**
 * Imports hero counter data from OpenDota API into SQLite.
 * Counter score = win rate when playing AGAINST that hero.
 * Higher score = hero is a better counter.
 */
@Component
public class CounterDataImporter {
    
    private static final Logger log = LoggerFactory.getLogger(CounterDataImporter.class);
    private static final int MIN_GAMES_THRESHOLD = 10; // Ignore matchups with few games
    
    private final DataSource dataSource;
    private final OpenDotaClient openDotaClient;
    
    public CounterDataImporter(DataSource dataSource, OpenDotaClient openDotaClient) {
        this.dataSource = dataSource;
        this.openDotaClient = openDotaClient;
    }
    
    /**
     * Import counter data for all heroes from OpenDota.
     * @return Number of matchup records imported
     */
    public int importCounterData() {
        log.info("Starting counter data import from OpenDota API...");
        
        List<Integer> heroIds = getHeroIds();
        log.info("Found {} heroes to process", heroIds.size());
        
        int totalImported = 0;
        int processed = 0;
        
        for (int heroId : heroIds) {
            try {
                int imported = importMatchupsForHero(heroId);
                totalImported += imported;
                processed++;
                
                if (processed % 10 == 0) {
                    log.info("Progress: {}/{} heroes processed ({} matchups)", 
                             processed, heroIds.size(), totalImported);
                }
            } catch (Exception e) {
                log.warn("Failed to import matchups for hero {}: {}", heroId, e.getMessage());
            }
        }
        
        log.info("Counter data import complete: {} matchups from {} heroes", 
                 totalImported, processed);
        return totalImported;
    }
    
    private List<Integer> getHeroIds() {
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id FROM heroes ORDER BY id")) {
            while (rs.next()) {
                ids.add(rs.getInt("id"));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to get hero IDs", e);
        }
        return ids;
    }
    
    private int importMatchupsForHero(int heroId) throws IOException, InterruptedException {
        List<OpenDotaClient.HeroMatchup> matchups = openDotaClient.getHeroMatchups(heroId);
        
        String sql = """
            INSERT INTO hero_counters (hero_id, counter_id, games, wins, counter_score, updated_at)
            VALUES (?, ?, ?, ?, ?, datetime('now'))
            ON CONFLICT(hero_id, counter_id) DO UPDATE SET
                games = excluded.games,
                wins = excluded.wins,
                counter_score = excluded.counter_score,
                updated_at = datetime('now')
            """;
        
        int imported = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            for (OpenDotaClient.HeroMatchup matchup : matchups) {
                if (matchup.games_played() < MIN_GAMES_THRESHOLD) continue;
                
                // Counter score: how well heroId does AGAINST counter_id
                // High win rate = heroId counters counter_id
                double counterScore = matchup.winRate();
                
                ps.setInt(1, heroId);
                ps.setInt(2, matchup.hero_id());
                ps.setInt(3, matchup.games_played());
                ps.setInt(4, matchup.wins());
                ps.setDouble(5, counterScore);
                ps.executeUpdate();
                imported++;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to insert matchups for hero " + heroId, e);
        }
        
        return imported;
    }
    
    /**
     * Check if counter data needs to be imported (empty or stale).
     */
    public boolean needsImport() {
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM hero_counters")) {
            return rs.next() && rs.getInt(1) == 0;
        } catch (SQLException e) {
            return true;
        }
    }
    
    /**
     * Get the last update time for counter data.
     */
    public String getLastUpdateTime() {
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(updated_at) FROM hero_counters")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            return null;
        }
    }
}

