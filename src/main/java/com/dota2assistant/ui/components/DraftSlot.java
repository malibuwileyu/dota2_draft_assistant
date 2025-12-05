package com.dota2assistant.ui.components;

import com.dota2assistant.domain.model.Hero;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * A single slot in the draft panel (pick or ban) that can resize.
 */
public class DraftSlot extends StackPane {
    
    private static final String CDN_BASE = "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/";
    
    private final boolean isPick;
    private final Rectangle background;
    private final Rectangle border;
    private ImageView heroImage;
    private Hero currentHero;
    private double slotWidth = 50;
    private double slotHeight = 28;
    
    public DraftSlot(boolean isPick) {
        this.isPick = isPick;
        
        border = new Rectangle();
        border.setArcWidth(4);
        border.setArcHeight(4);
        border.setFill(Color.TRANSPARENT);
        border.setStroke(Color.web(isPick ? "#22c55e" : "#ef4444"));
        border.setStrokeWidth(2);
        border.setOpacity(0.6);
        
        background = new Rectangle();
        background.setArcWidth(3);
        background.setArcHeight(3);
        background.setFill(Color.web("#1e293b"));
        
        setAlignment(Pos.CENTER);
        getChildren().addAll(border, background);
        resize(slotWidth, slotHeight);
    }
    
    public void resize(double width, double height) {
        this.slotWidth = width;
        this.slotHeight = height;
        
        border.setWidth(width);
        border.setHeight(height);
        background.setWidth(width - 4);
        background.setHeight(height - 4);
        
        setPrefSize(width, height);
        setMinSize(width, height);
        setMaxSize(width, height);
        
        // Resize hero image if present
        if (heroImage != null && currentHero != null) {
            heroImage.setFitWidth(width - 6);
            heroImage.setFitHeight(height - 6);
        }
    }
    
    public void setActive(boolean active) {
        if (active) {
            border.setOpacity(1.0);
            border.setStrokeWidth(2.5);
            background.setFill(Color.web(isPick ? "#14532d" : "#7f1d1d"));
        } else {
            border.setOpacity(0.6);
            border.setStrokeWidth(2);
            background.setFill(Color.web("#1e293b"));
        }
    }
    
    public void setHero(Hero hero) {
        this.currentHero = hero;
        if (heroImage != null) {
            getChildren().remove(heroImage);
        }
        
        if (hero != null) {
            String imageUrl = CDN_BASE + hero.name().replace("npc_dota_hero_", "") + ".png";
            Image image = new Image(imageUrl, slotWidth, slotHeight, true, true, true);
            heroImage = new ImageView(image);
            heroImage.setFitWidth(slotWidth - 6);
            heroImage.setFitHeight(slotHeight - 6);
            heroImage.setPreserveRatio(true);
            getChildren().add(heroImage);
            setActive(false);
        }
    }
    
    public void clear() {
        currentHero = null;
        if (heroImage != null) {
            getChildren().remove(heroImage);
            heroImage = null;
        }
        background.setFill(Color.web("#1e293b"));
        setActive(false);
    }
}
