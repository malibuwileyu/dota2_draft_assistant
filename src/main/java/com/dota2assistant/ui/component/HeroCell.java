package com.dota2assistant.ui.component;

import com.dota2assistant.data.model.Hero;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Component for displaying a hero with image and name.
 */
public class HeroCell extends VBox {
    
    private static final Logger logger = LoggerFactory.getLogger(HeroCell.class);
    private static final int DEFAULT_WIDTH = 85;
    private static final int DEFAULT_HEIGHT = 48;
    
    private final Hero hero;
    private final ImageView imageView;
    private final Label nameLabel;
    private boolean isSelected = false;
    
    /**
     * Creates a new HeroCell for the given hero.
     * 
     * @param hero The hero to display
     */
    public HeroCell(Hero hero) {
        this.hero = hero;
        this.imageView = new ImageView();
        this.nameLabel = new Label();
        
        initializeComponent();
    }
    
    /**
     * Sets up the component UI.
     */
    private void initializeComponent() {
        // Set up the VBox
        getStyleClass().add("hero-cell");
        setAlignment(Pos.CENTER);
        setSpacing(3);
        
        // Set up the image view
        imageView.setFitWidth(DEFAULT_WIDTH);
        imageView.setFitHeight(DEFAULT_HEIGHT);
        imageView.setPreserveRatio(true);
        imageView.getStyleClass().add("hero-image");
        
        // Set up the name label
        nameLabel.setText(hero.getLocalizedName());
        nameLabel.setTextAlignment(TextAlignment.CENTER);
        nameLabel.setWrapText(true);
        nameLabel.setPrefWidth(DEFAULT_WIDTH);
        nameLabel.getStyleClass().add("hero-name");
        
        // Create a stack pane to hold the image (allows for overlays)
        StackPane imagePane = new StackPane(imageView);
        imagePane.getStyleClass().add("hero-pane");
        imagePane.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        
        // Set up tooltip with additional hero information
        StringBuilder tooltipText = new StringBuilder();
        tooltipText.append(hero.getLocalizedName());
        
        if (hero.getRoles() != null && !hero.getRoles().isEmpty()) {
            tooltipText.append("\nRoles: ").append(String.join(", ", hero.getRoles()));
        }
        
        if (hero.getPrimaryAttribute() != null) {
            tooltipText.append("\nAttribute: ").append(hero.getPrimaryAttribute().toUpperCase());
        }
        
        Tooltip tooltip = new Tooltip(tooltipText.toString());
        Tooltip.install(this, tooltip);
        
        // Add components to the VBox
        getChildren().addAll(imagePane, nameLabel);
        VBox.setVgrow(imagePane, Priority.ALWAYS);
        
        // Load the hero image
        loadHeroImage();
        
        // Log component creation
        logger.debug("Created HeroCell for {} with image URL: {}", 
                    hero.getLocalizedName(), hero.getImageUrl());
    }
    
