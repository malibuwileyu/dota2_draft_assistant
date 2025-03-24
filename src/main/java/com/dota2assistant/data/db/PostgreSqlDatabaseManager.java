package com.dota2assistant.data.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
    
    @Value("${database.pool.size:10}")
    private int poolSize;
    
    @Value("${database.pool.idle.timeout:600000}")
    private long idleTimeout;
    
    @Value("${database.pool.max.lifetime:1800000}")
    private long maxLifetime;
    
    @Value("${database.pool.connection.timeout:30000}")
    private long connectionTimeout;
    
    private HikariDataSource dataSource;
    
    @PostConstruct
    public void initialize() {
        try {
            // Load PostgreSQL JDBC driver
            Class.forName("org.postgresql.Driver");
            
            // Initialize connection pool
            initConnectionPool();
            
            // Initialize database connection
            checkConnection();
            logger.info("PostgreSQL database connection pool initialized successfully");
            logger.info("DATABASE TYPE: PostgreSQL - Using connection URL: {}", dbUrl);
            logger.info("Connection pool configured with size: {}", poolSize);
            
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
    
    /**
     * Initialize the HikariCP connection pool
     */
    private void initConnectionPool() {
        logger.info("Initializing HikariCP connection pool");
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        
        // Pool configuration
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setIdleTimeout(idleTimeout);         // How long a connection can remain idle in pool
        config.setMaxLifetime(maxLifetime);         // Maximum lifetime of a connection in pool
        config.setConnectionTimeout(connectionTimeout); // Wait time for connection from pool
        
        // Connection testing and validation
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);          // Timeout for connection validation
        
        // Pool maintenance
        config.setLeakDetectionThreshold(60000);    // Detect connection leaks after 60 seconds
        
        // Pool metrics
        config.setRegisterMbeans(true);             // Register JMX management beans
        config.setPoolName("Dota2Assistant-DB-Pool");
        
        // Create the data source
        dataSource = new HikariDataSource(config);
        
        logger.info("HikariCP connection pool initialized");
    }
    
    // Track if we're in shutdown mode to prevent reinitialization
    private volatile boolean isShuttingDown = false;
    
    @Override
    public Connection getConnection() throws SQLException {
        // First check if we're in a transaction
        Connection transactionConn = transactionConnection.get();
        if (transactionConn != null && !transactionConn.isClosed()) {
            // If we have an active transaction, return that connection
            return transactionConn;
        }
        
        // Check if we're shutting down
        if (isShuttingDown) {
            throw new SQLException("Database manager is shutting down, no new connections allowed");
        }
        
        // Otherwise get a new connection from the pool
        if (dataSource == null || dataSource.isClosed()) {
            // Only attempt to reinitialize if we're not shutting down
            if (!isShuttingDown) {
                logger.warn("Connection pool is not initialized or closed, attempting to reinitialize");
                initConnectionPool();
            } else {
                throw new SQLException("Connection pool is closed due to application shutdown");
            }
        }
        
        Connection conn = dataSource.getConnection();
        // Let HikariCP manage the autoCommit state (default is true)
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
        applyMigrationIfNeeded(currentVersion, 7, "sql/007_match_enrichment.sql");
        
        // Apply match player won column fix
        applyMigrationIfNeeded(currentVersion, 8, "sql/008_fix_match_players.sql");
        
        // Note: We handle the match_players unique constraint below with custom code
        
        // Apply match_players unique constraint - run directly here for better error handling
        if (currentVersion < 9) {
            logger.info("Applying migration for match_players table unique constraint (version 9)");
            try {
                // First remove any duplicates
                executeScript(
                    "DELETE FROM match_players " +
                    "WHERE id IN (" +
                    "    SELECT id " +
                    "    FROM (" +
                    "        SELECT id, " +
                    "               ROW_NUMBER() OVER (PARTITION BY match_id, account_id ORDER BY id) as rnum " +
                    "        FROM match_players" +
                    "    ) t " +
                    "    WHERE t.rnum > 1" +
                    ");"
                );
                
                // Then add the constraint
                executeScript(
                    "DO $$ " +
                    "BEGIN " +
                    "    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'match_players_match_account_unique') THEN " +
                    "        ALTER TABLE match_players ADD CONSTRAINT match_players_match_account_unique UNIQUE (match_id, account_id); " +
                    "    END IF; " +
                    "END $$;"
                );
                
                // Record the migration
                executeScript(
                    "INSERT INTO db_version (version, description) VALUES (9, 'Add unique constraint to match_players') " +
                    "ON CONFLICT (version) DO NOTHING;"
                );
                
                logger.info("Successfully applied match_players constraint migration");
            } catch (SQLException e) {
                logger.error("Failed to apply match_players constraint: {}", e.getMessage(), e);
                // Continue with other migrations - we'll handle the constraint in code
            }
        }
        
        // Apply any other database fixes
        applyMigrationIfNeeded(currentVersion, 10, "sql/fix_database_schema.sql", 10);
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
     * Get the current connection pool statistics
     * @return A string containing the current connection pool statistics
     */
    public String getConnectionPoolStats() {
        if (dataSource == null) {
            return "Connection pool not initialized";
        }
        
        StringBuilder stats = new StringBuilder();
        stats.append("Connection Pool Statistics:\n");
        stats.append("  Total Connections: ").append(dataSource.getHikariPoolMXBean().getTotalConnections()).append("\n");
        stats.append("  Active Connections: ").append(dataSource.getHikariPoolMXBean().getActiveConnections()).append("\n");
        stats.append("  Idle Connections: ").append(dataSource.getHikariPoolMXBean().getIdleConnections()).append("\n");
        stats.append("  Threads Awaiting Connection: ").append(dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()).append("\n");
        
        return stats.toString();
    }
    
    @Override
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("healthy", healthCheck());
        status.put("timestamp", System.currentTimeMillis());
        
        if (dataSource != null) {
            try {
                status.put("total_connections", dataSource.getHikariPoolMXBean().getTotalConnections());
                status.put("active_connections", dataSource.getHikariPoolMXBean().getActiveConnections());
                status.put("idle_connections", dataSource.getHikariPoolMXBean().getIdleConnections());
                status.put("waiting_threads", dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
                status.put("pool_name", dataSource.getPoolName());
            } catch (Exception e) {
                status.put("pool_stats_error", e.getMessage());
            }
        } else {
            status.put("pool_initialized", false);
        }
        
        return status;
    }
    
    /**
     * Log the current connection pool statistics
     */
    public void logConnectionPoolStats() {
        if (dataSource == null) {
            logger.info("Connection pool not initialized");
            return;
        }
        
        logger.info("Connection pool stats - Total: {}, Active: {}, Idle: {}, Waiting: {}",
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
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
        // Set the shutdown flag first to prevent new connection attempts
        isShuttingDown = true;
        logger.info("Setting PostgreSQL database manager to shutdown mode");
        
        // Then close the connection pool
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("PostgreSQL connection pool has been shut down");
        }
        
        // Log that we're shutting down the database manager
        logger.info("PostgreSQL database manager shutdown completed");
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
    
    // ThreadLocal to store the connection used for a transaction in the current thread
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();
    
    @Override
    public void beginTransaction() throws SQLException {
        // Get a connection from the pool and store it in ThreadLocal
        Connection conn = getConnection();
        conn.setAutoCommit(false);
        transactionConnection.set(conn);
        logger.debug("Transaction started on thread {}", Thread.currentThread().getId());
    }
    
    @Override
    public void commitTransaction() throws SQLException {
        // Get the connection from ThreadLocal
        Connection conn = transactionConnection.get();
        if (conn == null) {
            throw new SQLException("No transaction in progress on this thread");
        }
        
        try {
            conn.commit();
            logger.debug("Transaction committed on thread {}", Thread.currentThread().getId());
        } finally {
            // Clean up
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                logger.warn("Error closing connection after commit", e);
            }
            transactionConnection.remove();
        }
    }
    
    @Override
    public void rollbackTransaction() throws SQLException {
        // Get the connection from ThreadLocal
        Connection conn = transactionConnection.get();
        if (conn == null) {
            throw new SQLException("No transaction in progress on this thread");
        }
        
        try {
            conn.rollback();
            logger.debug("Transaction rolled back on thread {}", Thread.currentThread().getId());
        } finally {
            // Clean up
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                logger.warn("Error closing connection after rollback", e);
            }
            transactionConnection.remove();
        }
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