package com.dota2assistant.util;

import com.dota2assistant.AppConfig;
import com.dota2assistant.data.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Utility class for database operations.
 * This class provides helper methods for database management and troubleshooting.
 */
public class DatabaseTools {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseTools.class);

    /**
     * Private constructor to prevent instantiation
     */
    private DatabaseTools() {
        // Utility class, no instantiation
    }
    
    /**
     * Gets the current database version.
     * 
     * @return the current version number, or 0 if no version is set
     * @throws SQLException if a database error occurs
     */
    public static int getCurrentDatabaseVersion() throws SQLException {
        DatabaseManager dbManager = AppConfig.getDatabaseManager();
        String sql = "SELECT MAX(version) FROM db_version";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        return 0;
    }
    
    /**
     * Gets information about column existence in a table.
     * 
     * @param tableName the name of the table to check
     * @param columnName the name of the column to check
     * @return true if the column exists, false otherwise
     * @throws SQLException if a database error occurs
     */
    public static boolean columnExists(String tableName, String columnName) throws SQLException {
        DatabaseManager dbManager = AppConfig.getDatabaseManager();
        String sql = "SELECT 1 FROM information_schema.columns " +
                   "WHERE table_name = ? AND column_name = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, tableName.toLowerCase());
            stmt.setString(2, columnName.toLowerCase());
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    /**
     * Adds a column to a table if it doesn't already exist.
     * 
     * @param tableName the name of the table to modify
     * @param columnName the name of the column to add
     * @param columnType the SQL type of the column (e.g., "BOOLEAN DEFAULT FALSE")
     * @return true if the column was added, false if it already existed
     * @throws SQLException if a database error occurs
     */
    public static boolean addColumnIfNotExists(String tableName, String columnName, String columnType) throws SQLException {
        if (columnExists(tableName, columnName)) {
            logger.info("Column {} already exists in table {}", columnName, tableName);
            return false;
        }
        
        DatabaseManager dbManager = AppConfig.getDatabaseManager();
        String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s", 
                                 tableName, columnName, columnType);
        
        try {
            dbManager.executeUpdate(sql);
            logger.info("Added column {} to table {}", columnName, tableName);
            return true;
        } catch (SQLException e) {
            logger.error("Failed to add column {} to table {}: {}", columnName, tableName, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Counts rows in a table.
     * 
     * @param tableName the name of the table to count rows in
     * @return the number of rows in the table
     * @throws SQLException if a database error occurs
     */
    public static int countRows(String tableName) throws SQLException {
        DatabaseManager dbManager = AppConfig.getDatabaseManager();
        String sql = "SELECT COUNT(*) FROM " + tableName;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        return 0;
    }
    
    /**
     * Checks if a table exists in the database.
     * 
     * @param tableName the name of the table to check
     * @return true if the table exists, false otherwise
     * @throws SQLException if a database error occurs
     */
    public static boolean tableExists(String tableName) throws SQLException {
        DatabaseManager dbManager = AppConfig.getDatabaseManager();
        String sql = "SELECT 1 FROM information_schema.tables WHERE table_name = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, tableName.toLowerCase());
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    /**
     * Creates a new table in the database if it doesn't exist.
     * 
     * @param tableName the name of the table to create
     * @param tableDefinition the SQL CREATE TABLE statement without the "CREATE TABLE IF NOT EXISTS tableName" part
     * @return true if the table was created, false if it already existed
     * @throws SQLException if a database error occurs
     */
    public static boolean createTableIfNotExists(String tableName, String tableDefinition) throws SQLException {
        if (tableExists(tableName)) {
            logger.info("Table {} already exists", tableName);
            return false;
        }
        
        DatabaseManager dbManager = AppConfig.getDatabaseManager();
        String sql = String.format("CREATE TABLE IF NOT EXISTS %s %s", tableName, tableDefinition);
        
        try {
            dbManager.executeUpdate(sql);
            logger.info("Created table {}", tableName);
            return true;
        } catch (SQLException e) {
            logger.error("Failed to create table {}: {}", tableName, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Adds a missing column to the matches table to fix issues with match details functionality.
     * This can be called as a fallback in case the migration scripts didn't run correctly.
     * 
     * @throws SQLException if a database error occurs
     */
    public static void fixMatchesTable() throws SQLException {
        // Add has_details column
        addColumnIfNotExists("matches", "has_details", "BOOLEAN DEFAULT FALSE");
        
        // Add lobby_type column
        addColumnIfNotExists("matches", "lobby_type", "INTEGER DEFAULT 0");
        
        // Add patch column
        addColumnIfNotExists("matches", "patch", "INTEGER DEFAULT NULL");
        
        // Add region column
        addColumnIfNotExists("matches", "region", "INTEGER DEFAULT NULL");
        
        // Make sure match_details table exists
        if (!tableExists("match_details")) {
            createTableIfNotExists("match_details", "(match_id BIGINT PRIMARY KEY, raw_data TEXT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT NOW(), FOREIGN KEY (match_id) REFERENCES matches(id))");
        }
        
        // Create indexes if needed
        try {
            DatabaseManager dbManager = AppConfig.getDatabaseManager();
            dbManager.executeUpdate("CREATE INDEX IF NOT EXISTS idx_match_details_updated_at ON match_details(updated_at)");
            dbManager.executeUpdate("CREATE INDEX IF NOT EXISTS idx_matches_has_details ON matches(has_details)");
        } catch (SQLException e) {
            logger.error("Failed to create indexes: {}", e.getMessage());
            throw e;
        }
        
        logger.info("Matches table fixed successfully");
    }
}