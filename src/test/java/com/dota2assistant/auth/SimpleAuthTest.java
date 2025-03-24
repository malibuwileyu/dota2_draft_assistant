package com.dota2assistant.auth;

import javafx.application.Application;
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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple test application for Steam authentication.
 */
public class SimpleAuthTest extends Application {
    
    private Label statusLabel;
    private TextArea logArea;
    private Button loginButton;
    private Button logoutButton;
    private Label userInfoLabel;
    
    private final AtomicBoolean isAuthInProgress = new AtomicBoolean(false);
    
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
        
        // Buttons at the bottom
        loginButton = new Button("Login with Steam");
        logoutButton = new Button("Logout");
        Button checkSessionButton = new Button("Check Saved Session");
        
        HBox buttonBox = new HBox(10, loginButton, logoutButton, checkSessionButton);
        buttonBox.setAlignment(Pos.CENTER);
        VBox bottomBox = new VBox(10, userInfoLabel, buttonBox);
        bottomBox.setAlignment(Pos.CENTER);
        root.setBottom(bottomBox);
        
        // Setup button actions
        loginButton.setOnAction(event -> startLogin());
        logoutButton.setOnAction(event -> log("Logout clicked"));
        checkSessionButton.setOnAction(event -> log("Check session clicked"));
        
        // Create scene and show stage
        Scene scene = new Scene(root, 600, 400);
        primaryStage.setTitle("Steam Auth Simple Test");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    private void startLogin() {
        if (isAuthInProgress.get()) {
            log("Authentication already in progress");
            return;
        }
        
        isAuthInProgress.set(true);
        log("Starting authentication...");
        statusLabel.setText("Authentication in progress...");
        
        // Create and start a simple auth callback server
        AuthCallbackServer callbackServer = new AuthCallbackServer();
        try {
            CompletableFuture<Map<String, Object>> callbackFuture = callbackServer.startAndWaitForCallback();
            
            // Generate login URL (hardcoded for test)
            String loginUrl = "https://steamcommunity.com/openid/login" +
                    "?openid.ns=http://specs.openid.net/auth/2.0" +
                    "&openid.mode=checkid_setup" +
                    "&openid.return_to=http://localhost:8080/login/oauth2/code/steam" +
                    "&openid.realm=http://localhost:8080" +
                    "&openid.identity=http://specs.openid.net/auth/2.0/identifier_select" +
                    "&openid.claimed_id=http://specs.openid.net/auth/2.0/identifier_select";
            
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
                    
                    // Parse Steam ID from the URL (simplified)
                    String steamId = parseSteamId(callbackUrl);
                    if (steamId != null) {
                        log("Authentication successful! Steam ID: " + steamId);
                        javafx.application.Platform.runLater(() -> {
                            statusLabel.setText("Logged in");
                            userInfoLabel.setText("User Steam ID: " + steamId);
                        });
                    } else {
                        log("Could not extract Steam ID from callback");
                        javafx.application.Platform.runLater(() -> {
                            statusLabel.setText("Authentication failed");
                        });
                    }
                } finally {
                    callbackServer.stopServer();
                    isAuthInProgress.set(false);
                }
            }).exceptionally(ex -> {
                log("Authentication error: " + ex.getMessage());
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Authentication failed");
                });
                callbackServer.stopServer();
                isAuthInProgress.set(false);
                return null;
            });
        } catch (IOException e) {
            log("Failed to start callback server: " + e.getMessage());
            statusLabel.setText("Authentication failed: Could not start callback server");
            isAuthInProgress.set(false);
        }
    }
    
    private String parseSteamId(String url) {
        // Very simplified extraction - in real app use regex or proper parsing
        if (url.contains("openid.identity=")) {
            int start = url.indexOf("openid.identity=") + "openid.identity=".length();
            String part = url.substring(start);
            if (part.contains("/")) {
                return part.substring(part.lastIndexOf("/") + 1);
            }
        }
        return null;
    }
    
    private void log(String message) {
        javafx.application.Platform.runLater(() -> {
            logArea.appendText(message + "\n");
        });
        System.out.println(message);
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}