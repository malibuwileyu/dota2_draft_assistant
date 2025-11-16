package com.dota2assistant.data.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a team of players in Dota 2.
 * This model supports both professional teams and user-created teams.
 */
public class PlayerTeam {
    
    private final LongProperty id;
    private final StringProperty name;
    private final StringProperty tag;
    private final StringProperty logoUrl;
    private final BooleanProperty isProfessional;
    private final ObjectProperty<LocalDateTime> lastUpdated;
    private final ObservableList<PlayerRoster> roster;
    private final IntegerProperty matchesPlayed;
    private final DoubleProperty winRate;
    
    public PlayerTeam(long id, String name, String tag, String logoUrl, 
                     boolean isProfessional, LocalDateTime lastUpdated,
                     List<PlayerRoster> roster, int matchesPlayed, double winRate) {
        this.id = new SimpleLongProperty(id);
        this.name = new SimpleStringProperty(name);
        this.tag = new SimpleStringProperty(tag);
        this.logoUrl = new SimpleStringProperty(logoUrl);
        this.isProfessional = new SimpleBooleanProperty(isProfessional);
        this.lastUpdated = new SimpleObjectProperty<>(lastUpdated);
        this.roster = FXCollections.observableArrayList(roster);
        this.matchesPlayed = new SimpleIntegerProperty(matchesPlayed);
        this.winRate = new SimpleDoubleProperty(winRate);
    }
    
    // Default constructor for new teams
    public PlayerTeam() {
        this(0, "", "", "", false, LocalDateTime.now(), 
             FXCollections.observableArrayList(), 0, 0.0);
    }

    public long getId() {
        return id.get();
    }

    public LongProperty idProperty() {
        return id;
    }

    public void setId(long id) {
        this.id.set(id);
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public String getTag() {
        return tag.get();
    }

    public StringProperty tagProperty() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag.set(tag);
    }

    public String getLogoUrl() {
        return logoUrl.get();
    }

    public StringProperty logoUrlProperty() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl.set(logoUrl);
    }

    public boolean isProfessional() {
        return isProfessional.get();
    }

    public BooleanProperty isProfessionalProperty() {
        return isProfessional;
    }

    public void setIsProfessional(boolean isProfessional) {
        this.isProfessional.set(isProfessional);
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated.get();
    }

    public ObjectProperty<LocalDateTime> lastUpdatedProperty() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated.set(lastUpdated);
    }

    public ObservableList<PlayerRoster> getRoster() {
        return roster;
    }
    
    public void addPlayer(PlayerRoster player) {
        roster.add(player);
    }
    
    public void removePlayer(PlayerRoster player) {
        roster.remove(player);
    }
    
    public int getMatchesPlayed() {
        return matchesPlayed.get();
    }

    public IntegerProperty matchesPlayedProperty() {
        return matchesPlayed;
    }

    public void setMatchesPlayed(int matchesPlayed) {
        this.matchesPlayed.set(matchesPlayed);
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
    
    /**
     * Gets a formatted string representation of the win rate.
     * @return Win rate as a percentage string (e.g., "65.4%")
     */
    public String getWinRateFormatted() {
        return String.format("%.1f%%", winRate.get() * 100);
    }
    
    /**
     * Determines if the team has enough players to be considered valid.
     * @return True if the team has at least 5 players
     */
    public boolean isComplete() {
        return roster.size() >= 5;
    }
    
    /**
     * Checks if the team is active based on having recent matches.
     * @return True if the team has played matches within the last 3 months
     */
    public boolean isActive() {
        return lastUpdated.get().isAfter(LocalDateTime.now().minusMonths(3));
    }
    
    @Override
    public String toString() {
        return name.get() + (tag.get().isEmpty() ? "" : " [" + tag.get() + "]");
    }
}