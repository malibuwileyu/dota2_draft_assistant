package com.dota2assistant.infrastructure.persistence;

import com.dota2assistant.domain.model.Attribute;
import com.dota2assistant.domain.model.Hero;
import com.dota2assistant.domain.repository.HeroRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite implementation of HeroRepository.
 */
@Repository
public class SqliteHeroRepository implements HeroRepository {
    
    private static final String SELECT_COLS = """
        id, name, localized_name, primary_attr, attack_type,
        roles, positions, attributes, image_url, icon_url
        """;
    
    private final DataSource dataSource;
    private final HeroRowMapper rowMapper;
    
    public SqliteHeroRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.rowMapper = new HeroRowMapper(objectMapper);
    }
    
    @Override
    public List<Hero> findAll() {
        String sql = "SELECT " + SELECT_COLS + " FROM heroes ORDER BY localized_name";
        return executeQuery(sql, stmt -> {});
    }
    
    @Override
    public Optional<Hero> findById(int id) {
        String sql = "SELECT " + SELECT_COLS + " FROM heroes WHERE id = ?";
        List<Hero> result = executeQuery(sql, stmt -> stmt.setInt(1, id));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }
    
    @Override
    public Optional<Hero> findByName(String name) {
        String sql = "SELECT " + SELECT_COLS + " FROM heroes WHERE name = ?";
        List<Hero> result = executeQuery(sql, stmt -> stmt.setString(1, name));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }
    
    @Override
    public List<Hero> findByAttribute(Attribute attribute) {
        String sql = "SELECT " + SELECT_COLS + " FROM heroes WHERE primary_attr = ? ORDER BY localized_name";
        return executeQuery(sql, stmt -> stmt.setString(1, attribute.name()));
    }
    
    @Override
    public List<Hero> findByRole(String role) {
        String sql = "SELECT " + SELECT_COLS + " FROM heroes WHERE roles LIKE ? ORDER BY localized_name";
        return executeQuery(sql, stmt -> stmt.setString(1, "%" + role + "%"));
    }
    
    @Override
    public List<Hero> search(String query) {
        String sql = "SELECT " + SELECT_COLS + " FROM heroes WHERE LOWER(localized_name) LIKE LOWER(?) ORDER BY localized_name";
        return executeQuery(sql, stmt -> stmt.setString(1, "%" + query + "%"));
    }
    
    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM heroes";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to count heroes", e);
        }
    }
    
    private List<Hero> executeQuery(String sql, StatementSetter setter) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            setter.set(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Hero> heroes = new ArrayList<>();
                while (rs.next()) {
                    heroes.add(rowMapper.mapRow(rs));
                }
                return heroes;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to query heroes", e);
        }
    }
    
    @FunctionalInterface
    private interface StatementSetter {
        void set(PreparedStatement stmt) throws SQLException;
    }
}
