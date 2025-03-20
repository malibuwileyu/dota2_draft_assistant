package com.dota2assistant.data.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PostgreSQL implementation of the DatabaseManager interface.
 */
@Component
//@Primary
@ConditionalOnProperty(name = "database.type", havingValue = "postgresql")
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
        // Database schema is initialized via SQL scripts
        // No need to initialize here as PostgreSQL database should already be set up
        logger.info("PostgreSQL database schema check bypassed - using external scripts for setup");
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
}