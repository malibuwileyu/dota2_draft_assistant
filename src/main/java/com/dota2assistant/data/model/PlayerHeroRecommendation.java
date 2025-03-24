package com.dota2assistant.data.model;

import javafx.beans.property.*;

/**
 * Represents a hero recommendation for a player.
 * This combines global meta information with player-specific performance.
 */
public class PlayerHeroRecommendation {
    
    private final ObjectProperty<Hero> hero;
    private final DoubleProperty recommendationScore;
    private final BooleanProperty isComfortPick;
    private final IntegerProperty matchesPlayed;
    private final DoubleProperty winRate;
    private final DoubleProperty kdaRatio;
    private final StringProperty recommendationReason;
    private final ObjectProperty<RecommendationType> recommendationType;
    
    /**
     * Recommendation type enum for categorizing hero recommendations.
     */
    public enum RecommendationType {
        COMFORT("Comfort Pick"),
        META("Meta Pick"),
        COUNTER("Counter Pick"),
        SYNERGY("Team Synergy"),
        BALANCED("Balanced Pick");
        
        private final String displayName;
        
        RecommendationType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Creates a new player hero recommendation.
     * 
     * @param hero The hero
     * @param recommendationScore Score for this recommendation (0-10)
     * @param isComfortPick Whether this is a comfort pick for the player
     * @param matchesPlayed Number of matches played with this hero
     * @param winRate Win rate with this hero (0.0-1.0)
     * @param kdaRatio KDA ratio with this hero
     * @param recommendationReason Reason for recommending this hero
     * @param recommendationType Type of recommendation
     */
    public PlayerHeroRecommendation(Hero hero, double recommendationScore, boolean isComfortPick,
                                  int matchesPlayed, double winRate, double kdaRatio,
                                  String recommendationReason, RecommendationType recommendationType) {
        this.hero = new SimpleObjectProperty<>(hero);
        this.recommendationScore = new SimpleDoubleProperty(recommendationScore);
        this.isComfortPick = new SimpleBooleanProperty(isComfortPick);
        this.matchesPlayed = new SimpleIntegerProperty(matchesPlayed);
        this.winRate = new SimpleDoubleProperty(winRate);
        this.kdaRatio = new SimpleDoubleProperty(kdaRatio);
        this.recommendationReason = new SimpleStringProperty(recommendationReason);
        this.recommendationType = new SimpleObjectProperty<>(recommendationType);
    }
    
    /**
     * Creates a recommendation from a performance object.
     * 
     * @param performance Player performance data
     * @param reason Recommendation reason
     * @param type Recommendation type
     */
    public PlayerHeroRecommendation(PlayerHeroPerformance performance, String reason, RecommendationType type) {
        this(
            performance.getHero(),
            performance.calculatePerformanceScore(),
            performance.isComfortPick(),
            performance.getMatches(),
            performance.getWinRate(),
            performance.getKdaRatio(),
            reason,
            type
        );
    }
    
    /**
     * Creates a recommendation based solely on meta data with no player history.
     * 
     * @param hero The hero
     * @param metaScore Meta score for this hero (0-10)
     * @param reason Recommendation reason
     */
    public PlayerHeroRecommendation(Hero hero, double metaScore, String reason) {
        this(
            hero,
            metaScore,
            false,
            0,
            0.0,
            0.0,
            reason,
            RecommendationType.META
        );
    }

    public Hero getHero() {
        return hero.get();
    }

    public ObjectProperty<Hero> heroProperty() {
        return hero;
    }

    public double getRecommendationScore() {
        return recommendationScore.get();
    }

    public DoubleProperty recommendationScoreProperty() {
        return recommendationScore;
    }

    public boolean isComfortPick() {
        return isComfortPick.get();
    }

    public BooleanProperty isComfortPickProperty() {
        return isComfortPick;
    }

    public int getMatchesPlayed() {
        return matchesPlayed.get();
    }

    public IntegerProperty matchesPlayedProperty() {
        return matchesPlayed;
    }

    public double getWinRate() {
        return winRate.get();
    }

    public DoubleProperty winRateProperty() {
        return winRate;
    }

    public double getKdaRatio() {
        return kdaRatio.get();
    }

    public DoubleProperty kdaRatioProperty() {
        return kdaRatio;
    }

    public String getRecommendationReason() {
        return recommendationReason.get();
    }

    public StringProperty recommendationReasonProperty() {
        return recommendationReason;
    }

    public RecommendationType getRecommendationType() {
        return recommendationType.get();
    }

    public ObjectProperty<RecommendationType> recommendationTypeProperty() {
        return recommendationType;
    }
    
    /**
     * Gets formatted win rate as percentage.
     * 
     * @return Win rate percentage string
     */
    public String getWinRateFormatted() {
        return String.format("%.1f%%", getWinRate() * 100);
    }
    
    /**
     * Gets formatted KDA ratio.
     * 
     * @return KDA ratio string
     */
    public String getKdaFormatted() {
        return String.format("%.1f", getKdaRatio());
    }
    
    /**
     * Gets formatted recommendation score.
     * 
     * @return Recommendation score string
     */
    public String getScoreFormatted() {
        return String.format("%.1f", getRecommendationScore());
    }
    
    /**
     * Gets a display string for the hero.
     * 
     * @return Hero name with comfort indicator if applicable
     */
    public String getHeroDisplay() {
        return isComfortPick() ? getHero().getLocalizedName() + " ★" : getHero().getLocalizedName();
    }
    
    /**
     * Determines if this recommendation has player history.
     * 
     * @return True if player has history with this hero
     */
    public boolean hasPlayerHistory() {
        return getMatchesPlayed() > 0;
    }
}