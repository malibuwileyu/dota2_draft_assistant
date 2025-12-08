package com.dota2assistant.cli;

import com.dota2assistant.config.AppConfig;
import com.dota2assistant.infrastructure.api.CounterDataImporter;
import com.dota2assistant.infrastructure.persistence.DatabaseMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Command-line tool to import counter data from OpenDota.
 * Run with: ./gradlew importCounterData
 */
public class ImportCounterDataCommand {
    
    private static final Logger log = LoggerFactory.getLogger(ImportCounterDataCommand.class);
    
    public static void main(String[] args) {
        log.info("=== OpenDota Counter Data Import ===");
        log.info("This will fetch matchup data for all heroes from OpenDota API.");
        log.info("Rate limited to ~55 requests/minute. Estimated time: ~3 minutes.");
        log.info("");
        
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            // Ensure database is migrated
            DatabaseMigrator migrator = ctx.getBean(DatabaseMigrator.class);
            migrator.migrate();
            
            // Run the import
            CounterDataImporter importer = ctx.getBean(CounterDataImporter.class);
            
            String lastUpdate = importer.getLastUpdateTime();
            if (lastUpdate != null) {
                log.info("Existing data last updated: {}", lastUpdate);
            }
            
            long start = System.currentTimeMillis();
            int count = importer.importCounterData();
            long elapsed = (System.currentTimeMillis() - start) / 1000;
            
            log.info("");
            log.info("=== Import Complete ===");
            log.info("Imported {} matchup records in {} seconds", count, elapsed);
        } catch (Exception e) {
            log.error("Import failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}

