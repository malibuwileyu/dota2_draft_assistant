package com.dota2assistant.ui;

import com.dota2assistant.domain.auth.AuthenticationService;
import com.dota2assistant.domain.repository.HeroRepository;
import com.dota2assistant.infrastructure.api.BackendApiClient;
import com.dota2assistant.ui.components.LoginPanel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application view containing header bar and content area.
 * Header includes app title and login panel.
 */
public class MainView extends BorderPane {
    
    private static final Logger log = LoggerFactory.getLogger(MainView.class);
    
    private final HeroRepository heroRepository;
    private final BackendApiClient backendClient;
    private final AuthenticationService authService;
    
    private final LoginPanel loginPanel;
    private final PracticeDraftView practiceDraftView;
    
    public MainView(HeroRepository heroRepository, BackendApiClient backendClient,
                    AuthenticationService authService) {
        this.heroRepository = heroRepository;
        this.backendClient = backendClient;
        this.authService = authService;
        
        this.loginPanel = new LoginPanel(authService);
        this.practiceDraftView = new PracticeDraftView(heroRepository, backendClient);
        
        setupHeader();
        setupContent();
        
        // Log session state
        authService.getCurrentSession().ifPresentOrElse(
            session -> log.info("User logged in: {}", session.personaName()),
            () -> log.info("No active session")
        );
    }
    
    private void setupHeader() {
        // App title
        Label titleLabel = new Label("🎮 Dota 2 Draft Assistant");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);
        
        // Spacer to push login to right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Header bar
        HBox header = new HBox(15, titleLabel, spacer, loginPanel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 15, 8, 15));
        header.setStyle("-fx-background-color: #0f172a;");
        
        setTop(header);
    }
    
    private void setupContent() {
        setCenter(practiceDraftView);
        setStyle("-fx-background-color: #0a0e14;");
    }
}

