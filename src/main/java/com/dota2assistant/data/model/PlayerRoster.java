package com.dota2assistant.data.model;

import javafx.beans.property.*;
import java.time.LocalDateTime;

/**
 * Represents a player's position on a team roster.
 * This includes information about the player's role within the team.
 */
public class PlayerRoster {
    
    public enum Position {
        CARRY(1, "Carry/Position 1"),
        MID(2, "Mid/Position 2"),
        OFFLANE(3, "Offlane/Position 3"),
        SOFT_SUPPORT(4, "Soft Support/Position 4"),
        HARD_SUPPORT(5, "Hard Support/Position 5"),
        SUBSTITUTE(6, "Substitute"),
        COACH(7, "Coach"),
        UNKNOWN(0, "Unknown");
        
        private final int value;
        private final String displayName;
        
        Position(int value, String displayName) {
            this.value = value;
            this.displayName = displayName;
        }
        
        public int getValue() {
            return value;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public static Position fromValue(int value) {
            for (Position position : values()) {
                if (position.getValue() == value) {
                    return position;
                }
            }
            return UNKNOWN;
        }
    }
    
    private final LongProperty teamId;
    private final LongProperty accountId;
    private final StringProperty name;
    private final ObjectProperty<Position> position;
    private final BooleanProperty isActive;
    private final ObjectProperty<LocalDateTime> joinDate;
    private final ObjectProperty<LocalDateTime> leaveDate;
    
    public PlayerRoster(long teamId, long accountId, String name, Position position, 
                       boolean isActive, LocalDateTime joinDate, LocalDateTime leaveDate) {
        this.teamId = new SimpleLongProperty(teamId);
        this.accountId = new SimpleLongProperty(accountId);
        this.name = new SimpleStringProperty(name);
        this.position = new SimpleObjectProperty<>(position);
        this.isActive = new SimpleBooleanProperty(isActive);
        this.joinDate = new SimpleObjectProperty<>(joinDate);
        this.leaveDate = new SimpleObjectProperty<>(leaveDate);
    }
    
    // Default constructor for new roster entries
    public PlayerRoster(long teamId, long accountId, String name) {
        this(teamId, accountId, name, Position.UNKNOWN, 
             true, LocalDateTime.now(), null);
    }

    public long getTeamId() {
        return teamId.get();
    }

    public LongProperty teamIdProperty() {
        return teamId;
    }

    public void setTeamId(long teamId) {
        this.teamId.set(teamId);
    }

    public long getAccountId() {
        return accountId.get();
    }

    public LongProperty accountIdProperty() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId.set(accountId);
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

    public Position getPosition() {
        return position.get();
    }

    public ObjectProperty<Position> positionProperty() {
        return position;
    }

    public void setPosition(Position position) {
        this.position.set(position);
    }

    public boolean isActive() {
        return isActive.get();
    }

    public BooleanProperty isActiveProperty() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive.set(active);
    }

    public LocalDateTime getJoinDate() {
        return joinDate.get();
    }

    public ObjectProperty<LocalDateTime> joinDateProperty() {
        return joinDate;
    }

    public void setJoinDate(LocalDateTime joinDate) {
        this.joinDate.set(joinDate);
    }

    public LocalDateTime getLeaveDate() {
        return leaveDate.get();
    }

    public ObjectProperty<LocalDateTime> leaveDateProperty() {
        return leaveDate;
    }

    public void setLeaveDate(LocalDateTime leaveDate) {
        this.leaveDate.set(leaveDate);
    }
    
    /**
     * Gets position display name (e.g., "Carry/Position 1")
     */
    public String getPositionDisplay() {
        return position.get().getDisplayName();
    }
    
    /**
     * Gets position number (1-5, 0 for unknown)
     */
    public int getPositionNumber() {
        return position.get().getValue();
    }
    
    /**
     * Calculates the duration this player has been on the team.
     * @return String representation of the duration (e.g., "2 months", "1 year")
     */
    public String getDurationOnTeam() {
        LocalDateTime endDate = (leaveDate.get() != null) ? leaveDate.get() : LocalDateTime.now();
        LocalDateTime startDate = joinDate.get();
        
        long years = java.time.temporal.ChronoUnit.YEARS.between(startDate, endDate);
        if (years > 0) {
            return years + (years == 1 ? " year" : " years");
        }
        
        long months = java.time.temporal.ChronoUnit.MONTHS.between(startDate, endDate);
        if (months > 0) {
            return months + (months == 1 ? " month" : " months");
        }
        
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        return days + (days == 1 ? " day" : " days");
    }
    
    @Override
    public String toString() {
        return name.get() + " (" + getPositionDisplay() + ")";
    }
}