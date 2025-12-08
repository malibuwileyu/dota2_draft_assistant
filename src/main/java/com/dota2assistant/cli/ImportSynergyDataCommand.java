package com.dota2assistant.cli;

import com.dota2assistant.config.AppConfig;
import com.dota2assistant.infrastructure.api.SynergyDataImporter;
import com.dota2assistant.infrastructure.persistence.DatabaseMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Command-line tool to import synergy data from DotaBASED.
 * Run with: ./gradlew importSynergyData
 */
public class ImportSynergyDataCommand {
    
    private static final Logger log = LoggerFactory.getLogger(ImportSynergyDataCommand.class);
    
    public static void main(String[] args) {
        log.info("=== DotaBASED Synergy Data Import ===");
        log.info("This will fetch hero pair win rates from DotaBASED.");
        log.info("");
        
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            // Ensure database is migrated
            DatabaseMigrator migrator = ctx.getBean(DatabaseMigrator.class);
            migrator.migrate();
            
            // Run the import
            SynergyDataImporter importer = ctx.getBean(SynergyDataImporter.class);
            
            long start = System.currentTimeMillis();
            int count = importer.importSynergyData();
            long elapsed = (System.currentTimeMillis() - start) / 1000;
            
            log.info("");
            log.info("=== Import Complete ===");
            log.info("Imported {} synergy records in {} seconds", count, elapsed);
        } catch (Exception e) {
            log.error("Import failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}

