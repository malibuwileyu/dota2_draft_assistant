package com.dota2assistant.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents an authenticated user session.
 * Immutable record containing Steam profile and session metadata.
 */
public record UserSession(
    String steamId,
    String personaName,
    String avatarUrl,
    String profileUrl,
    Integer mmr,
    List<Integer> favoriteHeroIds,
    List<String> preferredRoles,
    Instant createdAt,
    Instant lastLoginAt,
    Instant expiresAt,
    String jwtToken  // JWT token for backend API calls
) {
    
    /**
     * Creates a new session from Steam login response (legacy, no token).
     */
    public static UserSession fromSteamLogin(String steamId, String personaName, 
                                              String avatarUrl, String profileUrl) {
        Instant now = Instant.now();
        return new UserSession(
            steamId, personaName, avatarUrl, profileUrl,
            null, List.of(), List.of(), now, now,
            now.plusSeconds(30 * 24 * 60 * 60), null
        );
    }
    
    /**
     * Creates an updated session with new last login time.
     */
    public UserSession withRefreshedLogin() {
        Instant now = Instant.now();
        return new UserSession(
            steamId, personaName, avatarUrl, profileUrl,
            mmr, favoriteHeroIds, preferredRoles, createdAt, now,
            now.plusSeconds(30 * 24 * 60 * 60), jwtToken
        );
    }
    
    /**
     * Creates an updated session with MMR.
     */
    public UserSession withMmr(Integer newMmr) {
        return new UserSession(
            steamId, personaName, avatarUrl, profileUrl,
            newMmr, favoriteHeroIds, preferredRoles, createdAt, lastLoginAt, expiresAt, jwtToken
        );
    }
    
    /**
     * Creates an updated session with favorite heroes.
     */
    public UserSession withFavoriteHeroes(List<Integer> heroes) {
        return new UserSession(
            steamId, personaName, avatarUrl, profileUrl,
            mmr, heroes, preferredRoles, createdAt, lastLoginAt, expiresAt, jwtToken
        );
    }
    
    /**
     * Creates an updated session with preferred roles.
     */
    public UserSession withPreferredRoles(List<String> roles) {
        return new UserSession(
            steamId, personaName, avatarUrl, profileUrl,
            mmr, favoriteHeroIds, roles, createdAt, lastLoginAt, expiresAt, jwtToken
        );
    }
    
    /**
     * Creates an updated session with new JWT token.
     */
    public UserSession withToken(String newToken) {
        return new UserSession(
            steamId, personaName, avatarUrl, profileUrl,
            mmr, favoriteHeroIds, preferredRoles, createdAt, lastLoginAt, expiresAt, newToken
        );
    }
    
    /**
     * Checks if this session has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Gets the Steam ID as a 64-bit integer for API calls.
     */
    public long steamId64() {
        return Long.parseLong(steamId);
    }
    
    /**
     * Converts Steam ID to 32-bit format used by OpenDota.
     */
    public long accountId() {
        return steamId64() - 76561197960265728L;
    }
    
    /**
     * Checks if this session has a valid JWT token.
     */
    public boolean hasValidToken() {
        return jwtToken != null && !jwtToken.isBlank();
    }
}

