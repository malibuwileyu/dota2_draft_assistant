package com.dota2assistant.ui.components;

import com.dota2assistant.domain.model.Hero;
import javafx.geometry.Pos;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/**
 * A hero selection button with image.
 */
public class HeroButton extends StackPane {
    
    private static final String CDN_BASE = "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/";
    private static final double BUTTON_WIDTH = 72;
    private static final double BUTTON_HEIGHT = 41;
    
    private final Hero hero;
    private final ImageView imageView;
    private boolean available = true;
    
    public HeroButton(Hero hero) {
        this.hero = hero;
        
        // Load hero image from CDN
        String imageUrl = CDN_BASE + hero.name().replace("npc_dota_hero_", "") + ".png";
        Image image = new Image(imageUrl, BUTTON_WIDTH, BUTTON_HEIGHT, true, true, true);
        imageView = new ImageView(image);
        imageView.setFitWidth(BUTTON_WIDTH);
        imageView.setFitHeight(BUTTON_HEIGHT);
        imageView.setPreserveRatio(true);
        
        setPrefSize(BUTTON_WIDTH, BUTTON_HEIGHT);
        setMinSize(BUTTON_WIDTH, BUTTON_HEIGHT);
        setMaxSize(BUTTON_WIDTH, BUTTON_HEIGHT);
        setAlignment(Pos.CENTER);
        getChildren().add(imageView);
        
        setStyle("-fx-background-color: #1e293b; -fx-background-radius: 4; -fx-cursor: hand;");
        
        Tooltip tooltip = new Tooltip(hero.localizedName());
        Tooltip.install(this, tooltip);
        
        setupHoverEffect();
    }
    
    private void setupHoverEffect() {
        setOnMouseEntered(e -> {
            if (available) setStyle("-fx-background-color: #3b82f6; -fx-background-radius: 4; -fx-cursor: hand;");
        });
        setOnMouseExited(e -> {
            if (available) setStyle("-fx-background-color: #1e293b; -fx-background-radius: 4; -fx-cursor: hand;");
        });
    }
    
    private boolean filterMatch = true; // Whether hero matches current search filter
    
    public void setAvailable(boolean available) {
        this.available = available;
        updateVisualState();
    }
    
    /**
     * Set whether this hero matches the current search filter.
     * Filtered-out heroes are grayed but still clickable (unlike picked heroes).
     */
    public void setFilterMatch(boolean matches) {
        this.filterMatch = matches;
        updateVisualState();
    }
    
    private void updateVisualState() {
        if (!available) {
            // Picked/banned - fully disabled
            ColorAdjust grayscale = new ColorAdjust();
            grayscale.setSaturation(-0.8);
            grayscale.setBrightness(-0.4);
            imageView.setEffect(grayscale);
            setStyle("-fx-background-color: #0f172a; -fx-background-radius: 4;");
            setDisable(true);
            setOpacity(1.0);
        } else if (!filterMatch) {
            // Doesn't match filter - grayed out but clickable
            ColorAdjust dim = new ColorAdjust();
            dim.setSaturation(-0.6);
            dim.setBrightness(-0.3);
            imageView.setEffect(dim);
            setStyle("-fx-background-color: #1e293b; -fx-background-radius: 4; -fx-cursor: hand;");
            setDisable(false);
            setOpacity(0.4);
        } else {
            // Available and matches filter
            imageView.setEffect(null);
            setStyle("-fx-background-color: #1e293b; -fx-background-radius: 4; -fx-cursor: hand;");
            setDisable(false);
            setOpacity(1.0);
        }
    }
    
    public void setOnHeroClick(Consumer<Hero> handler) {
        setOnMouseClicked(e -> { if (available) handler.accept(hero); });
    }
    
    public Hero getHero() { return hero; }
}

