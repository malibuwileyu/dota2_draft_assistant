package com.dota2assistant.ui.components;

import com.dota2assistant.domain.auth.AuthenticationService;
import com.dota2assistant.domain.model.UserSession;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Login panel component showing Steam login button or user profile.
 */
public class LoginPanel extends HBox {
    
    private static final Logger log = LoggerFactory.getLogger(LoginPanel.class);
    
    private final AuthenticationService authService;
    private Consumer<UserSession> onLoginSuccess = session -> {};
    private Runnable onLogout = () -> {};
    
    private final VBox loginView = new VBox(10);
    private final HBox profileView = new HBox(10);
    
    public LoginPanel(AuthenticationService authService) {
        this.authService = authService;
        
        setAlignment(Pos.CENTER_RIGHT);
        setPadding(new Insets(5, 10, 5, 10));
        setSpacing(10);
        
        setupLoginView();
        setupProfileView();
        
        // Show appropriate view based on login state
        updateView();
    }
    
    public void setOnLoginSuccess(Consumer<UserSession> handler) {
        this.onLoginSuccess = handler;
    }
    
    public void setOnLogout(Runnable handler) {
        this.onLogout = handler;
    }
    
    private void setupLoginView() {
        Button loginButton = new Button("🎮 Login with Steam");
        styleButton(loginButton, "#1b2838", "#2a475e"); // Steam colors
        loginButton.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        loginButton.setOnAction(e -> handleLogin(loginButton));
        
        loginView.setAlignment(Pos.CENTER);
        loginView.getChildren().add(loginButton);
    }
    
    private void setupProfileView() {
        profileView.setAlignment(Pos.CENTER_RIGHT);
        profileView.setSpacing(8);
    }
    
    private void handleLogin(Button loginButton) {
        loginButton.setDisable(true);
        loginButton.setText("Logging in...");
        
        authService.login(
            session -> Platform.runLater(() -> {
                loginButton.setDisable(false);
                loginButton.setText("🎮 Login with Steam");
                updateView();
                onLoginSuccess.accept(session);
            }),
            error -> Platform.runLater(() -> {
                loginButton.setDisable(false);
                loginButton.setText("🎮 Login with Steam");
                showError(error);
            })
        );
    }
    
    private void updateView() {
        getChildren().clear();
        
        Optional<UserSession> sessionOpt = authService.getCurrentSession();
        
        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            
            profileView.getChildren().clear();
            
            // Avatar
            ImageView avatar = new ImageView();
            if (session.avatarUrl() != null && !session.avatarUrl().isBlank()) {
                Image avatarImage = new Image(session.avatarUrl(), 32, 32, true, true, true);
                avatar.setImage(avatarImage);
            }
            avatar.setFitWidth(32);
            avatar.setFitHeight(32);
            Circle clip = new Circle(16, 16, 16);
            avatar.setClip(clip);
            
            // User info
            VBox userInfo = new VBox(2);
            userInfo.setAlignment(Pos.CENTER_LEFT);
            
            Label nameLabel = new Label(session.personaName());
            nameLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            nameLabel.setTextFill(Color.WHITE);
            
            String mmrText = session.mmr() != null ? "MMR: " + session.mmr() : "MMR: --";
            Label mmrLabel = new Label(mmrText);
            mmrLabel.setFont(Font.font("System", 10));
            mmrLabel.setTextFill(Color.web("#9ca3af"));
            
            userInfo.getChildren().addAll(nameLabel, mmrLabel);
            
            // Logout button
            Button logoutButton = new Button("Logout");
            styleButton(logoutButton, "#374151", "#4b5563");
            logoutButton.setFont(Font.font("System", 10));
            logoutButton.setOnAction(e -> {
                authService.logout();
                updateView();
                onLogout.run();
            });
            
            profileView.getChildren().addAll(avatar, userInfo, logoutButton);
            getChildren().add(profileView);
            
        } else {
            getChildren().add(loginView);
        }
    }
    
    private void showError(String error) {
        // Show temporary error message
        Label errorLabel = new Label("⚠️ " + error);
        errorLabel.setTextFill(Color.web("#ef4444"));
        errorLabel.setFont(Font.font("System", 11));
        
        getChildren().add(0, errorLabel);
        
        // Remove after 5 seconds
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                Platform.runLater(() -> getChildren().remove(errorLabel));
            } catch (InterruptedException ignored) {}
        }).start();
    }
    
    private void styleButton(Button btn, String bg, String hover) {
        String base = "-fx-background-color: %s; -fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 6 12;";
        btn.setStyle(base.formatted(bg));
        btn.setOnMouseEntered(e -> btn.setStyle(base.formatted(hover)));
        btn.setOnMouseExited(e -> btn.setStyle(base.formatted(bg)));
    }
    
    /**
     * Refreshes the view to reflect current auth state.
     */
    public void refresh() {
        Platform.runLater(this::updateView);
    }
}

