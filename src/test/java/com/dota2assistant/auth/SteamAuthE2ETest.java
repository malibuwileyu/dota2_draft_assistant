package com.dota2assistant.auth;

import com.dota2assistant.data.db.DatabaseManager;
import com.dota2assistant.data.db.PostgreSqlDatabaseManager;
import com.dota2assistant.data.model.Session;
import com.dota2assistant.data.repository.UserRepository;
import com.dota2assistant.util.PropertyLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * E2E test for Steam authentication flow.
 * This is a standalone JavaFX application that allows manual testing of the entire auth flow.
 * 
 * To run: java -cp target/classes:target/test-classes com.dota2assistant.auth.SteamAuthE2ETest
 * Or use: mvn test -Dtest=SteamAuthE2ETest
 */
public class SteamAuthE2ETest extends Application {

    private Label statusLabel;
    private TextArea logArea;
    private Button loginButton;
    private Button logoutButton;
    private Button checkSessionButton;
    private Label userInfoLabel;
    
    private UserService userService;
    private AuthCallbackServer callbackServer;
    private AtomicReference<Boolean> isAuthInProgress = new AtomicReference<>(false);
    
    @Override
    public void start(Stage primaryStage) {
        // Setup UI
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        
        // Status label at the top
        statusLabel = new Label("Not logged in");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        root.setTop(statusLabel);
        BorderPane.setMargin(statusLabel, new Insets(0, 0, 10, 0));
        
        // Log area in the center
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(300);
        root.setCenter(logArea);
        
        // User info below log area
        userInfoLabel = new Label("No user information");
        root.setBottom(userInfoLabel);
        BorderPane.setMargin(userInfoLabel, new Insets(10, 0, 10, 0));
        
        // Buttons at the bottom
        loginButton = new Button("Login with Steam");
        logoutButton = new Button("Logout");
        checkSessionButton = new Button("Check Saved Session");
        
        HBox buttonBox = new HBox(10, loginButton, logoutButton, checkSessionButton);
        buttonBox.setAlignment(Pos.CENTER);
        VBox bottomBox = new VBox(10, userInfoLabel, buttonBox);
        bottomBox.setAlignment(Pos.CENTER);
        root.setBottom(bottomBox);
        
        // Initialize services
        initializeServices();
        
        // Setup button actions
        setupButtonActions();
        
        // Set initial button states
        updateButtonStates();
        
        // Create scene and show stage
        Scene scene = new Scene(root, 600, 400);
        primaryStage.setTitle("Steam Auth E2E Test");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Check for saved session on startup
        checkSavedSession();
    }
    
