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
        logger.info("Starting application");
        
        // Check if user is already logged in
        com.dota2assistant.auth.UserService userService = context.getBean(com.dota2assistant.auth.UserService.class);
        String sessionToken = com.dota2assistant.auth.SessionStorageUtil.loadSessionToken();
        
        if (sessionToken != null && !sessionToken.isEmpty()) {
            // Try to restore session
            boolean isSessionRestored = userService.restoreSession(sessionToken);
            if (isSessionRestored && userService.isLoggedIn()) {
                // User is already logged in, skip the login screen
                logger.info("Found valid session - skipping login screen");
                loadMainView(primaryStage);
                return;
            }
            // If session restoration failed, continue to login screen
        }
        
        logger.info("Loading login view");
        
        // Load login view if no valid session exists
        FXMLLoader loginLoader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
        loginLoader.setControllerFactory(context::getBean);
        
        Parent loginRoot = loginLoader.load();
        Scene loginScene = new Scene(loginRoot);
        
        // Load CSS
        loginScene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/application.css")).toExternalForm()
        );
        
        // Get login controller
        com.dota2assistant.ui.controller.LoginController loginController = loginLoader.getController();
        loginController.setLoginEventHandler(new com.dota2assistant.ui.controller.LoginController.LoginEventHandler() {
            @Override
            public void onLoginSuccessful() {
                // When login is successful, load the main view
                loadMainView(primaryStage);
            }
            
            @Override
            public void onContinueWithoutLogin() {
                // User chose to continue without login, still load main view
                loadMainView(primaryStage);
            }
        });
        
        // Set application icon if available
        try {
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/app_icon.png")));
        } catch (Exception e) {
            logger.warn("Could not load application icon", e);
        }
        
        // Configure and show login window
        primaryStage.setTitle("Dota 2 Draft Assistant - Login");
        primaryStage.setScene(loginScene);
        primaryStage.setMinWidth(400);
        primaryStage.setMinHeight(450); // Increased minimum height
        primaryStage.setWidth(400);
        primaryStage.setHeight(500); // Increased initial height
        primaryStage.show();
        
        logger.info("Login screen displayed");
    }
    
    /**
     * Loads and displays the main view after authentication.
     */
    private void loadMainView(Stage primaryStage) {
        try {
            logger.info("Loading main view");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            loader.setControllerFactory(context::getBean);
            
            Parent root = loader.load();
            Scene scene = new Scene(root);
            
            // Load CSS
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/css/application.css")).toExternalForm()
            );
            
            // Configure the stage
            primaryStage.setTitle("Dota 2 Draft Assistant");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1280);
            primaryStage.setMinHeight(800);
            primaryStage.setWidth(1280);
            primaryStage.setHeight(800);
            primaryStage.centerOnScreen(); // Center the window
            
            // Make sure the stage is showing and has focus
            if (!primaryStage.isShowing()) {
                primaryStage.show();
            }
            
            // Ensure the window is visible, on top, and focused
            primaryStage.setAlwaysOnTop(true); // Temporarily set always on top
            primaryStage.requestFocus();
            primaryStage.toFront();
            
            // After a short delay, turn off always-on-top to not be annoying
            javafx.application.Platform.runLater(() -> {
                try {
                    Thread.sleep(500); // Small delay to ensure window activation
                    primaryStage.setAlwaysOnTop(false);
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while resetting always-on-top", e);
                }
            });
            
            logger.info("Main application started successfully");
        } catch (Exception e) {
            logger.error("Failed to load main view", e);
            // Show error dialog
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR,
                    "Failed to load the application: " + e.getMessage());
            alert.showAndWait();
        }
    }
    
    @Override
    public void stop() {
        logger.info("Closing application");
        
        // Stop any active authentication server
        try {
            com.dota2assistant.auth.SteamApiService steamApiService = context.getBean(com.dota2assistant.auth.SteamApiService.class);
            if (steamApiService != null) {
                steamApiService.stopActiveAuthServer();
                logger.info("Successfully cleaned up auth servers on shutdown");
            }
        } catch (Exception e) {
            logger.warn("Error accessing SteamApiService on shutdown", e);
        }
        
        // Close Spring context
        if (context != null) {
            context.close();
        }
    }
}