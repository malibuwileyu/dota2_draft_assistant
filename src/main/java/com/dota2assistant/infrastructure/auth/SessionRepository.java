package com.dota2assistant.infrastructure.auth;

import com.dota2assistant.domain.model.UserSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for persisting user sessions to SQLite.
 * Stores session metadata including JWT token.
 */
@Repository
public class SessionRepository {

    private static final Logger log = LoggerFactory.getLogger(SessionRepository.class);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public SessionRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void ensureTableExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS user_sessions (
                steam_id TEXT PRIMARY KEY,
                persona_name TEXT NOT NULL,
                avatar_url TEXT,
                profile_url TEXT,
                mmr INTEGER,
                favorite_hero_ids TEXT,
                preferred_roles TEXT,
                created_at TEXT NOT NULL,
                last_login_at TEXT NOT NULL,
                expires_at TEXT NOT NULL,
                jwt_token TEXT
            )
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.debug("User sessions table ready");
        } catch (SQLException e) {
            log.error("Failed to create user_sessions table: {}", e.getMessage());
        }
    }

    /**
     * Saves or updates a user session.
     */
    public void save(UserSession session) {
        String sql = """
            INSERT OR REPLACE INTO user_sessions 
            (steam_id, persona_name, avatar_url, profile_url, mmr, 
             favorite_hero_ids, preferred_roles, created_at, last_login_at, expires_at, jwt_token)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String favoriteHeroIds = objectMapper.writeValueAsString(session.favoriteHeroIds());
            String preferredRoles = objectMapper.writeValueAsString(session.preferredRoles());

            stmt.setString(1, session.steamId());
            stmt.setString(2, session.personaName());
            stmt.setString(3, session.avatarUrl());
            stmt.setString(4, session.profileUrl());
            if (session.mmr() != null) {
                stmt.setInt(5, session.mmr());
            } else {
                stmt.setNull(5, Types.INTEGER);
            }
            stmt.setString(6, favoriteHeroIds);
            stmt.setString(7, preferredRoles);
            stmt.setString(8, session.createdAt().toString());
            stmt.setString(9, session.lastLoginAt().toString());
            stmt.setString(10, session.expiresAt().toString());
            stmt.setString(11, session.jwtToken());

            stmt.executeUpdate();
            log.debug("Saved session for Steam ID: {}", session.steamId());

        } catch (SQLException | JsonProcessingException e) {
            log.error("Failed to save session: {}", e.getMessage());
            throw new RuntimeException("Failed to save session", e);
        }
    }

    /**
     * Finds the most recent active session.
     */
    public Optional<UserSession> findActiveSession() {
        String sql = "SELECT * FROM user_sessions ORDER BY last_login_at DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                UserSession session = mapRow(rs);
                if (!session.isExpired()) {
                    return Optional.of(session);
                }
            }
            return Optional.empty();

        } catch (SQLException e) {
            log.error("Failed to find active session: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Finds a session by Steam ID.
     */
    public Optional<UserSession> findBySteamId(String steamId) {
        String sql = "SELECT * FROM user_sessions WHERE steam_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, steamId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();

        } catch (SQLException e) {
            log.error("Failed to find session by Steam ID: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Deletes a session by Steam ID.
     */
    public void delete(String steamId) {
        String sql = "DELETE FROM user_sessions WHERE steam_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, steamId);
            stmt.executeUpdate();
            log.debug("Deleted session for Steam ID: {}", steamId);

        } catch (SQLException e) {
            log.error("Failed to delete session: {}", e.getMessage());
        }
    }

    /**
     * Deletes all sessions (for complete logout).
     */
    public void deleteAll() {
        String sql = "DELETE FROM user_sessions";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(sql);
            log.debug("Deleted all sessions");

        } catch (SQLException e) {
            log.error("Failed to delete all sessions: {}", e.getMessage());
        }
    }

    private UserSession mapRow(ResultSet rs) throws SQLException {
        try {
            String favoriteHeroIdsJson = rs.getString("favorite_hero_ids");
            String preferredRolesJson = rs.getString("preferred_roles");

            List<Integer> favoriteHeroIds = favoriteHeroIdsJson != null ?
                objectMapper.readValue(favoriteHeroIdsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Integer.class)) :
                List.of();

            List<String> preferredRoles = preferredRolesJson != null ?
                objectMapper.readValue(preferredRolesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)) :
                List.of();

            Integer mmr = rs.getObject("mmr") != null ? rs.getInt("mmr") : null;
            String jwtToken = rs.getString("jwt_token");

            return new UserSession(
                rs.getString("steam_id"),
                rs.getString("persona_name"),
                rs.getString("avatar_url"),
                rs.getString("profile_url"),
                mmr,
                favoriteHeroIds,
                preferredRoles,
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("last_login_at")),
                Instant.parse(rs.getString("expires_at")),
                jwtToken
            );

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize session data: {}", e.getMessage());
            throw new SQLException("Failed to map session row", e);
        }
    }
}
