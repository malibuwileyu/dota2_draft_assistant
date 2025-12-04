package com.dota2assistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Database configuration for SQLite.
 * Creates the data directory and configures the DataSource.
 */
@Configuration
public class DatabaseConfig {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    
    @Value("${database.path:${user.home}/.dota2assistant/data.db}")
    private String databasePath;
    
    @Bean
    public DataSource dataSource() throws IOException {
        Path dbPath = Path.of(databasePath);
        Path parentDir = dbPath.getParent();
        
        // Create directory if it doesn't exist
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            log.info("Created database directory: {}", parentDir);
        }
        
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        
        // Enable foreign keys
        ds.setEnforceForeignKeys(true);
        
        log.info("Database configured at: {}", dbPath.toAbsolutePath());
        return ds;
    }
}

