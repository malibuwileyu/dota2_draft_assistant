package com.dota2assistant.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a Steam user with profile information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SteamUser implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @JsonProperty("steamid")
    private String steamId;
    
    @JsonProperty("personaname")
    private String username;
    
    @JsonProperty("profileurl")
    private String profileUrl;
    
    @JsonProperty("avatar")
    private String avatarUrl;
    
    @JsonProperty("avatarmedium")
    private String avatarMediumUrl;
    
    @JsonProperty("avatarfull")
    private String avatarFullUrl;

    // Last login timestamp
    @JsonProperty("lastlogoff")
    private long lastLogoff;
    
    // Account creation timestamp 
    @JsonProperty("timecreated")
    private long timeCreated;

    // Default constructor
    public SteamUser() {
    }
    
    // Constructor with steamId
    public SteamUser(String steamId) {
        this.steamId = steamId;
    }
    
    // Getters and Setters
    
    public String getSteamId() {
        return steamId;
    }

    public void setSteamId(String steamId) {
        this.steamId = steamId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public void setProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAvatarMediumUrl() {
        return avatarMediumUrl;
    }

    public void setAvatarMediumUrl(String avatarMediumUrl) {
        this.avatarMediumUrl = avatarMediumUrl;
    }

    public String getAvatarFullUrl() {
        return avatarFullUrl;
    }

    public void setAvatarFullUrl(String avatarFullUrl) {
        this.avatarFullUrl = avatarFullUrl;
    }

    public long getLastLogoff() {
        return lastLogoff;
    }

    public void setLastLogoff(long lastLogoff) {
        this.lastLogoff = lastLogoff;
    }

    public long getTimeCreated() {
        return timeCreated;
    }

    public void setTimeCreated(long timeCreated) {
        this.timeCreated = timeCreated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SteamUser steamUser = (SteamUser) o;
        return Objects.equals(steamId, steamUser.steamId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(steamId);
    }

    @Override
    public String toString() {
        return "SteamUser{" +
                "steamId='" + steamId + '\'' +
                ", username='" + username + '\'' +
                '}';
    }
}