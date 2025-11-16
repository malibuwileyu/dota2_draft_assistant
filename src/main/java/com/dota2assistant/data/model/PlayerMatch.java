package com.dota2assistant.data.model;

import com.dota2assistant.util.GameModeUtil;
import javafx.beans.property.*;

import java.time.LocalDateTime;

/**
 * Represents a match played by a player.
 */
public class PlayerMatch {
    
    private final LongProperty matchId;
    private final ObjectProperty<Hero> hero;
    private final BooleanProperty won;
    private final IntegerProperty duration; // in seconds
    private final ObjectProperty<LocalDateTime> date;
    private final IntegerProperty kills;
    private final IntegerProperty deaths;
    private final IntegerProperty assists;
    private final StringProperty gameMode;
    
    // Additional properties for match details
    private final BooleanProperty radiantSide = new SimpleBooleanProperty(true);
    private final BooleanProperty radiantWin = new SimpleBooleanProperty(false);
    private final BooleanProperty favorite = new SimpleBooleanProperty(false);
    private final BooleanProperty hidden = new SimpleBooleanProperty(false);
    private final StringProperty notes = new SimpleStringProperty("");
    
    public PlayerMatch(long matchId, Hero hero, boolean won, int duration, LocalDateTime date,
                      int kills, int deaths, int assists, String gameMode) {
        this.matchId = new SimpleLongProperty(matchId);
        this.hero = new SimpleObjectProperty<>(hero);
        this.won = new SimpleBooleanProperty(won);
        this.duration = new SimpleIntegerProperty(duration);
        this.date = new SimpleObjectProperty<>(date);
        this.kills = new SimpleIntegerProperty(kills);
        this.deaths = new SimpleIntegerProperty(deaths);
        this.assists = new SimpleIntegerProperty(assists);
        this.gameMode = new SimpleStringProperty(gameMode);
    }

    public long getMatchId() {
        return matchId.get();
    }

    public LongProperty matchIdProperty() {
        return matchId;
    }

    public void setMatchId(long matchId) {
        this.matchId.set(matchId);
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

    public boolean isWon() {
        return won.get();
    }

    public BooleanProperty wonProperty() {
        return won;
    }

    public void setWon(boolean won) {
        this.won.set(won);
    }

    public int getDuration() {
        return duration.get();
    }

    public IntegerProperty durationProperty() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration.set(duration);
    }

    public LocalDateTime getDate() {
        return date.get();
    }

    public ObjectProperty<LocalDateTime> dateProperty() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date.set(date);
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
     * Gets the raw game mode string.
     */
    public String getGameMode() {
        return gameMode.get();
    }
    
    /**
     * Gets the game mode ID using the GameModeUtil.
     */
    public int getGameModeId() {
        return GameModeUtil.getGameModeId(getGameMode());
    }
    
    /**
     * Gets a formatted game mode string.
     */
    public String getFormattedGameMode() {
        return GameModeUtil.formatGameMode(getGameMode());
    }

    public StringProperty gameModeProperty() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode.set(gameMode);
    }
    
    /**
     * Gets a formatted string representation of the match result.
     * @return "Win" or "Loss"
     */
    public String getResultFormatted() {
        return won.get() ? "Win" : "Loss";
    }
    
    /**
     * Gets a formatted string representation of the match duration.
     * @return Duration in mm:ss format
     */
    public String getDurationFormatted() {
        int minutes = duration.get() / 60;
        int seconds = duration.get() % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    /**
     * Gets a formatted string representation of the player's KDA.
     * @return KDA in the format "K/D/A"
     */
    public String getKdaFormatted() {
        return String.format("%d/%d/%d", kills.get(), deaths.get(), assists.get());
    }
    
    /**
     * Calculates the KDA ratio.
     * @return (K + A) / D, or K + A if D is 0
     */
    public double getKdaRatio() {
        if (deaths.get() == 0) {
            return kills.get() + assists.get();
        }
        return (kills.get() + assists.get()) / (double) deaths.get();
    }
    
    /**
     * Checks if the player was on the Radiant side.
     */
    public boolean isRadiantSide() {
        return radiantSide.get();
    }

    /**
     * Property for radiant side status.
     */
    public BooleanProperty radiantSideProperty() {
        return radiantSide;
    }

    /**
     * Sets whether the player was on the Radiant side.
     */
    public void setRadiantSide(boolean radiantSide) {
        this.radiantSide.set(radiantSide);
    }

    /**
     * Checks if Radiant won the match.
     */
    public boolean isRadiantWin() {
        return radiantWin.get();
    }

    /**
     * Property for Radiant win status.
     */
    public BooleanProperty radiantWinProperty() {
        return radiantWin;
    }

    /**
     * Sets whether Radiant won the match.
     */
    public void setRadiantWin(boolean radiantWin) {
        this.radiantWin.set(radiantWin);
    }
    
    /**
     * Checks if the match is marked as a favorite.
     */
    public boolean isFavorite() {
        return favorite.get();
    }

    /**
     * Property for favorite status.
     */
    public BooleanProperty favoriteProperty() {
        return favorite;
    }

    /**
     * Sets whether the match is marked as a favorite.
     */
    public void setFavorite(boolean favorite) {
        this.favorite.set(favorite);
    }

    /**
     * Checks if the match is hidden.
     */
    public boolean isHidden() {
        return hidden.get();
    }

    /**
     * Property for hidden status.
     */
    public BooleanProperty hiddenProperty() {
        return hidden;
    }

    /**
     * Sets whether the match is hidden.
     */
    public void setHidden(boolean hidden) {
        this.hidden.set(hidden);
    }

    /**
     * Gets the notes for this match.
     */
    public String getNotes() {
        return notes.get();
    }

    /**
     * Property for match notes.
     */
    public StringProperty notesProperty() {
        return notes;
    }

    /**
     * Sets the notes for this match.
     */
    public void setNotes(String notes) {
        this.notes.set(notes);
    }
}