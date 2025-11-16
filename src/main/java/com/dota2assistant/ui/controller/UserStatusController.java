package com.dota2assistant.ui.controller;

import com.dota2assistant.auth.SteamUser;
import com.dota2assistant.auth.UserService;
import com.dota2assistant.auth.SessionStorageUtil;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controller for the user status component that shows the current login status
 * (whether logged in or guest mode) and provides a login/logout button.
 */
@Component
public class UserStatusController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(UserStatusController.class);
    
    @FXML
    private HBox userStatusContainer;
    
    @FXML
    private Label usernameLabel;
    
    @FXML
    private ImageView avatarView;
    
    @FXML
    private Button loginLogoutButton;
    
    private final UserService userService;
    private Consumer<Boolean> onLoginStatusChanged;
    
    @Autowired
    public UserStatusController(UserService userService) {
        this.userService = userService;
        logger.debug("UserStatusController created");
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("Initializing UserStatusController");
        
        // Register for login state changes
        userService.addLoginStateListener((evt) -> {
            // This will be called when login state changes via the notifyLoginStateChanged method
            boolean isLoggedIn = (boolean)evt.getNewValue();
            logger.info("Login state change detected in UserStatusController: {}", 
                isLoggedIn ? "logged in" : "logged out");
            
            // Update UI on JavaFX thread
            javafx.application.Platform.runLater(() -> {
                updateUI();
                logger.info("UserStatusController UI updated after login state change");
            });
        });
        
        // Configure button action
        loginLogoutButton.setOnAction(event -> {
            if (userService.isLoggedIn()) {
                // Clear saved session token first
                SessionStorageUtil.clearSessionToken();
                
                // Then perform logout
                userService.logout();
                
                // Update UI to reflect logout
                updateUI();
                
                // Notify any listeners
                if (onLoginStatusChanged != null) {
                    onLoginStatusChanged.accept(false);
                }
                
                logger.info("User logged out successfully");
            } else {
                // Navigate to the login screen by directly starting the auth flow
                logger.info("Login button clicked - starting Steam auth flow");
                userService.getSteamApiService().startAuthFlow();
                
                // Also notify listeners that login was requested
                if (onLoginStatusChanged != null) {
                    onLoginStatusChanged.accept(true);
                }
            }
        });
        
        // Initial UI update
        updateUI();
    }
    
    /**
     * Updates the UI based on the current login status.
     */
    public void updateUI() {
        logger.debug("Updating user status UI");
        Optional<SteamUser> currentUser = userService.getCurrentUser();
        
        if (currentUser.isPresent()) {
            SteamUser user = currentUser.get();
            usernameLabel.setText(user.getUsername());
            logger.debug("User logged in: {}", user.getUsername());
            
            // Load avatar if available
            if (user.getAvatarMediumUrl() != null) {
                try {
                    Image avatarImage = new Image(user.getAvatarMediumUrl());
                    avatarView.setImage(avatarImage);
                    avatarView.setVisible(true);
                    logger.debug("Loaded avatar from URL: {}", user.getAvatarMediumUrl());
                } catch (Exception e) {
                    logger.warn("Failed to load avatar image: {}", e.getMessage());
                    avatarView.setVisible(false);
                }
            } else {
                avatarView.setVisible(false);
                logger.debug("No avatar URL provided");
            }
            
            // Update button
            loginLogoutButton.setText("Logout");
            loginLogoutButton.setTooltip(new Tooltip("Log out of Steam"));
        } else {
            // Guest mode
            usernameLabel.setText("Guest");
            avatarView.setVisible(false);
            logger.debug("User is in guest mode");
            
            // Update button
            loginLogoutButton.setText("Login");
            loginLogoutButton.setTooltip(new Tooltip("Login with Steam"));
        }
    }
    
    /**
     * Sets a callback to be invoked when login status changes.
     * 
     * @param callback The callback to invoke
     */
    public void setOnLoginStatusChanged(Consumer<Boolean> callback) {
        this.onLoginStatusChanged = callback;
    }
    
    /**
     * Gets the user service that this controller uses.
     * This allows the main controller to access user information.
     * 
     * @return The UserService instance
     */
    public UserService getUserService() {
        return this.userService;
    }
}