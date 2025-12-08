package com.dota2assistant.domain.auth;

import com.dota2assistant.domain.model.UserSession;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Domain service for authentication operations.
 * Coordinates between auth client, session storage, and credential store.
 */
public interface AuthenticationService {
    
    /**
     * Checks if user is currently logged in with a valid session.
     */
    boolean isLoggedIn();
    
    /**
     * Gets the current session if logged in.
     */
    Optional<UserSession> getCurrentSession();
    
    /**
     * Initiates Steam login flow. Opens browser for user to authenticate.
     * @param onSuccess Called with session when login succeeds
     * @param onError Called with error message on failure
     */
    void login(Consumer<UserSession> onSuccess, Consumer<String> onError);
    
    /**
     * Logs out the current user and clears session.
     */
    void logout();
    
    /**
     * Attempts to restore session from persistent storage.
     * @return The restored session if valid, empty otherwise
     */
    Optional<UserSession> restoreSession();
    
    /**
     * Updates the current session's MMR from OpenDota.
     */
    CompletableFuture<Optional<Integer>> refreshMmr();
    
    /**
     * Updates favorite heroes based on match history analysis.
     */
    CompletableFuture<Void> refreshFavoriteHeroes();
}

