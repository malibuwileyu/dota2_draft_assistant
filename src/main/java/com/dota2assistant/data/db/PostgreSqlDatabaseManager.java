package com.dota2assistant.data.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * PostgreSQL implementation of the DatabaseManager interface.
 */
@Component
@Primary
public class PostgreSqlDatabaseManager implements DatabaseManager {
    
    private static final Logger logger = LoggerFactory.getLogger(PostgreSqlDatabaseManager.class);
    
    @Value("${database.url}")
    private String dbUrl;
    
    @Value("${database.username}")
    private String dbUsername;
    
    @Value("${database.password}")
    private String dbPassword;
    
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();
    
    @PostConstruct
    public void initialize() {
        try {
            // Load PostgreSQL JDBC driver
            Class.forName("org.postgresql.Driver");
            
            // Initialize database connection
            checkConnection();
            logger.info("PostgreSQL database connection initialized successfully");
            logger.info("DATABASE TYPE: PostgreSQL - Using connection URL: {}", dbUrl);
            
            // Initialize database schema
            try {
                initDatabase();
                logger.info("PostgreSQL database schema initialization completed successfully");
            } catch (SQLException e) {
                logger.error("Failed to initialize PostgreSQL database schema", e);
            }
            
        } catch (ClassNotFoundException e) {
            logger.error("PostgreSQL JDBC driver not found", e);
            throw new RuntimeException("PostgreSQL JDBC driver not found", e);
        } catch (SQLException e) {
            logger.error("Failed to initialize PostgreSQL database connection - connection will be retried. Error: {}", e.getMessage());
            logger.debug("Connection failure details:", e);
            // Don't throw exception - we'll try to reconnect when needed
        }
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = connectionRef.get();
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
            conn.setAutoCommit(true);
            connectionRef.set(conn);
        }
        return conn;
    }
    
    private void checkConnection() throws SQLException {
        try (Connection conn = getConnection()) {
            if (conn == null || conn.isClosed()) {
                throw new SQLException("Could not establish PostgreSQL database connection");
            }
            
            // Check if we can query the database
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            }
        }
    }
    
    @Override
    public void initDatabase() throws SQLException {
        logger.info("Initializing PostgreSQL database schema");
        try {
            // Run initialization scripts to ensure database is set up correctly
            ensureDatabaseSchemaExists();
            logger.info("PostgreSQL database schema initialization complete");
        } catch (SQLException e) {
            logger.error("Failed to initialize PostgreSQL database schema", e);
            throw e;
        }
    }
    
    /**
     * Ensure that the database schema exists and is properly initialized
     */
    private void ensureDatabaseSchemaExists() throws SQLException {
        try {
            // Check if db_version table exists
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'db_version'")) {
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        // Table exists, check current version
                        checkAndApplyMigrations();
                    } else {
                        // Table doesn't exist, run initial schema script
                        runInitialSchemaScript();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to check database schema", e);
            throw e;
        }
    }
    
    /**
     * Run the initial schema script
     */
    private void runInitialSchemaScript() throws SQLException {
        logger.info("Running initial schema script");
        
        try {
            // Load the schema script from resources
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("sql/001_initial_schema.sql");
            try (InputStream is = resource.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                
                String script = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
                executeScript(script);
                logger.info("Initial schema script executed successfully");
                
                // Run additional migration scripts
                checkAndApplyMigrations();
            }
        } catch (Exception e) {
            logger.error("Failed to run initial schema script", e);
            throw new SQLException("Failed to run initial schema script", e);
        }
    }
    
    /**
     * Check the current database version and apply any pending migrations
     */
    private void checkAndApplyMigrations() throws SQLException {
        logger.info("Checking for database migrations");
        
        // Get the current database version
        int currentVersion = 0;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT MAX(version) FROM db_version")) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    currentVersion = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get current database version", e);
            throw e;
        }
        
        logger.info("Current database version: {}", currentVersion);
        
        // Apply migrations in order
        applyMigrationIfNeeded(currentVersion, 2, "sql/002_import_hero_data.sql");
        applyMigrationIfNeeded(currentVersion, 3, "sql/003_import_ability_data.sql");
        applyMigrationIfNeeded(currentVersion, 4, "sql/004_import_match_data.sql");
        applyMigrationIfNeeded(currentVersion, 5, "sql/005_user_profiles.sql");
        applyMigrationIfNeeded(currentVersion, 5, "sql/005_user_match_history.sql");
        
        // Apply specific migrations based on the branch and features needed
        // We have two version 6 migrations, apply them based on what's needed
        applyMigrationIfNeeded(currentVersion, 6, "sql/006_team_data.sql");
        
        // Apply match enrichment schema updates
        applyMigrationIfNeeded(currentVersion, 7, "sql/006_match_enrichment.sql", 7); // Apply as version 7
        
        // Apply any other database fixes
        applyMigrationIfNeeded(currentVersion, 8, "sql/fix_database_schema.sql", 8);
    }
    
    /**
     * Apply a migration if the current version is less than the target version
     */
    private void applyMigrationIfNeeded(int currentVersion, int targetVersion, String scriptPath) throws SQLException {
        applyMigrationIfNeeded(currentVersion, targetVersion, scriptPath, targetVersion);
    }
    
    /**
     * Apply a migration if the current version is less than the target version, with an optional override version
     * This allows remapping script filenames to different version numbers for resolving conflicts
     */
    private void applyMigrationIfNeeded(int currentVersion, int targetVersion, String scriptPath, int overrideVersion) throws SQLException {
        int versionToUse = overrideVersion;
        
        if (currentVersion < targetVersion) {
            logger.info("Applying migration: {} (file requires version {}, will be applied as version {})", 
                      scriptPath, targetVersion, versionToUse);
            
            try {
                // Load the migration script from resources
                org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource(scriptPath);
                String script = readResourceAsString(resource);
                
                if (script != null && !script.isEmpty()) {
                    // Execute the script
                    executeScript(script);
                    
                    // For scripts with inconsistent version numbers and db_version table inserts,
                    // we need to ensure the correct version is recorded
                    try (Connection conn = getConnection()) {
                        // Check if the migration already recorded itself
                        int maxVersion = 0;
                        try (PreparedStatement stmt = conn.prepareStatement("SELECT MAX(version) FROM db_version")) {
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) {
                                    maxVersion = rs.getInt(1);
                                }
                            }
                        }
                        
                        // If the script didn't update the version or updated to the wrong version, fix it
                        if (maxVersion < versionToUse) {
                            try (PreparedStatement stmt = conn.prepareStatement(
                                "INSERT INTO db_version (version, description) VALUES (?, ?) " +
                                "ON CONFLICT (version) DO UPDATE SET description = ?")) {
                                stmt.setInt(1, versionToUse);
                                String description = "Migration: " + scriptPath + " (auto-versioned)";
                                stmt.setString(2, description);
                                stmt.setString(3, description + " (updated)");
                                stmt.executeUpdate();
                            }
                        }
                    }
                    
                    logger.info("Migration script executed successfully: {} (applied as version {})", scriptPath, versionToUse);
                } else {
                    logger.warn("Migration script was empty or could not be loaded: {}", scriptPath);
                }
            } catch (Exception e) {
                logger.error("Failed to apply migration: {}", scriptPath, e);
                throw new SQLException("Failed to apply migration: " + scriptPath, e);
            }
        }
    }
    
    /**
     * Read a resource as a string
     */
    private String readResourceAsString(org.springframework.core.io.Resource resource) {
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        } catch (Exception e) {
            logger.error("Failed to read resource: {}", resource.getFilename(), e);
            return null;
        }
    }
    
    @Override
    @PreDestroy
    public void closeConnection() {
        Connection conn = connectionRef.getAndSet(null);
        if (conn != null) {
            try {
                conn.close();
                logger.info("PostgreSQL database connection closed");
            } catch (SQLException e) {
                logger.error("Failed to close PostgreSQL database connection", e);
            }
        }
    }
    
    @Override
    public void executeUpdate(String sql) throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }
    
    @Override
    public <T> List<T> query(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        List<T> results = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Set parameters
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(handler.handle(rs));
                }
            }
        }
        
        return results;
    }
    
    @Override
    public <T> T queryForObject(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Set parameters
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return handler.handle(rs);
                }
                return null;
            }
        }
    }
    
    @Override
    public int executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Set parameters
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            
            return stmt.executeUpdate();
        }
    }
    
    @Override
    public long executeInsert(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            // Set parameters
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Creating record failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                } else {
                    throw new SQLException("Creating record failed, no ID obtained.");
                }
            }
        }
    }
    
    @Override
    public int[] executeBatch(String sql, List<Object[]> batchParams) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Set batch parameters
            for (Object[] params : batchParams) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                stmt.addBatch();
            }
            
            return stmt.executeBatch();
        }
    }
    
    @Override
    public void beginTransaction() throws SQLException {
        Connection conn = getConnection();
        conn.setAutoCommit(false);
        logger.debug("Transaction started");
    }
    
    @Override
    public void commitTransaction() throws SQLException {
        Connection conn = getConnection();
        conn.commit();
        conn.setAutoCommit(true);
        logger.debug("Transaction committed");
    }
    
    @Override
    public void rollbackTransaction() throws SQLException {
        Connection conn = getConnection();
        conn.rollback();
        conn.setAutoCommit(true);
        logger.debug("Transaction rolled back");
    }
    
    @Override
    public void executeScript(String script) throws SQLException {
        if (script == null || script.trim().isEmpty()) {
            logger.warn("Empty script provided to executeScript");
            return;
        }
        
        logger.debug("Executing SQL script (length: {} characters)", script.length());
        
        Connection conn = getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        
        try {
            // Split the script into statements, handling PostgreSQL syntax
            List<String> statements = new ArrayList<>();
            StringBuilder currentStatement = new StringBuilder();
            
            // Read the script line by line to properly handle comments and semicolons
            try (BufferedReader reader = new BufferedReader(new StringReader(script))) {
                String line;
                boolean inBlockComment = false;
                
                while ((line = reader.readLine()) != null) {
                    // Skip empty lines
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    
                    // Handle block comments
                    if (line.trim().startsWith("/*")) {
                        inBlockComment = true;
                    }
                    
                    if (inBlockComment) {
                        if (line.contains("*/")) {
                            inBlockComment = false;
                        }
                        continue;
                    }
                    
                    // Skip line comments
                    if (line.trim().startsWith("--")) {
                        continue;
                    }
                    
                    // Add to current statement
                    currentStatement.append(line).append("\n");
                    
                    // Check if this line ends a statement
                    if (line.trim().endsWith(";")) {
                        statements.add(currentStatement.toString());
                        currentStatement = new StringBuilder();
                    }
                }
            } catch (IOException e) {
                logger.error("Error reading script", e);
                throw new SQLException("Error reading script", e);
            }
            
            // Add the last statement if not empty
            if (currentStatement.length() > 0) {
                statements.add(currentStatement.toString());
            }
            
            // Execute each statement
            for (String statement : statements) {
                statement = statement.trim();
                if (!statement.isEmpty()) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(statement);
                    } catch (SQLException e) {
                        // Log the error but continue with other statements
                        logger.warn("Error executing SQL statement: {}: {}", statement, e.getMessage());
                    }
                }
            }
            
            // Commit all changes
            conn.commit();
            logger.debug("SQL script executed successfully");
        } catch (SQLException e) {
            // Rollback on error
            conn.rollback();
            logger.error("Error executing SQL script: {}", e.getMessage(), e);
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }
}