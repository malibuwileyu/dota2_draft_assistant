package com.dota2assistant.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs SQL migrations on application startup.
 * Migrations are versioned files in resources/db/migrations/.
 */
@Component
public class DatabaseMigrator {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrator.class);
    private static final String MIGRATIONS_PATH = "/db/migrations/";
    
    private final DataSource dataSource;
    
    public DatabaseMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Runs all pending migrations.
     */
    public void migrate() {
        try (Connection conn = dataSource.getConnection()) {
            createMigrationTable(conn);
            
            List<String> migrations = List.of(
                "V001__initial_schema.sql",
                "V002__synergy_counter_data.sql"
            );
            
            for (String migration : migrations) {
                if (!isMigrationApplied(conn, migration)) {
                    applyMigration(conn, migration);
                }
            }
            
            log.info("Database migrations complete");
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to run database migrations", e);
        }
    }
    
    private void createMigrationTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version TEXT PRIMARY KEY,
                applied_at TEXT DEFAULT (datetime('now'))
            )
            """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private boolean isMigrationApplied(Connection conn, String version) throws SQLException {
        String sql = "SELECT 1 FROM schema_migrations WHERE version = ?";
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private void applyMigration(Connection conn, String filename) throws SQLException, IOException {
        log.info("Applying migration: {}", filename);
        
        String sql = loadMigrationSql(filename);
        
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            // Split by semicolon and execute each statement
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
            
            // Record migration
            try (var insert = conn.prepareStatement(
                    "INSERT INTO schema_migrations (version) VALUES (?)")) {
                insert.setString(1, filename);
                insert.executeUpdate();
            }
            
            conn.commit();
            log.info("Migration applied: {}", filename);
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    private String loadMigrationSql(String filename) throws IOException {
        String path = MIGRATIONS_PATH + filename;
        try (var is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Migration file not found: " + path);
            }
            try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        }
    }
}

