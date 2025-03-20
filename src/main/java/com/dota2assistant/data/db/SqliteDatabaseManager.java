package com.dota2assistant.data.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "database.type", havingValue = "sqlite", matchIfMissing = true)
public class SqliteDatabaseManager implements DatabaseManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SqliteDatabaseManager.class);
    
    private String dbFileName;
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();
    
    public SqliteDatabaseManager(String dbFileName) {
        this.dbFileName = dbFileName;
    }
    
    @PostConstruct
    public void initialize() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            
            // Initialize database
            checkAndInitializeSchema();
        } catch (ClassNotFoundException e) {
            logger.error("SQLite JDBC driver not found", e);
            throw new RuntimeException("SQLite JDBC driver not found", e);
        } catch (SQLException | RuntimeException e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = connectionRef.get();
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFileName);
            conn.setAutoCommit(true);
            
            // Enable foreign keys
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            
            connectionRef.set(conn);
        }
        return conn;
    }
    
    @Override
    public void initDatabase() throws SQLException {
        checkAndInitializeSchema();
    }
    
    private void checkAndInitializeSchema() throws SQLException {
        Connection conn = getConnection();
        
        // Check if database needs to be initialized
        if (!isSchemaInitialized(conn)) {
            logger.info("Initializing database schema");
            
            // Execute initial schema script
            executeResourceScript("sql/001_initial_schema.sql");
            
            logger.info("Database schema initialized successfully");
        } else {
            logger.info("Database schema already initialized");
            
            // Check for and apply migrations
            applyPendingMigrations();
        }
    }
    
    private boolean isSchemaInitialized(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='db_version'")) {
                boolean hasTable = rs.next();
                
                // If we have the heroes table, the schema is already initialized
                try (ResultSet rs2 = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='heroes'")) {
                    if (rs2.next()) {
                        logger.info("Heroes table exists, schema is already initialized");
                        return true;
                    }
                } catch (SQLException e) {
                    // Ignore
                }
                
                return hasTable;
            } catch (SQLException e) {
                return false;
            }
        }
    }
    
    private int getCurrentDatabaseVersion() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            try (ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM db_version")) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            } catch (SQLException e) {
                logger.error("Failed to get current database version", e);
                return 0;
            }
        }
    }
    
    private void applyPendingMigrations() throws SQLException {
        int currentVersion = getCurrentDatabaseVersion();
        logger.info("Current database version: {}", currentVersion);
        
        // List of available migration scripts (in order)
        List<String> migrationScripts = getAvailableMigrationScripts(currentVersion);
        
        for (String scriptPath : migrationScripts) {
            logger.info("Applying migration: {}", scriptPath);
            executeResourceScript(scriptPath);
        }
    }
    
    private List<String> getAvailableMigrationScripts(int currentVersion) {
        List<String> scripts = new ArrayList<>();
        
        // In a production application, you might scan the classpath for migration scripts
        // For simplicity, we'll hardcode the migrations in order
        if (currentVersion < 2) {
            scripts.add("sql/002_import_hero_data.sql");
        }
        if (currentVersion < 3) {
            scripts.add("sql/003_import_ability_data.sql");
        }
        if (currentVersion < 4) {
            scripts.add("sql/004_import_match_data.sql");
        }
        
        return scripts;
    }
    
    private void executeResourceScript(String resourcePath) throws SQLException {
        logger.info("Executing SQL script: {}", resourcePath);
        
        try {
            Resource resource = new ClassPathResource(resourcePath);
            try (InputStream is = resource.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                
                String script = reader.lines().collect(Collectors.joining("\n"));
                String[] statements = script.split(";");
                
                Connection conn = getConnection();
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                
                try {
                    for (String statement : statements) {
                        statement = statement.trim();
                        if (!statement.isEmpty()) {
                            try (Statement stmt = conn.createStatement()) {
                                stmt.executeUpdate(statement);
                            }
                        }
                    }
                    
                    // Commit all changes
                    conn.commit();
                    logger.info("Script executed successfully: {}", resourcePath);
                } catch (SQLException e) {
                    // Rollback on error
                    conn.rollback();
                    logger.error("Error executing script: {}", resourcePath, e);
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to execute SQL script: {}", resourcePath, e);
            throw new SQLException("Failed to execute SQL script: " + resourcePath, e);
        }
    }
    
    @Override
    @PreDestroy
    public void closeConnection() {
        Connection conn = connectionRef.getAndSet(null);
        if (conn != null) {
            try {
                conn.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Failed to close database connection", e);
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
    
    /**
     * Interface for mapping ResultSet rows to objects.
     */
    // Using the interface defined in DatabaseManager
}