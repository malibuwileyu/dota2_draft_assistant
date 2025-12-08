package com.dota2assistant.infrastructure.auth;

import com.dota2assistant.domain.auth.AuthenticationService;
import com.dota2assistant.domain.model.UserSession;
import com.dota2assistant.infrastructure.api.OpenDotaClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Default implementation of AuthenticationService.
 * Coordinates Steam login, session persistence, and credential storage.
 */
@Service
public class DefaultAuthenticationService implements AuthenticationService {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultAuthenticationService.class);
    private static final String SESSION_TOKEN_KEY = "session_token";
    
    private final SteamAuthClient steamAuthClient;
    private final SessionRepository sessionRepository;
    private final CredentialStore credentialStore;
    private final OpenDotaClient openDotaClient;
    private final ObjectMapper objectMapper;
    
    private UserSession currentSession;
    
    public DefaultAuthenticationService(
            SteamAuthClient steamAuthClient,
            SessionRepository sessionRepository,
            CredentialStoreFactory credentialStoreFactory,
            OpenDotaClient openDotaClient,
            ObjectMapper objectMapper) {
        this.steamAuthClient = steamAuthClient;
        this.sessionRepository = sessionRepository;
        this.credentialStore = credentialStoreFactory.create();
        this.openDotaClient = openDotaClient;
        this.objectMapper = objectMapper;
        
        // Attempt to restore session on startup
        restoreSession().ifPresent(session -> {
            this.currentSession = session;
            log.info("Restored session for user: {}", session.personaName());
        });
    }
    
    @Override
    public boolean isLoggedIn() {
        return currentSession != null && !currentSession.isExpired();
    }
    
    @Override
    public Optional<UserSession> getCurrentSession() {
        if (currentSession != null && currentSession.isExpired()) {
            log.info("Session expired, clearing");
            logout();
        }
        return Optional.ofNullable(currentSession);
    }
    
    @Override
    public void login(Consumer<UserSession> onSuccess, Consumer<String> onError) {
        log.info("Initiating Steam login...");
        
        steamAuthClient.login(
            session -> {
                // Save session to database
                sessionRepository.save(session);
                
                // Store session token in secure credential store
                try {
                    String token = objectMapper.writeValueAsString(session);
                    credentialStore.store(SESSION_TOKEN_KEY, token);
                } catch (Exception e) {
                    log.warn("Failed to store session token in secure store: {}", e.getMessage());
                }
                
                currentSession = session;
                log.info("Login successful for user: {}", session.personaName());
                
                // Fetch additional data asynchronously
                refreshMmr();
                refreshFavoriteHeroes();
                
                onSuccess.accept(session);
            },
            error -> {
                log.error("Login failed: {}", error);
                onError.accept(error);
            }
        );
    }
    
    @Override
    public void logout() {
        if (currentSession != null) {
            log.info("Logging out user: {}", currentSession.personaName());
            
            // Clear from database
            sessionRepository.delete(currentSession.steamId());
            
            // Clear from credential store
            credentialStore.delete(SESSION_TOKEN_KEY);
            
            currentSession = null;
        }
    }
    
    @Override
    public Optional<UserSession> restoreSession() {
        // First try to restore from credential store (most secure)
        Optional<String> storedToken = credentialStore.retrieve(SESSION_TOKEN_KEY);
        if (storedToken.isPresent()) {
            try {
                UserSession session = objectMapper.readValue(storedToken.get(), UserSession.class);
                if (!session.isExpired()) {
                    // Update last login time
                    session = session.withRefreshedLogin();
                    sessionRepository.save(session);
                    return Optional.of(session);
                }
            } catch (Exception e) {
                log.warn("Failed to restore session from credential store: {}", e.getMessage());
            }
        }
        
        // Fall back to database
        Optional<UserSession> dbSession = sessionRepository.findActiveSession();
        if (dbSession.isPresent()) {
            UserSession session = dbSession.get().withRefreshedLogin();
            sessionRepository.save(session);
            
            // Re-store in credential store
            try {
                String token = objectMapper.writeValueAsString(session);
                credentialStore.store(SESSION_TOKEN_KEY, token);
            } catch (Exception e) {
                log.warn("Failed to update credential store: {}", e.getMessage());
            }
            
            return Optional.of(session);
        }
        
        return Optional.empty();
    }
    
    @Override
    public CompletableFuture<Optional<Integer>> refreshMmr() {
        if (currentSession == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        long accountId = currentSession.accountId();
        log.debug("Fetching MMR for account ID: {}", accountId);
        
        return openDotaClient.fetchPlayerMmr(accountId)
            .thenApply(mmrOpt -> {
                mmrOpt.ifPresent(mmr -> {
                    currentSession = currentSession.withMmr(mmr);
                    sessionRepository.save(currentSession);
                    log.info("Updated MMR to {} for user: {}", mmr, currentSession.personaName());
                });
                return mmrOpt;
            })
            .exceptionally(e -> {
                log.warn("Failed to fetch MMR: {}", e.getMessage());
                return Optional.empty();
            });
    }
    
    @Override
    public CompletableFuture<Void> refreshFavoriteHeroes() {
        if (currentSession == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        long accountId = currentSession.accountId();
        log.debug("Fetching favorite heroes for account ID: {}", accountId);
        
        return openDotaClient.fetchMostPlayedHeroes(accountId, 20)
            .thenAccept(heroes -> {
                if (!heroes.isEmpty()) {
                    List<Integer> heroIds = heroes.stream()
                        .limit(10) // Top 10 favorites
                        .toList();
                    currentSession = currentSession.withFavoriteHeroes(heroIds);
                    sessionRepository.save(currentSession);
                    log.info("Updated {} favorite heroes for user: {}", heroIds.size(), currentSession.personaName());
                }
            })
            .exceptionally(e -> {
                log.warn("Failed to fetch favorite heroes: {}", e.getMessage());
                return null;
            });
    }
}