    private void initializeServices() {
        // Create services with real implementations
        try {
            log("Initializing services...");
            
            // Create property loader
            PropertyLoader propertyLoader = new PropertyLoader();
            
            // Create HTTP client
            OkHttpClient httpClient = new OkHttpClient();
            
            // Create ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            
            // Create authentication components
            SteamAuthenticationManager authManager = new SteamAuthenticationManager(httpClient, propertyLoader);
            SteamApiService steamApiService = new SteamApiService(httpClient, objectMapper, propertyLoader, authManager);
            callbackServer = new AuthCallbackServer();
            
            // Create database manager (using PostgreSQL for testing)
            DatabaseManager dbManager = new PostgreSqlDatabaseManager();
            UserRepository userRepository = new UserRepository(dbManager, objectMapper);
            
            // Initialize UserService
            userService = new UserService(steamApiService, authManager, userRepository);
            
            log("Services initialized successfully");
        } catch (Exception e) {
            log("Error initializing services: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void setupButtonActions() {
        // Login button
        loginButton.setOnAction(event -> {
            if (isAuthInProgress.get()) {
                log("Authentication already in progress");
                return;
            }
            
            isAuthInProgress.set(true);
            log("Starting authentication...");
            statusLabel.setText("Authentication in progress...");
            
            // Start the callback server
            CompletableFuture<Map<String, Object>> callbackFuture;
            try {
                callbackFuture = callbackServer.startAndWaitForCallback();
            } catch (IOException e) {
                log("Failed to start callback server: " + e.getMessage());
                statusLabel.setText("Authentication failed: Could not start callback server");
                isAuthInProgress.set(false);
                return;
            }
            
            // Get the Steam login URL
            String loginUrl = userService.getLoginUrl();
            log("Login URL: " + loginUrl);
            
            // Open the browser
            getHostServices().showDocument(loginUrl);
            
            // Handle the callback result
            callbackFuture.thenAcceptAsync(callbackData -> {
                try {
                    String callbackUrl = (String) callbackData.get("url");
                    AuthCallbackServer.ClientInfo clientInfo = 
                            (AuthCallbackServer.ClientInfo) callbackData.get("clientInfo");
                    
                    log("Received callback URL: " + callbackUrl);
                    log("Client IP: " + clientInfo.getIpAddress());
                    log("User Agent: " + clientInfo.getUserAgent());
                    
                    boolean success = userService.processLogin(callbackUrl, 
                            clientInfo.getIpAddress(), clientInfo.getUserAgent());
                    
                    Platform.runLater(() -> {
                        if (success) {
                            log("Authentication successful!");
                            statusLabel.setText("Logged in");
                            updateUserInfo();
                            
                            // Save session token
                            userService.getCurrentSessionToken().ifPresent(token -> {
                                boolean saved = SessionStorageUtil.saveSessionToken(token);
                                log("Session token saved: " + saved);
                            });
                        } else {
                            log("Authentication failed");
                            statusLabel.setText("Authentication failed");
                        }
                        updateButtonStates();
                    });
                } finally {
                    callbackServer.stopServer();
                    isAuthInProgress.set(false);
                }
            }).exceptionally(ex -> {
                log("Authentication error: " + ex.getMessage());
                Platform.runLater(() -> {
                    statusLabel.setText("Authentication failed");
                    updateButtonStates();
                });
                callbackServer.stopServer();
                isAuthInProgress.set(false);
                return null;
            });
        });
        
        // Logout button
        logoutButton.setOnAction(event -> {
            log("Logging out...");
            userService.logout();
            SessionStorageUtil.clearSessionToken();
            statusLabel.setText("Logged out");
            userInfoLabel.setText("No user information");
            updateButtonStates();
            log("Logout complete");
        });
        
        // Check session button
        checkSessionButton.setOnAction(event -> {
            checkSavedSession();
        });
    }
    
    private void checkSavedSession() {
        log("Checking for saved session...");
        String sessionToken = SessionStorageUtil.loadSessionToken();
        
        if (sessionToken != null && !sessionToken.isEmpty()) {
            log("Found saved session token: " + sessionToken);
            boolean restored = userService.restoreSession(sessionToken);
            
            if (restored) {
                log("Session restored successfully");
                statusLabel.setText("Session restored");
                updateUserInfo();
            } else {
                log("Failed to restore session (token may be expired)");
                SessionStorageUtil.clearSessionToken();
            }
        } else {
            log("No saved session found");
        }
        
        updateButtonStates();
    }
    
    private void updateUserInfo() {
        userService.getCurrentUser().ifPresent(user -> {
            userInfoLabel.setText(String.format(
                    "User: %s (Steam ID: %s)",
                    user.getUsername(),
                    user.getSteamId()
            ));
            
            // Get preferences if available
            try {
                Map<String, ?> prefs = userService.getCurrentUserPreferences();
                if (!prefs.isEmpty()) {
                    log("User preferences: " + prefs);
                }
                
                // Get skill metrics if available
                Map<String, Double> metrics = userService.getCurrentUserSkillMetrics();
                if (!metrics.isEmpty()) {
                    log("User skill metrics: " + metrics);
                }
            } catch (Exception e) {
                log("Error loading user data: " + e.getMessage());
            }
        });
    }
    
    private void updateButtonStates() {
        boolean loggedIn = userService.isLoggedIn();
        loginButton.setDisable(loggedIn || isAuthInProgress.get());
        logoutButton.setDisable(!loggedIn);
    }
    
    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
        });
        System.out.println(message);
    }
    
    @Override
    public void stop() {
        // Clean up resources
        if (callbackServer != null) {
            callbackServer.stopServer();
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}