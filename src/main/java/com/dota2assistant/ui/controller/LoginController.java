package com.dota2assistant.ui.controller;

import com.dota2assistant.auth.AuthCallbackServer;
import com.dota2assistant.auth.SessionStorageUtil;
import com.dota2assistant.auth.SteamUser;
import com.dota2assistant.auth.UserService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the login screen.
 */
public class LoginController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    
    @FXML
    private Button loginButton;
    
    @FXML
    private Button logoutButton;
    
    @FXML
    private VBox userInfoContainer;
    
    @FXML
    private Label usernameLabel;
    
    @FXML
    private ImageView avatarImageView;
    
    @FXML
    private Label statusLabel;
    
    // Event handler for login events
    public interface LoginEventHandler {
        void onLoginSuccessful();
        void onContinueWithoutLogin();
    }
    
    private LoginEventHandler loginEventHandler;
    
    private final UserService userService;
    private final AuthCallbackServer callbackServer;
    private boolean isAuthInProgress = false;
    
    public LoginController(UserService userService, AuthCallbackServer callbackServer) {
        this.userService = userService;
        this.callbackServer = callbackServer;
    }
    
    public void setLoginEventHandler(LoginEventHandler handler) {
        this.loginEventHandler = handler;
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initially hide user info and show login button
        
        // Check Steam API key status
        checkSteamApiKeyStatus();
        
        // Try to restore session from saved preferences
        tryRestoreSession();
        
        // Update UI based on current login state
        updateUI();
        
        // Set up the login button
        loginButton.setOnAction(event -> startLogin());
        
        // Set up the logout button
        logoutButton.setOnAction(event -> {
            userService.logout();
            // Clear the saved session token
            SessionStorageUtil.clearSessionToken();
            updateUI();
            statusLabel.setText("You have been logged out");
        });
    }
    
    /**
     * Check the status of the Steam API key and provide feedback.
     */
    private void checkSteamApiKeyStatus() {
        if (!userService.isSteamApiKeyValid()) {
            String message = "Steam API key appears to be invalid or missing. Authentication will not work.";
            statusLabel.setText(message);
            logger.error(message);
            
            // Disable login button if API key is invalid
            loginButton.setDisable(true);
            loginButton.setTooltip(new javafx.scene.control.Tooltip(
                "Steam login is disabled because the API key is invalid or missing. " +
                "Please set the STEAM_API_KEY environment variable or update application.properties.override."
            ));
        }
    }
    
    /**
     * Try to restore a previous session if available.
     */
    private void tryRestoreSession() {
        logger.info("Attempting to restore previous session");
        
        // Get the saved session token
        String sessionToken = SessionStorageUtil.loadSessionToken();
        
        if (sessionToken != null && !sessionToken.isEmpty()) {
            logger.info("Found saved session token, attempting to restore");
            
            // Try to restore the session
            boolean restored = userService.restoreSession(sessionToken);
            
            if (restored) {
                logger.info("Session successfully restored");
                statusLabel.setText("Welcome back!");
            } else {
                logger.info("Session token invalid or expired");
                statusLabel.setText("Previous session expired, please log in again");
                // Clear the invalid token
                SessionStorageUtil.clearSessionToken();
            }
        } else {
            logger.info("No saved session found");
        }
    }
    
    /**
     * Updates the UI based on the current login status.
     */
    private void updateUI() {
        Optional<SteamUser> currentUser = userService.getCurrentUser();
        
        if (currentUser.isPresent()) {
            // User is logged in - show user info
            SteamUser user = currentUser.get();
            usernameLabel.setText(user.getUsername());
            
            // Load avatar image if available
            if (user.getAvatarMediumUrl() != null) {
                try {
                    avatarImageView.setImage(new Image(user.getAvatarMediumUrl()));
                } catch (Exception e) {
                    logger.warn("Failed to load avatar image: {}", e.getMessage());
                }
            }
            
            userInfoContainer.setVisible(true);
            loginButton.setVisible(false);
            logoutButton.setVisible(true);
            statusLabel.setText("Logged in as " + user.getUsername());
        } else {
            // User is not logged in - show login button
            userInfoContainer.setVisible(false);
            loginButton.setVisible(true);
            logoutButton.setVisible(false);
            statusLabel.setText("Not logged in");
        }
    }
    
    /**
     * Starts the login process.
     */
    private void startLogin() {
        if (isAuthInProgress) {
            logger.warn("Authentication already in progress");
            return;
        }
        
        // First try to use the SteamApiService directly
        try {
            isAuthInProgress = true;
            statusLabel.setText("Starting authentication...");
            
            // Directly use SteamApiService for better flow control
            userService.getSteamApiService().startAuthFlow();
            
            // Now show a spinner/indicator since auth is in progress
            statusLabel.setText("Authentication in progress... Please complete login in your browser.");
        } catch (Exception e) {
            logger.error("Failed to start authentication flow", e);
            statusLabel.setText("Authentication failed: " + e.getMessage());
            isAuthInProgress = false;
        }
        
        // Setup a listener to check for authentication status
        // This will be triggered when the user service detects a login
        Thread statusChecker = new Thread(() -> {
            try {
                // Poll login status for up to 5 minutes
                int maxAttempts = 300; // 5 minutes * 60 seconds / 1 second polling interval
                for (int i = 0; i < maxAttempts; i++) {
                    if (userService.isLoggedIn()) {
                        // Authentication successful
                        Platform.runLater(() -> {
                            statusLabel.setText("Authentication successful!");
                            updateUI();
                            
                            // Save the session token for future use
                            userService.getCurrentSessionToken().ifPresent(token -> {
                                boolean saved = SessionStorageUtil.saveSessionToken(token);
                                if (!saved) {
                                    logger.warn("Failed to save session token in LoginController");
                                }
                            });
                            
                            // Notify handler if present
                            if (loginEventHandler != null) {
                                loginEventHandler.onLoginSuccessful();
                            }
                        });
                        return;
                    }
                    
                    // Wait for 1 second before checking again
                    Thread.sleep(1000);
                }
                
                // If we get here, timeout occurred
                Platform.runLater(() -> {
                    statusLabel.setText("Authentication timed out. Please try again.");
                    isAuthInProgress = false;
                });
            } catch (InterruptedException e) {
                logger.info("Authentication status checking interrupted");
            } finally {
                isAuthInProgress = false;
            }
        });
        
        // Start as daemon thread so it doesn't block application exit
        statusChecker.setDaemon(true);
        statusChecker.start();
    }
    
    /**
     * Handles the "Continue Without Login" button click.
     */
    @FXML
    public void onContinueWithoutLogin() {
        logger.info("Continuing without login");
        
        // Notify handler if present
        if (loginEventHandler != null) {
            loginEventHandler.onContinueWithoutLogin();
        }
    }
}