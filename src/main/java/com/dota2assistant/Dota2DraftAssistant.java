package com.dota2assistant;

import com.dota2assistant.config.AppConfig;
import com.dota2assistant.domain.auth.AuthenticationService;
import com.dota2assistant.domain.repository.HeroRepository;
import com.dota2assistant.infrastructure.api.BackendApiClient;
import com.dota2assistant.infrastructure.persistence.DatabaseMigrator;
import com.dota2assistant.infrastructure.persistence.HeroDataImporter;
import com.dota2assistant.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
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
            importer.importHeroes();
        } else {
            log.info("Hero data already imported");
        }
    }
    
    @Override
    public void start(Stage primaryStage) {
        log.info("Starting JavaFX application...");
        
        HeroRepository heroRepo = springContext.getBean(HeroRepository.class);
        BackendApiClient backendClient = springContext.getBean(BackendApiClient.class);
        AuthenticationService authService = springContext.getBean(AuthenticationService.class);
        
        MainView mainView = new MainView(heroRepo, backendClient, authService);
        
        var scene = new Scene(mainView, 1280, 900);
        
        primaryStage.setTitle("Dota 2 Draft Assistant");
        primaryStage.setScene(scene);
        primaryStage.setWidth(1280);
        primaryStage.setHeight(900);
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(800);
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