    /**
     * Loads the hero image from the URL or uses a placeholder.
     */
    private void loadHeroImage() {
        try {
            // Special handling for Dawnbreaker
            if (hero.getId() == 135 || 
                (hero.getName() != null && hero.getName().toLowerCase().contains("dawn")) || 
                (hero.getLocalizedName() != null && hero.getLocalizedName().toLowerCase().contains("dawn"))) {
                try {
                    // Try to use the file we know exists
                    InputStream is = getClass().getResourceAsStream("/images/heroes/135_dawnbreaker.png");
                    if (is != null) {
                        Image image = new Image(is);
                        if (!image.isError()) {
                            imageView.setImage(image);
                            logger.error("SPECIAL CASE: Loaded Dawnbreaker image successfully!");
                            return;
                        }
                    } else {
                        is = getClass().getResourceAsStream("/images/heroes/135.png");
                        if (is != null) {
                            Image image = new Image(is);
                            if (!image.isError()) {
                                imageView.setImage(image);
                                logger.error("SPECIAL CASE: Loaded Dawnbreaker (135.png) image successfully!");
                                return;
                            }
                        }
                    }
                    logger.error("SPECIAL CASE: Could not load Dawnbreaker image with special handling");
                } catch (Exception e) {
                    logger.error("SPECIAL CASE: Error in Dawnbreaker handling: {}", e.getMessage());
                }
            }
            
            if (hero.getImageUrl() != null && !hero.getImageUrl().isEmpty()) {
                String imageUrl = hero.getImageUrl();
                
                // Special logging for Dawnbreaker
                if (hero.getName() != null && hero.getName().toLowerCase().contains("dawn") || 
                    hero.getLocalizedName() != null && hero.getLocalizedName().toLowerCase().contains("dawn") ||
                    imageUrl.contains("135_dawn") || hero.getId() == 135 || 
                    imageUrl.contains("135.png")) {
                    logger.error("DAWNBREAKER DEBUG - ID: {}, Name: {}, LocalizedName: {}, URL: {}", 
                               hero.getId(), hero.getName(), hero.getLocalizedName(), imageUrl);
                    
                    // Force a check for the file
                    try {
                        InputStream testStream = getClass().getResourceAsStream(imageUrl);
                        if (testStream != null) {
                            logger.error("DAWNBREAKER IMAGE FILE EXISTS!");
                            testStream.close();
                        } else {
                            logger.error("DAWNBREAKER IMAGE FILE DOES NOT EXIST!");
                        }
                    } catch (Exception e) {
                        logger.error("Error testing Dawnbreaker image: {}", e.getMessage());
                    }
                }
                
                logger.debug("Attempting to load image from: {}", imageUrl);
                
                if (imageUrl.startsWith("/")) {
                    // Load from local resources
                    logger.debug("Hero {} (ID: {}) - Trying to load image from: {}", 
                               hero.getLocalizedName(), hero.getId(), imageUrl);
                               
                    InputStream is = getClass().getResourceAsStream(imageUrl);
                    if (is != null) {
                        Image image = new Image(is);
                        if (!image.isError()) {
                            imageView.setImage(image);
                            logger.debug("Successfully loaded local image for {} (ID: {})", 
                                       hero.getLocalizedName(), hero.getId());
                            return;
                        } else {
                            logger.warn("Error loading local image for {} (ID: {}): {}, falling back to placeholder", 
                                       hero.getLocalizedName(), hero.getId(), imageUrl);
                        }
                    } else {
                        logger.warn("Could not find local image for {} (ID: {}): {}, falling back to placeholder", 
                                   hero.getLocalizedName(), hero.getId(), imageUrl);
                                   
                        // Try to list available files in the directory for debugging
                        try {
                            logger.info("Available files in resources directory:");
                            InputStream dirStream = getClass().getResourceAsStream("/images/heroes/");
                            if (dirStream != null) {
                                logger.info("Directory exists but cannot list contents programmatically");
                                dirStream.close();
                            } else {
                                logger.warn("Directory /images/heroes/ does not exist in resources");
                            }
                        } catch (Exception e) {
                            logger.error("Error checking directory: {}", e.getMessage());
                        }
                    }
                } else {
                    // Try to load from URL
                    Image image = new Image(imageUrl, true);
                    imageView.setImage(image);
                    
                    // Handle loading errors
                    image.errorProperty().addListener((obs, oldVal, newVal) -> {
                        if (Boolean.TRUE.equals(newVal)) {
                            logger.warn("Failed to load image from URL: {}, falling back to placeholder", imageUrl);
                            loadPlaceholderImage();
                        }
                    });
                    
                    // If image loads successfully, return
                    if (!image.isError()) {
                        return;
                    }
                }
            }
            
            // If we get here, we need to load a placeholder
            loadPlaceholderImage();
        } catch (Exception e) {
            logger.error("Failed to load hero image for {}: {}", hero.getLocalizedName(), e.getMessage());
            loadPlaceholderImage();
        }
    }
    
