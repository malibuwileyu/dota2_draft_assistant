package com.dota2assistant;

import com.dota2assistant.config.AppConfig;
import com.dota2assistant.infrastructure.persistence.DatabaseMigrator;
import com.dota2assistant.infrastructure.persistence.HeroDataImporter;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Dota 2 Draft Assistant application.
 * Initializes Spring context and launches JavaFX UI.
 */
public class Dota2DraftAssistant extends Application {
    
    private static final Logger log = LoggerFactory.getLogger(Dota2DraftAssistant.class);
    
    private AnnotationConfigApplicationContext springContext;
    private int heroCount = 0;
    
    public static void main(String[] args) {
        log.info("Starting Dota 2 Draft Assistant v0.1.0");
        launch(args);
    }
    
    @Override
    public void init() {
        log.info("Initializing Spring context...");
        springContext = new AnnotationConfigApplicationContext(AppConfig.class);
        log.info("Spring context initialized");
        
        // Run database migrations
        log.info("Running database migrations...");
        DatabaseMigrator migrator = springContext.getBean(DatabaseMigrator.class);
        migrator.migrate();
        
        // Import hero data if needed
        HeroDataImporter importer = springContext.getBean(HeroDataImporter.class);
        if (importer.needsImport()) {
            log.info("Importing hero data...");
            heroCount = importer.importHeroes();
        } else {
            log.info("Hero data already imported");
        }
    }
    
    @Override
    public void start(Stage primaryStage) {
        log.info("Starting JavaFX application...");
        
        // Placeholder UI - will be replaced with FXML in Phase 3
        String statusText = heroCount > 0 
            ? "Dota 2 Draft Assistant v0.1.0\n\nPhase 0: Foundation Complete!\n\n" + heroCount + " heroes loaded"
            : "Dota 2 Draft Assistant v0.1.0\n\nPhase 0: Foundation Complete!";
        var label = new Label(statusText);
        label.setStyle("-fx-font-size: 24px; -fx-text-fill: white; -fx-text-alignment: center;");
        
        var root = new StackPane(label);
        root.setStyle("-fx-background-color: #0f172a;");
        
        var scene = new Scene(root, 1280, 720);
        
        primaryStage.setTitle("Dota 2 Draft Assistant");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(600);
        primaryStage.show();
        
        log.info("Application started successfully");
    }
    
    @Override
    public void stop() {
        log.info("Shutting down application...");
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
        log.info("Application stopped");
    }
}

