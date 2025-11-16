package com.dota2assistant.data.repository;

import com.dota2assistant.auth.SteamUser;
import com.dota2assistant.data.db.DatabaseManager;
import com.dota2assistant.data.model.Session;
import com.dota2assistant.data.model.UserPreference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Repository for user profile and authentication-related data.
 */
@Repository
public class UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);

    private final DatabaseManager databaseManager;
    private final ObjectMapper objectMapper;

    public UserRepository(DatabaseManager databaseManager, ObjectMapper objectMapper) {
        this.databaseManager = databaseManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Save or update a Steam user in the database.
     * 
     * @param steamUser The Steam user to save
     * @return The database ID for the user
     */
    public long saveUser(SteamUser steamUser) {
        try {
            // Check if user exists
            String checkSql = "SELECT account_id FROM players WHERE steam_id = ?";
            Long accountId = databaseManager.queryForObject(
                checkSql,
                rs -> rs.getLong("account_id"),
                steamUser.getSteamId()
            );
            
            if (accountId != null) {
                // Update existing user
                String updateSql = "UPDATE players SET " +
                    "username = ?, personaname = ?, avatar = ?, avatar_medium_url = ?, " +
                    "avatar_full_url = ?, profile_url = ?, time_created = ?, " +
                    "last_logoff = ?, updated_date = CURRENT_TIMESTAMP " +
                    "WHERE steam_id = ?";
                
                databaseManager.executeUpdate(updateSql,
                    steamUser.getUsername(),
                    steamUser.getUsername(),  // Using username for personaname too
                    steamUser.getAvatarUrl(),
                    steamUser.getAvatarMediumUrl(),
                    steamUser.getAvatarFullUrl(),
                    steamUser.getProfileUrl(),
                    steamUser.getTimeCreated(),
                    steamUser.getLastLogoff(),
                    steamUser.getSteamId()
                );
                
                return accountId;
            } else {
                // Insert new user
                // Parse steamId to long for account_id
                long steamId = Long.parseLong(steamUser.getSteamId());
                
                String insertSql = "INSERT INTO players " +
                    "(account_id, steam_id, username, personaname, avatar, avatar_medium_url, " +
                    "avatar_full_url, profile_url, time_created, last_logoff) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
                return databaseManager.executeInsert(insertSql,
                    steamId,
                    steamUser.getSteamId(),
                    steamUser.getUsername(),
                    steamUser.getUsername(),
                    steamUser.getAvatarUrl(),
                    steamUser.getAvatarMediumUrl(),
                    steamUser.getAvatarFullUrl(),
                    steamUser.getProfileUrl(),
                    steamUser.getTimeCreated(),
                    steamUser.getLastLogoff()
                );
            }
        } catch (SQLException e) {
            logger.error("Failed to save user", e);
            throw new RuntimeException("Failed to save user", e);
        }
    }

    /**
     * Get a user by Steam ID.
     * 
     * @param steamId The Steam ID to look up
     * @return The Steam user if found
     */
    public Optional<SteamUser> getUserBySteamId(String steamId) {
        try {
            String sql = "SELECT * FROM players WHERE steam_id = ?";
            
            List<SteamUser> users = databaseManager.query(
                sql,
                this::mapSteamUser,
                steamId
            );
            
            return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
        } catch (SQLException e) {
            logger.error("Failed to get user by Steam ID", e);
            return Optional.empty();
        }
    }

    /**
     * Create an authentication session for a user.
     * 
     * @param accountId The user's account ID
     * @param ipAddress The IP address of the client
     * @param userAgent The user agent of the client
     * @return The created session with token
     */
    public Session createSession(long accountId, String ipAddress, String userAgent) {
        try {
            // Generate a session token
            String sessionToken = UUID.randomUUID().toString();
            
            // Set expiration time (e.g., 30 days from now)
            Timestamp expireTime = Timestamp.from(Instant.now().plusSeconds(30 * 24 * 60 * 60));
            
            String sql = "INSERT INTO auth_sessions " +
                "(account_id, session_token, expire_time, ip_address, user_agent) " +
                "VALUES (?, ?, ?, ?, ?)";
            
            long id = databaseManager.executeInsert(sql,
                accountId,
                sessionToken,
                expireTime,
                ipAddress,
                userAgent
            );
            
            // Log the login
            logLogin(accountId, ipAddress, userAgent, "steam_openid", true);
            
            // Create and return the session
            Session session = new Session();
            session.setId(id);
            session.setAccountId(accountId);
            session.setSessionToken(sessionToken);
            session.setCreatedTime(new Date());
            session.setExpireTime(expireTime);
            session.setLastActive(new Date());
            session.setActive(true);
            
            return session;
        } catch (SQLException e) {
            logger.error("Failed to create session", e);
            throw new RuntimeException("Failed to create session", e);
        }
    }

    /**
     * Get a session by token.
     * 
     * @param sessionToken The session token to look up
     * @return The session if found and valid
     */
    public Optional<Session> getSessionByToken(String sessionToken) {
        try {
            String sql = "SELECT * FROM auth_sessions WHERE session_token = ? AND is_active = TRUE AND expire_time > CURRENT_TIMESTAMP";
            
            List<Session> sessions = databaseManager.query(
                sql,
                this::mapSession,
                sessionToken
            );
            
            if (!sessions.isEmpty()) {
                // Update the last active time
                updateSessionActivity(sessions.get(0).getId());
                return Optional.of(sessions.get(0));
            }
            
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to get session by token", e);
            return Optional.empty();
        }
    }

    /**
     * Update the last active time for a session.
     * 
     * @param sessionId The session ID
     */
    public void updateSessionActivity(long sessionId) {
        try {
            String sql = "UPDATE auth_sessions SET last_active = CURRENT_TIMESTAMP WHERE id = ?";
            databaseManager.executeUpdate(sql, sessionId);
        } catch (SQLException e) {
            logger.error("Failed to update session activity", e);
        }
    }

    /**
     * Invalidate a session.
     * 
     * @param sessionToken The session token to invalidate
     */
    public void invalidateSession(String sessionToken) {
        try {
            String sql = "UPDATE auth_sessions SET is_active = FALSE WHERE session_token = ?";
            databaseManager.executeUpdate(sql, sessionToken);
        } catch (SQLException e) {
            logger.error("Failed to invalidate session", e);
        }
    }

    /**
     * Log a login attempt.
     * 
     * @param accountId The user's account ID
     * @param ipAddress The IP address of the client
     * @param userAgent The user agent of the client
     * @param loginMethod The login method used
     * @param success Whether the login was successful
     */
    public void logLogin(long accountId, String ipAddress, String userAgent, String loginMethod, boolean success) {
        try {
            String sql = "INSERT INTO login_history " +
                "(account_id, login_ip, login_method, success, user_agent) " +
                "VALUES (?, ?, ?, ?, ?)";
            
            databaseManager.executeUpdate(sql,
                accountId,
                ipAddress,
                loginMethod,
                success,
                userAgent
            );
        } catch (SQLException e) {
            logger.error("Failed to log login", e);
        }
    }

    /**
     * Save a user preference.
     * 
     * @param accountId The user's account ID
     * @param preferenceName The preference name
     * @param preferenceValue The preference value
     */
    public void savePreference(long accountId, String preferenceName, String preferenceValue) {
        try {
            // Get the preference type ID
            String getTypeSql = "SELECT id FROM user_preference_types WHERE name = ?";
            Integer typeId = databaseManager.queryForObject(
                getTypeSql,
                rs -> rs.getInt("id"),
                preferenceName
            );
            
            if (typeId == null) {
                // Create the preference type if it doesn't exist
                String insertTypeSql = "INSERT INTO user_preference_types (name, description) VALUES (?, ?)";
                typeId = (int) databaseManager.executeInsert(insertTypeSql, preferenceName, "User defined preference");
            }
            
            // Check if the preference exists
            String checkSql = "SELECT id FROM user_preferences WHERE account_id = ? AND preference_type_id = ?";
            Integer preferenceId = databaseManager.queryForObject(
                checkSql,
                rs -> rs.getInt("id"),
                accountId,
                typeId
            );
            
            if (preferenceId != null) {
                // Update existing preference
                String updateSql = "UPDATE user_preferences SET value = ?, updated_date = CURRENT_TIMESTAMP WHERE id = ?";
                databaseManager.executeUpdate(updateSql, preferenceValue, preferenceId);
            } else {
                // Insert new preference
                String insertSql = "INSERT INTO user_preferences (account_id, preference_type_id, value) VALUES (?, ?, ?)";
                databaseManager.executeUpdate(insertSql, accountId, typeId, preferenceValue);
            }
        } catch (SQLException e) {
            logger.error("Failed to save preference", e);
        }
    }

    /**
     * Get all preferences for a user.
     * 
     * @param accountId The user's account ID
     * @return The user's preferences
     */
    public Map<String, UserPreference> getUserPreferences(long accountId) {
        try {
            String sql = "SELECT pt.name, pt.description, pt.default_value, up.value, up.updated_date " +
                "FROM user_preference_types pt " +
                "LEFT JOIN user_preferences up ON pt.id = up.preference_type_id AND up.account_id = ?";
            
            List<UserPreference> preferences = databaseManager.query(
                sql,
                rs -> {
                    UserPreference pref = new UserPreference();
                    pref.setName(rs.getString("name"));
                    pref.setDescription(rs.getString("description"));
                    
                    // Use the user's value if set, otherwise use the default
                    String value = rs.getString("value");
                    if (value == null) {
                        value = rs.getString("default_value");
                    }
                    pref.setValue(value);
                    
                    Timestamp updated = rs.getTimestamp("updated_date");
                    if (updated != null) {
                        pref.setUpdatedDate(updated);
                    }
                    
                    return pref;
                },
                accountId
            );
            
            // Convert to map for easy access
            Map<String, UserPreference> preferencesMap = new HashMap<>();
            for (UserPreference pref : preferences) {
                preferencesMap.put(pref.getName(), pref);
            }
            
            return preferencesMap;
        } catch (SQLException e) {
            logger.error("Failed to get user preferences", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a specific preference for a user.
     * 
     * @param accountId The user's account ID
     * @param preferenceName The preference name
     * @return The preference value if found
     */
    public Optional<String> getPreference(long accountId, String preferenceName) {
        try {
            String sql = "SELECT up.value, pt.default_value " +
                "FROM user_preference_types pt " +
                "LEFT JOIN user_preferences up ON pt.id = up.preference_type_id AND up.account_id = ? " +
                "WHERE pt.name = ?";
            
            return Optional.ofNullable(databaseManager.queryForObject(
                sql,
                rs -> {
                    String value = rs.getString("value");
                    if (value == null) {
                        value = rs.getString("default_value");
                    }
                    return value;
                },
                accountId,
                preferenceName
            ));
        } catch (SQLException e) {
            logger.error("Failed to get preference", e);
            return Optional.empty();
        }
    }

    /**
     * Save a user skill metric.
     * 
     * @param accountId The user's account ID
     * @param metricName The metric name
     * @param metricValue The metric value
     */
    public void saveSkillMetric(long accountId, String metricName, double metricValue) {
        try {
            // Check if the metric exists
            String checkSql = "SELECT id FROM user_skill_metrics WHERE account_id = ? AND metric_name = ?";
            Integer metricId = databaseManager.queryForObject(
                checkSql,
                rs -> rs.getInt("id"),
                accountId,
                metricName
            );
            
            if (metricId != null) {
                // Update existing metric
                String updateSql = "UPDATE user_skill_metrics SET metric_value = ?, last_updated = CURRENT_TIMESTAMP WHERE id = ?";
                databaseManager.executeUpdate(updateSql, metricValue, metricId);
            } else {
                // Insert new metric
                String insertSql = "INSERT INTO user_skill_metrics (account_id, metric_name, metric_value) VALUES (?, ?, ?)";
                databaseManager.executeUpdate(insertSql, accountId, metricName, metricValue);
            }
        } catch (SQLException e) {
            logger.error("Failed to save skill metric", e);
        }
    }

    /**
     * Get all skill metrics for a user.
     * 
     * @param accountId The user's account ID
     * @return The user's skill metrics
     */
    public Map<String, Double> getUserSkillMetrics(long accountId) {
        try {
            String sql = "SELECT metric_name, metric_value FROM user_skill_metrics WHERE account_id = ?";
            
            List<Map.Entry<String, Double>> metrics = databaseManager.query(
                sql,
                rs -> new AbstractMap.SimpleEntry<>(
                    rs.getString("metric_name"),
                    rs.getDouble("metric_value")
                ),
                accountId
            );
            
            // Convert to map for easy access
            Map<String, Double> metricsMap = new HashMap<>();
            for (Map.Entry<String, Double> entry : metrics) {
                metricsMap.put(entry.getKey(), entry.getValue());
            }
            
            return metricsMap;
        } catch (SQLException e) {
            logger.error("Failed to get user skill metrics", e);
            return Collections.emptyMap();
        }
    }

    // Helper methods for mapping database rows to objects

    private SteamUser mapSteamUser(ResultSet rs) throws SQLException {
        SteamUser user = new SteamUser();
        user.setSteamId(rs.getString("steam_id"));
        user.setUsername(rs.getString("username"));
        user.setAvatarUrl(rs.getString("avatar"));
        user.setAvatarMediumUrl(rs.getString("avatar_medium_url"));
        user.setAvatarFullUrl(rs.getString("avatar_full_url"));
        user.setProfileUrl(rs.getString("profile_url"));
        user.setTimeCreated(rs.getLong("time_created"));
        user.setLastLogoff(rs.getLong("last_logoff"));
        return user;
    }

    private Session mapSession(ResultSet rs) throws SQLException {
        Session session = new Session();
        session.setId(rs.getLong("id"));
        session.setAccountId(rs.getLong("account_id"));
        session.setSessionToken(rs.getString("session_token"));
        session.setCreatedTime(rs.getTimestamp("created_time"));
        session.setExpireTime(rs.getTimestamp("expire_time"));
        session.setLastActive(rs.getTimestamp("last_active"));
        session.setIpAddress(rs.getString("ip_address"));
        session.setUserAgent(rs.getString("user_agent"));
        session.setDeviceInfo(rs.getString("device_info"));
        session.setActive(rs.getBoolean("is_active"));
        return session;
    }
}