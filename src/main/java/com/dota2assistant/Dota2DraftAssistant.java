package com.dota2assistant;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Objects;

public class Dota2DraftAssistant extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(Dota2DraftAssistant.class);
    private AnnotationConfigApplicationContext context;
    
    public static void main(String[] args) {
        logger.info("Starting Dota 2 Draft Assistant");
        launch(args);
    }
    
    @Override
    public void init() {
        logger.info("Initializing application context");
        context = new AnnotationConfigApplicationContext();
        context.register(AppConfig.class);
        context.refresh();
    }
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.info("Loading main view");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        loader.setControllerFactory(context::getBean);
        
        Parent root = loader.load();
        Scene scene = new Scene(root);
        
        // Load CSS
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/application.css")).toExternalForm()
        );
        
        // Set application icon if available
        try {
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/app_icon.png")));
        } catch (Exception e) {
            logger.warn("Could not load application icon", e);
        }
        
        primaryStage.setTitle("Dota 2 Draft Assistant");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1280);
        primaryStage.setMinHeight(800);
        primaryStage.setWidth(1280);
        primaryStage.setHeight(800);
        primaryStage.show();
        
        logger.info("Application started successfully");
    }
    
    @Override
    public void stop() {
        logger.info("Closing application");
        if (context != null) {
            context.close();
        }
    }
}