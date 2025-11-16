package com.dota2assistant.ui.component;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.PlayerHeroPerformance;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.TilePane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Grid view component for displaying heroes.
 */
public class HeroGridView extends FlowPane {
    
    private static final Logger logger = LoggerFactory.getLogger(HeroGridView.class);
    private static final double CELL_WIDTH = 85;
    private static final double CELL_HEIGHT = 85;
    private static final double SPACING = 10;
    
    private final ObservableList<Hero> heroes;
    private final List<HeroCell> heroCells = new ArrayList<>();
    private HeroCell selectedCell;
    private Consumer<Hero> onHeroSelectedCallback;
    private Map<Integer, PlayerHeroPerformance> heroPerformanceMap = new HashMap<>();
    
    /**
     * Creates a new HeroGridView with the given heroes.
     * 
     * @param heroes The list of heroes to display
     */
    public HeroGridView(ObservableList<Hero> heroes) {
        this.heroes = heroes;
        initializeComponent();
        populateGrid();
        
        // Listen for changes in the heroes list
        heroes.addListener((ListChangeListener<Hero>) change -> {
            while (change.next()) {
                if (change.wasAdded() || change.wasRemoved() || change.wasReplaced()) {
                    logger.debug("Heroes list changed, repopulating grid");
                    populateGrid();
                }
            }
        });
    }
    
    /**
     * Initializes the component UI.
     */
    private void initializeComponent() {
        // Set style class
        getStyleClass().add("hero-grid");
        
        // Set layout properties
        setPadding(new Insets(SPACING));
        setHgap(SPACING);
        setVgap(SPACING);
        setMinWidth(USE_COMPUTED_SIZE);
        setMinHeight(USE_COMPUTED_SIZE);
        setPrefWrapLength(CELL_WIDTH * 5 + SPACING * 4); // Show 5 heroes per row
        setMaxWidth(CELL_WIDTH * 5 + SPACING * 6); // Limit width to prevent horizontal scrolling
        
        // Log initialization
        logger.debug("HeroGridView initialized with spacing {} and wrap length {}", 
                    SPACING, getPrefWrapLength());
    }
    
    /**
     * Populates the grid with hero cells.
     */
    private void populateGrid() {
        getChildren().clear();
        heroCells.clear();
        
        int performanceDataCount = 0;
        
        for (Hero hero : heroes) {
            // Check if we have performance data for this hero
            PlayerHeroPerformance performance = null;
            if (heroPerformanceMap.containsKey(hero.getId())) {
                performance = heroPerformanceMap.get(hero.getId());
                performanceDataCount++;
            }
            
            // Create cell with hero and performance data if available
            HeroCell cell = new HeroCell(hero, performance);
            
            // Set click handling
            cell.setOnMouseClicked(event -> {
                logger.debug("Hero cell clicked: {}", hero.getLocalizedName());
                
                // Deselect previous selection
                if (selectedCell != null) {
                    selectedCell.setSelected(false);
                }
                
                // Select the new cell
                cell.setSelected(true);
                selectedCell = cell;
                
                // Notify callback
                if (onHeroSelectedCallback != null) {
                    onHeroSelectedCallback.accept(hero);
                }
            });
            
            heroCells.add(cell);
            getChildren().add(cell);
        }
        
        logger.debug("Grid populated with {} hero cells ({} with performance data)", 
                   heroCells.size(), performanceDataCount);
    }
    
    /**
     * Sets the callback for when a hero is selected.
     * 
     * @param callback The callback to invoke when a hero is selected
     */
    public void setOnHeroSelected(Consumer<Hero> callback) {
        this.onHeroSelectedCallback = callback;
    }
    
    /**
     * Gets the currently selected hero.
     * 
     * @return The selected hero, or null if none is selected
     */
    public Hero getSelectedHero() {
        return selectedCell != null ? selectedCell.getHero() : null;
    }
    
    /**
     * Clears the current selection.
     */
    public void clearSelection() {
        if (selectedCell != null) {
            selectedCell.setSelected(false);
            selectedCell = null;
        }
    }
    
    /**
     * Selects the hero with the given ID.
     * 
     * @param heroId The ID of the hero to select
     * @return true if a hero with the given ID was found and selected, false otherwise
     */
    public boolean selectHero(int heroId) {
        for (HeroCell cell : heroCells) {
            if (cell.getHero().getId() == heroId) {
                // Deselect previous selection
                if (selectedCell != null) {
                    selectedCell.setSelected(false);
                }
                
                // Select the new cell
                cell.setSelected(true);
                selectedCell = cell;
                
                // Notify callback
                if (onHeroSelectedCallback != null) {
                    onHeroSelectedCallback.accept(cell.getHero());
                }
                
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Updates the hero cells with player performance data.
     * 
     * @param performanceData Map of hero IDs to performance data
     */
    public void updateHeroPerformanceData(Map<Integer, PlayerHeroPerformance> performanceData) {
        if (performanceData == null || performanceData.isEmpty()) {
            logger.debug("No performance data provided, skipping update");
            return;
        }
        
        // Store the new performance data
        heroPerformanceMap.clear();
        heroPerformanceMap.putAll(performanceData);
        logger.debug("Updated hero performance map with {} entries", heroPerformanceMap.size());
        
        // Update existing cells
        for (HeroCell cell : heroCells) {
            Hero hero = cell.getHero();
            if (hero != null && heroPerformanceMap.containsKey(hero.getId())) {
                PlayerHeroPerformance performance = heroPerformanceMap.get(hero.getId());
                cell.setPlayerPerformance(performance);
                
                // Log comfort picks for debugging
                if (performance.isComfortPick()) {
                    logger.debug("Comfort hero found: {} (matches: {}, win rate: {})", 
                               hero.getLocalizedName(), 
                               performance.getMatches(),
                               performance.getWinRateFormatted());
                }
            }
        }
    }
}