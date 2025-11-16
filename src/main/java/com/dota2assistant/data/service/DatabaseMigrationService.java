package com.dota2assistant.data.service;

import com.dota2assistant.data.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service responsible for applying database migrations at application startup.
 * This ensures that the database schema is up-to-date with the application's needs.
 */
@Service
public class DatabaseMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrationService.class);
    
    // Pattern to extract version number from migration filenames
    private static final Pattern MIGRATION_FILENAME_PATTERN = Pattern.compile("^(\\d+)_.*\\.sql$");
    
    private final DatabaseManager databaseManager;
    
    /**
     * Creates a new DatabaseMigrationService.
     * 
     * @param databaseManager the database manager to use for executing migrations
     */
    public DatabaseMigrationService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    /**
     * Initializes the migration service and applies any pending migrations
     */
    @PostConstruct
    public void initialize() {
        logger.info("Initializing database migration service");
        
        try {
            // Create the version table if it doesn't exist
            ensureVersionTableExists();
            
            // Apply any pending migrations
            applyPendingMigrations();
            
            logger.info("Database migration service initialization completed successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize database migration service", e);
            // Don't throw exception to allow application to start even with migration failures
            // The application should handle missing columns gracefully
        }
    }
    
    /**
     * Ensures the version tracking table exists
     */
    private void ensureVersionTableExists() throws SQLException {
        String createVersionTableSql = "CREATE TABLE IF NOT EXISTS db_version (" +
                                     "  version INT PRIMARY KEY," +
                                     "  applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                                     "  description TEXT" +
                                     ")";
        
        try {
            databaseManager.executeUpdate(createVersionTableSql);
            logger.debug("Ensured db_version table exists");
        } catch (SQLException e) {
            logger.error("Failed to create db_version table", e);
            throw e;
        }
    }
    
    /**
     * Gets the current database schema version
     * 
     * @return the current schema version, or 0 if no version is recorded
     * @throws SQLException if a database access error occurs
     */
    private int getCurrentVersion() throws SQLException {
        String sql = "SELECT MAX(version) FROM db_version";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int version = rs.getInt(1);
                    logger.debug("Current database version: {}", version);
                    return version;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get current database version", e);
            throw e;
        }
        
        logger.debug("No database version found, assuming version 0");
        return 0;
    }
    
    /**
     * Applies any pending migrations
     */
    private void applyPendingMigrations() throws SQLException, IOException {
        int currentVersion = getCurrentVersion();
        logger.info("Current database version: {}", currentVersion);
        
        // Find all migration scripts
        List<MigrationScript> migrationScripts = findMigrationScripts();
        
        // Sort them by version
        migrationScripts.sort(Comparator.comparingInt(MigrationScript::getVersion));
        
        // Apply each migration if needed
        for (MigrationScript migration : migrationScripts) {
            if (migration.getVersion() > currentVersion) {
                logger.info("Applying migration: {} (version {})", migration.getFilename(), migration.getVersion());
                
                try {
                    // Apply the migration
                    applyMigration(migration);
                    
                    // Update the version number
                    updateVersionNumber(migration.getVersion(), "Applied migration: " + migration.getFilename());
                    
                    logger.info("Migration applied successfully: {}", migration.getFilename());
                } catch (Exception e) {
                    logger.error("Failed to apply migration: {}", migration.getFilename(), e);
                    throw e;
                }
            } else {
                logger.debug("Skipping migration already applied: {} (version {})", 
                          migration.getFilename(), migration.getVersion());
            }
        }
        
        // Apply special case for match_enrichment.sql - it has a filename conflict
        if (currentVersion < 7) {
            applyMatchEnrichmentMigration();
        }
        
        logger.info("Database migrations completed. Current version: {}", getCurrentVersion());
    }
    
    /**
     * Special case to apply the match enrichment migration
     */
    private void applyMatchEnrichmentMigration() throws SQLException, IOException {
        logger.info("Applying match enrichment migration as version 7");
        
        ClassPathResource resource = new ClassPathResource("sql/007_match_enrichment.sql");
        String script = readResourceAsString(resource);
        
        if (script != null && !script.isEmpty()) {
            // Execute the script
            databaseManager.executeScript(script);
            
            // Ensure version table is updated correctly
            int version = 7;
            try (Connection conn = databaseManager.getConnection()) {
                // Check if the migration already recorded itself
                if (getCurrentVersion() < version) {
                    updateVersionNumber(version, "Match enrichment migration (manual application)");
                }
            }
            
            logger.info("Match enrichment migration applied successfully");
        } else {
            logger.warn("Match enrichment migration script was empty or could not be loaded");
        }
    }
    
    /**
     * Finds all migration scripts in the classpath
     */
    private List<MigrationScript> findMigrationScripts() throws IOException {
        List<MigrationScript> migrations = new ArrayList<>();
        
        // Use Spring's resource loading to find all migration scripts
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:sql/*_*.sql");
        
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) continue;
            
            // Extract version number from filename
            Matcher matcher = MIGRATION_FILENAME_PATTERN.matcher(filename);
            if (matcher.matches()) {
                try {
                    int version = Integer.parseInt(matcher.group(1));
                    
                    // Skip the old match enrichment script if it exists - we use the renamed version
                    if (filename.equals("006_match_enrichment.sql")) {
                        continue;
                    }
                    
                    migrations.add(new MigrationScript(version, filename, resource));
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse version number from migration filename: {}", filename);
                }
            }
        }
        
        return migrations;
    }
    
    /**
     * Applies a migration script
     */
    private void applyMigration(MigrationScript migration) throws SQLException, IOException {
        // Read the script
        String script = readResourceAsString(migration.getResource());
        
        if (script != null && !script.isEmpty()) {
            // Execute the script
            databaseManager.executeScript(script);
        } else {
            throw new IOException("Migration script was empty or could not be loaded: " + migration.getFilename());
        }
    }
    
    /**
     * Updates the version number in the database
     */
    private void updateVersionNumber(int version, String description) throws SQLException {
        String sql = "INSERT INTO db_version (version, description) VALUES (?, ?) " +
                   "ON CONFLICT (version) DO UPDATE SET description = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, version);
            stmt.setString(2, description);
            stmt.setString(3, description + " (updated)");
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update version number", e);
            throw e;
        }
    }
    
    /**
     * Reads a resource as a string
     */
    private String readResourceAsString(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
    
    /**
     * Class to represent a migration script
     */
    private static class MigrationScript {
        private final int version;
        private final String filename;
        private final Resource resource;
        
        public MigrationScript(int version, String filename, Resource resource) {
            this.version = version;
            this.filename = filename;
            this.resource = resource;
        }
        
        public int getVersion() {
            return version;
        }
        
        public String getFilename() {
            return filename;
        }
        
        public Resource getResource() {
            return resource;
        }
    }
}