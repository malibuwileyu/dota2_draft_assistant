package com.dota2assistant.infrastructure.persistence;

import com.dota2assistant.domain.repository.SynergyRepository;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * SQLite implementation of SynergyRepository.
 * Uses Bayesian average to weight scores by sample size.
 */
@Repository
public class SqliteSynergyRepository implements SynergyRepository {
    
    // Confidence threshold for Bayesian average
    // With C=1000: 100 games → 91% prior, 1000 games → 50% prior, 10000 games → 9% prior
    private static final int CONFIDENCE_GAMES = 1000;
    private static final double PRIOR = 0.5;
    
    private final DataSource dataSource;
    
    public SqliteSynergyRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Compute Bayesian weighted score: pulls low-sample scores toward 0.5
     */
    private static String weightedScore(String scoreCol, String gamesCol) {
        return String.format("((%s * %s + %d * %f) / (%s + %d))",
            gamesCol, scoreCol, CONFIDENCE_GAMES, PRIOR, gamesCol, CONFIDENCE_GAMES);
    }
    
    @Override
    public Optional<Double> getSynergyScore(int heroId, int allyId) {
        String sql = "SELECT " + weightedScore("synergy_score", "games") + " as weighted " +
                     "FROM hero_synergies " +
                     "WHERE (hero1_id = ? AND hero2_id = ?) OR (hero1_id = ? AND hero2_id = ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, Math.min(heroId, allyId));
            stmt.setInt(2, Math.max(heroId, allyId));
            stmt.setInt(3, Math.min(allyId, heroId));
            stmt.setInt(4, Math.max(allyId, heroId));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(rs.getDouble(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to get synergy score", e);
        }
    }
    
    @Override
    public Optional<Double> getCounterScore(int heroId, int enemyId) {
        String sql = "SELECT " + weightedScore("counter_score", "games") + " as weighted " +
                     "FROM hero_counters WHERE hero_id = ? AND counter_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, heroId);
            stmt.setInt(2, enemyId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(rs.getDouble(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to get counter score", e);
        }
    }
    
    @Override
    public Map<Integer, Double> getAllSynergies(int heroId) {
        String sql = "SELECT CASE WHEN hero1_id = ? THEN hero2_id ELSE hero1_id END as ally_id, " +
                     weightedScore("synergy_score", "games") + " as weighted " +
                     "FROM hero_synergies WHERE hero1_id = ? OR hero2_id = ?";
        Map<Integer, Double> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, heroId);
            stmt.setInt(2, heroId);
            stmt.setInt(3, heroId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.put(rs.getInt(1), rs.getDouble(2));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to get all synergies", e);
        }
        return result;
    }
    
    @Override
    public Map<Integer, Double> getAllCounters(int heroId) {
        String sql = "SELECT counter_id, " + weightedScore("counter_score", "games") + " as weighted " +
                     "FROM hero_counters WHERE hero_id = ?";
        Map<Integer, Double> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, heroId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.put(rs.getInt(1), rs.getDouble(2));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to get all counters", e);
        }
        return result;
    }
    
    @Override
    public List<Integer> getBestSynergies(int heroId, int limit) {
        String sql = "SELECT CASE WHEN hero1_id = ? THEN hero2_id ELSE hero1_id END as ally_id " +
                     "FROM hero_synergies WHERE hero1_id = ? OR hero2_id = ? " +
                     "ORDER BY " + weightedScore("synergy_score", "games") + " DESC LIMIT ?";
        return queryIntList(sql, heroId, heroId, heroId, limit);
    }
    
    @Override
    public List<Integer> getBestCounters(int heroId, int limit) {
        String sql = "SELECT counter_id FROM hero_counters WHERE hero_id = ? " +
                     "ORDER BY " + weightedScore("counter_score", "games") + " DESC LIMIT ?";
        return queryIntList(sql, heroId, limit);
    }
    
    @Override
    public List<Integer> getCounteredBy(int heroId, int limit) {
        String sql = "SELECT hero_id FROM hero_counters WHERE counter_id = ? " +
                     "ORDER BY " + weightedScore("counter_score", "games") + " DESC LIMIT ?";
        return queryIntList(sql, heroId, limit);
    }
    
    private List<Integer> queryIntList(String sql, int... params) {
        List<Integer> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) stmt.setInt(i + 1, params[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to query synergy data", e);
        }
        return result;
    }
}
