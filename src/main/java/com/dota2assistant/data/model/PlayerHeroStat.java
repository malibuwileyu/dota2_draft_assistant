package com.dota2assistant.data.model;

import javafx.beans.property.*;

import java.time.LocalDateTime;

/**
 * Represents hero statistics for a specific player.
 */
public class PlayerHeroStat {
    
    private final ObjectProperty<Hero> hero;
    private final IntegerProperty matches;
    private final IntegerProperty wins;
    private final IntegerProperty kills;
    private final IntegerProperty deaths;
    private final IntegerProperty assists;
    private final DoubleProperty winRate;
    private final DoubleProperty kdaRatio;
    private final ObjectProperty<LocalDateTime> lastPlayed;
    
    public PlayerHeroStat() {
        this.hero = new SimpleObjectProperty<>(null);
        this.matches = new SimpleIntegerProperty(0);
        this.wins = new SimpleIntegerProperty(0);
        this.kills = new SimpleIntegerProperty(0);
        this.deaths = new SimpleIntegerProperty(0);
        this.assists = new SimpleIntegerProperty(0);
        this.winRate = new SimpleDoubleProperty(0.0);
        this.kdaRatio = new SimpleDoubleProperty(0.0);
        this.lastPlayed = new SimpleObjectProperty<>(null);
    }
    
    public PlayerHeroStat(Hero hero, int matches, int wins, int kills, int deaths, int assists, double winRate, double kdaRatio, LocalDateTime lastPlayed) {
        this.hero = new SimpleObjectProperty<>(hero);
        this.matches = new SimpleIntegerProperty(matches);
        this.wins = new SimpleIntegerProperty(wins);
        this.kills = new SimpleIntegerProperty(kills);
        this.deaths = new SimpleIntegerProperty(deaths);
        this.assists = new SimpleIntegerProperty(assists);
        this.winRate = new SimpleDoubleProperty(winRate);
        this.kdaRatio = new SimpleDoubleProperty(kdaRatio);
        this.lastPlayed = new SimpleObjectProperty<>(lastPlayed);
    }
    
    public PlayerHeroStat(Hero hero, int matches, double winRate, double kdaRatio, LocalDateTime lastPlayed) {
        this(hero, matches, (int)(matches * winRate), 0, 0, 0, winRate, kdaRatio, lastPlayed);
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
    
    /**
     * Alias for setMatches for compatibility with other code
     */
    public void setMatchesPlayed(int matches) {
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

    public LocalDateTime getLastPlayed() {
        return lastPlayed.get();
    }

    public ObjectProperty<LocalDateTime> lastPlayedProperty() {
        return lastPlayed;
    }

    public void setLastPlayed(LocalDateTime lastPlayed) {
        this.lastPlayed.set(lastPlayed);
    }
    
    public int getWins() {
        return wins.get();
    }
    
    public IntegerProperty winsProperty() {
        return wins;
    }
    
    public void setWins(int wins) {
        this.wins.set(wins);
    }
    
    public int getKills() {
        return kills.get();
    }
    
    public IntegerProperty killsProperty() {
        return kills;
    }
    
    public void setKills(int kills) {
        this.kills.set(kills);
    }
    
    public int getDeaths() {
        return deaths.get();
    }
    
    public IntegerProperty deathsProperty() {
        return deaths;
    }
    
    public void setDeaths(int deaths) {
        this.deaths.set(deaths);
    }
    
    public int getAssists() {
        return assists.get();
    }
    
    public IntegerProperty assistsProperty() {
        return assists;
    }
    
    public void setAssists(int assists) {
        this.assists.set(assists);
    }
    
    /**
     * Gets a formatted string representation of the win rate.
     * @return Win rate as a percentage string (e.g., "65.4%")
     */
    public String getWinRateFormatted() {
        return String.format("%.1f%%", winRate.get() * 100);
    }
    
    /**
     * Gets a formatted string representation of the KDA ratio.
     * @return KDA ratio with one decimal place (e.g., "3.2")
     */
    public String getKdaFormatted() {
        return String.format("%.1f", kdaRatio.get());
    }
    
    /**
     * Gets matches played as a string.
     * @return Number of matches played as a string.
     */
    public String getMatchesPlayed() {
        return String.format("%d", matches.get());
    }
    
    /**
     * Gets KDA as a formatted string.
     * @return KDA as a formatted string (e.g., "10/5/12")
     */
    public String getKdaStats() {
        return String.format("%d/%d/%d", kills.get(), deaths.get(), assists.get());
    }
}