    /**
     * Loads a placeholder image when the hero image is not available.
     */
    private void loadPlaceholderImage() {
        try {
            // Try to load placeholder based on attribute
            String attributePlaceholder = "/images/placeholder.png";
            String heroesAttributePlaceholder = "/images/heroes/placeholder.png";
            
            if (hero.getPrimaryAttribute() != null) {
                switch (hero.getPrimaryAttribute().toLowerCase()) {
                    case "str":
                        attributePlaceholder = "/images/placeholder_str.png";
                        heroesAttributePlaceholder = "/images/heroes/placeholder_str.png";
                        break;
                    case "agi":
                        attributePlaceholder = "/images/placeholder_agi.png";
                        heroesAttributePlaceholder = "/images/heroes/placeholder_agi.png";
                        break;
                    case "int":
                        attributePlaceholder = "/images/placeholder_int.png";
                        heroesAttributePlaceholder = "/images/heroes/placeholder_int.png";
                        break;
                }
            }
            
            // Try main images directory first
            logger.debug("Loading placeholder image: {}", attributePlaceholder);
            InputStream is = getClass().getResourceAsStream(attributePlaceholder);
            
            // Try heroes subdirectory if main directory failed
            if (is == null) {
                logger.debug("Trying heroes subdirectory: {}", heroesAttributePlaceholder);
                is = getClass().getResourceAsStream(heroesAttributePlaceholder);
            }
            
            if (is != null) {
                Image placeholder = new Image(is);
                imageView.setImage(placeholder);
                logger.debug("Successfully loaded attribute-specific placeholder");
            } else {
                // Fallback default placeholder
                is = getClass().getResourceAsStream("/images/placeholder.png");
                if (is == null) {
                    is = getClass().getResourceAsStream("/images/heroes/placeholder.png");
                }
                
                if (is != null) {
                    Image placeholder = new Image(is);
                    imageView.setImage(placeholder);
                    logger.debug("Successfully loaded default placeholder");
                } else {
                    // Create a default colored rectangle as last resort
                    logger.warn("Failed to load any placeholder image; creating default colored shape");
                    
                    // Create a colored rectangle based on attribute
                    Color color = Color.GRAY;
                    if (hero.getPrimaryAttribute() != null) {
                        switch (hero.getPrimaryAttribute().toLowerCase()) {
                            case "str":
                                color = Color.RED;
                                break;
                            case "agi":
                                color = Color.GREEN;
                                break;
                            case "int":
                                color = Color.BLUE;
                                break;
                        }
                    }
                    
                    // Create a new image from the rectangle
                    WritableImage placeholderImage = new WritableImage(DEFAULT_WIDTH, DEFAULT_HEIGHT);
                    PixelWriter pixelWriter = placeholderImage.getPixelWriter();
                    for (int x = 0; x < DEFAULT_WIDTH; x++) {
                        for (int y = 0; y < DEFAULT_HEIGHT; y++) {
                            // Add a border
                            if (x == 0 || y == 0 || x == DEFAULT_WIDTH - 1 || y == DEFAULT_HEIGHT - 1) {
                                pixelWriter.setColor(x, y, Color.WHITE);
                            } else {
                                pixelWriter.setColor(x, y, color);
                            }
                        }
                    }
                    imageView.setImage(placeholderImage);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load placeholder image: {}", e.getMessage());
        }
    }
    
    /**
     * Sets whether this hero cell is selected.
     * 
     * @param selected true if selected, false otherwise
     */
    public void setSelected(boolean selected) {
        this.isSelected = selected;
        if (selected) {
            getStyleClass().add("selected");
        } else {
            getStyleClass().remove("selected");
        }
        logger.debug("{} selection state: {}", hero.getLocalizedName(), selected);
    }
    
    /**
     * Returns whether this hero cell is selected.
     * 
     * @return true if selected, false otherwise
     */
    public boolean isSelected() {
        return isSelected;
    }
    
    /**
     * Gets the hero associated with this cell.
     * 
     * @return the hero
     */
    public Hero getHero() {
        return hero;
    }
}