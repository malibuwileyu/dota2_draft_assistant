package com.dota2assistant.data.model;

import javafx.beans.property.*;

import java.time.LocalDateTime;

/**
 * Represents a player's performance statistics with a specific hero,
 * weighted according to match history depth and recency.
 */
public class PlayerHeroPerformance {
    
    private final ObjectProperty<Hero> hero;
    private final IntegerProperty matches;
    private final DoubleProperty winRate;
    private final DoubleProperty kdaRatio;
    private final DoubleProperty impactScore;
    private final DoubleProperty confidenceScore;
    private final DoubleProperty pickRate;
    private final BooleanProperty isComfortPick;
    private final DoubleProperty globalMetaWeight;
    private final DoubleProperty personalDataWeight;
    private final ObjectProperty<LocalDateTime> lastPlayed;
    
    /**
     * Creates a new player hero performance object.
     * 
     * @param hero The hero
     * @param matches Number of matches played with this hero
     * @param winRate Win rate with this hero (0.0-1.0)
     * @param kdaRatio KDA ratio with this hero
     * @param impactScore Impact score for this hero (calculated from various metrics)
     * @param confidenceScore Confidence in these stats (0.0-1.0)
     * @param pickRate How often the player picks this hero (0.0-1.0)
     * @param isComfortPick Whether this is considered a comfort pick for the player
     * @param globalMetaWeight Weight given to global meta data (0.0-1.0)
     * @param personalDataWeight Weight given to personal data (0.0-1.0)
     * @param lastPlayed When the player last played this hero
     */
    public PlayerHeroPerformance(Hero hero, int matches, double winRate, double kdaRatio, 
                               double impactScore, double confidenceScore, double pickRate,
                               boolean isComfortPick, double globalMetaWeight, 
                               double personalDataWeight, LocalDateTime lastPlayed) {
        this.hero = new SimpleObjectProperty<>(hero);
        this.matches = new SimpleIntegerProperty(matches);
        this.winRate = new SimpleDoubleProperty(winRate);
        this.kdaRatio = new SimpleDoubleProperty(kdaRatio);
        this.impactScore = new SimpleDoubleProperty(impactScore);
        this.confidenceScore = new SimpleDoubleProperty(confidenceScore);
        this.pickRate = new SimpleDoubleProperty(pickRate);
        this.isComfortPick = new SimpleBooleanProperty(isComfortPick);
        this.globalMetaWeight = new SimpleDoubleProperty(globalMetaWeight);
        this.personalDataWeight = new SimpleDoubleProperty(personalDataWeight);
        this.lastPlayed = new SimpleObjectProperty<>(lastPlayed);
    }
    
    /**
     * Creates a default performance object with just the hero and base values.
     * 
     * @param hero The hero
     */
    public PlayerHeroPerformance(Hero hero) {
        this(hero, 0, 0.0, 0.0, 0.0, 0.0, 0.0, false, 1.0, 0.0, null);
    }

    public Hero getHero() {
        return hero.get();
    }

    public ObjectProperty<Hero> heroProperty() {
        return hero;
    }

    public void setHero(Hero hero) {
        this.hero.set(hero);
    }

    public int getMatches() {
        return matches.get();
    }

    public IntegerProperty matchesProperty() {
        return matches;
    }

    public void setMatches(int matches) {
        this.matches.set(matches);
    }

    public double getWinRate() {
        return winRate.get();
    }

    public DoubleProperty winRateProperty() {
        return winRate;
    }

    public void setWinRate(double winRate) {
        this.winRate.set(winRate);
    }

    public double getKdaRatio() {
        return kdaRatio.get();
    }

    public DoubleProperty kdaRatioProperty() {
        return kdaRatio;
    }

    public void setKdaRatio(double kdaRatio) {
        this.kdaRatio.set(kdaRatio);
    }

    public double getImpactScore() {
        return impactScore.get();
    }

    public DoubleProperty impactScoreProperty() {
        return impactScore;
    }

    public void setImpactScore(double impactScore) {
        this.impactScore.set(impactScore);
    }

    public double getConfidenceScore() {
        return confidenceScore.get();
    }

    public DoubleProperty confidenceScoreProperty() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore.set(confidenceScore);
    }

    public double getPickRate() {
        return pickRate.get();
    }

    public DoubleProperty pickRateProperty() {
        return pickRate;
    }

    public void setPickRate(double pickRate) {
        this.pickRate.set(pickRate);
    }

    public boolean isComfortPick() {
        return isComfortPick.get();
    }

    public BooleanProperty isComfortPickProperty() {
        return isComfortPick;
    }

    public void setComfortPick(boolean isComfortPick) {
        this.isComfortPick.set(isComfortPick);
    }

    public double getGlobalMetaWeight() {
        return globalMetaWeight.get();
    }

    public DoubleProperty globalMetaWeightProperty() {
        return globalMetaWeight;
    }

    public void setGlobalMetaWeight(double globalMetaWeight) {
        this.globalMetaWeight.set(globalMetaWeight);
    }

    public double getPersonalDataWeight() {
        return personalDataWeight.get();
    }

    public DoubleProperty personalDataWeightProperty() {
        return personalDataWeight;
    }

    public void setPersonalDataWeight(double personalDataWeight) {
        this.personalDataWeight.set(personalDataWeight);
    }

    public LocalDateTime getLastPlayed() {
        return lastPlayed.get();
    }

    public ObjectProperty<LocalDateTime> lastPlayedProperty() {
        return lastPlayed;
    }

    public void setLastPlayed(LocalDateTime lastPlayed) {
        this.lastPlayed.set(lastPlayed);
    }
    
    /**
     * Returns a formatted win rate as a percentage.
     * 
     * @return Win rate percentage string
     */
    public String getWinRateFormatted() {
        return String.format("%.1f%%", winRate.get() * 100);
    }
    
    /**
     * Returns a formatted KDA ratio.
     * 
     * @return KDA ratio string
     */
    public String getKdaFormatted() {
        return String.format("%.1f", kdaRatio.get());
    }
    
    /**
     * Returns a formatted impact score.
     * 
     * @return Impact score string
     */
    public String getImpactFormatted() {
        return String.format("%.1f", impactScore.get());
    }
    
    /**
     * Returns a string representation of the data weight balance.
     * 
     * @return Weight distribution string (e.g., "60% global / 40% personal")
     */
    public String getWeightDistribution() {
        return String.format("%.0f%% global / %.0f%% personal", 
                           globalMetaWeight.get() * 100, 
                           personalDataWeight.get() * 100);
    }
    
    /**
     * Calculates a single performance score that combines various metrics.
     * 
     * @return Performance score from 0.0-10.0
     */
    public double calculatePerformanceScore() {
        // Calculate weighted score based on win rate, impact, and confidence
        double baseScore = (winRate.get() * 6) + (impactScore.get() * 4);
        
        // Apply confidence penalty for low match counts
        return baseScore * confidenceScore.get();
    }
    
    /**
     * Gets the formatted performance score.
     * 
     * @return Formatted performance score
     */
    public String getPerformanceScoreFormatted() {
        return String.format("%.1f", calculatePerformanceScore());
    }
}