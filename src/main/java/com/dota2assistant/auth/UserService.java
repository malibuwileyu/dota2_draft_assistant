package com.dota2assistant.auth;

import com.dota2assistant.data.model.Session;
import com.dota2assistant.data.model.UserPreference;
import com.dota2assistant.data.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Service for managing user authentication and profiles.
 */
@Service
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    private final SteamApiService steamApiService;
    private final SteamAuthenticationManager steamAuthManager;
    private final UserRepository userRepository;
    
    // Currently logged in user
    private SteamUser currentUser;
    private Session currentSession;
    
    @Autowired
    public UserService(
            SteamApiService steamApiService, 
            SteamAuthenticationManager steamAuthManager,
            UserRepository userRepository) {
        this.steamApiService = steamApiService;
        this.steamAuthManager = steamAuthManager;
        this.userRepository = userRepository;
        
        // Set this UserService in the SteamApiService for callback handling
        this.steamApiService.setUserService(this);
        
        // Validate the API key on startup to catch configuration issues early
        validateSteamApiKey();
    }
    
    /**
     * Validates the Steam API key on startup.
     * This helps identify configuration issues early.
     */
    private void validateSteamApiKey() {
        boolean isValid = steamApiService.validateApiKey();
        if (isValid) {
            logger.info("Steam API key validation successful");
        } else {
            logger.error("⚠️ Steam API key validation failed. Authentication will not work properly.");
            logger.error("Please check your Steam API key in application.properties.override or set the STEAM_API_KEY environment variable.");
        }
    }
    
    /**
     * Checks if the Steam API key is valid.
     * 
     * @return True if the key is valid, false otherwise
     */
    public boolean isSteamApiKeyValid() {
        return steamApiService.validateApiKey();
    }
    
    /**
     * Gets the Steam login URL for authentication.
     */
    public String getLoginUrl() {
        return steamAuthManager.getLoginUrl();
    }
    
    /**
     * Processes the OpenID authentication response and logs in the user.
     * 
     * @param returnUrl The full URL returned from Steam OpenID
     * @param ipAddress The IP address of the client
     * @param userAgent The user agent of the client
     * @return True if login was successful, false otherwise
     */
    public boolean processLogin(String returnUrl, String ipAddress, String userAgent) {
        // Validate API key first
        if (!steamApiService.validateApiKey()) {
            logger.error("Cannot process login: Steam API key validation failed");
            return false;
        }
        
        // Extract Steam ID from the authentication response
        String steamId = steamAuthManager.validateAndGetSteamId(returnUrl);
        
        if (steamId == null || steamId.isEmpty()) {
            logger.warn("Failed to validate Steam authentication or extract Steam ID from response");
            logger.debug("Return URL was: {}", returnUrl);
            return false;
        }
        
        logger.info("Successfully extracted Steam ID: {}", steamId);
        
        // Get profile information from Steam API
        logger.debug("Fetching user profile for Steam ID: {}", steamId);
        Optional<SteamUser> userOptional = steamApiService.getUserProfile(steamId);
        
        if (userOptional.isPresent()) {
            SteamUser user = userOptional.get();
            logger.info("Successfully retrieved profile for user: {} ({})", 
                    user.getUsername(), user.getSteamId());
            
            // Check if user owns Dota 2 - non-blocking warning only
            boolean ownsDota2 = steamApiService.userOwnsDota2(steamId);
            if (!ownsDota2) {
                logger.warn("User does not own Dota 2: {}. This is a warning only.", steamId);
                // Non-blocking - we allow login even if user doesn't own Dota 2
            }
            
            try {
                // Store user in database
                long accountId = userRepository.saveUser(user);
                logger.debug("User saved in database with account ID: {}", accountId);
                
                // Create authentication session with client information
                currentSession = userRepository.createSession(accountId, ipAddress, userAgent);
                logger.debug("Created session: {}", currentSession.getSessionToken().substring(0, 8) + "...");
                
                // Set as current user
                currentUser = user;
                logger.info("User successfully logged in: {} ({}) from IP: {}", 
                        user.getUsername(), user.getSteamId(), ipAddress);
                
                // Notify listeners about successful login
                notifyLoginStateChanged(true);
                
                return true;
            } catch (Exception e) {
                logger.error("Error saving user or session to database", e);
                return false;
            }
        } else {
            logger.error("Could not retrieve profile for Steam ID: {}. Check the Steam API key and permissions.", steamId);
            return false;
        }
    }
    
    /**
     * Processes the OpenID authentication response and logs in the user.
     * This overload is provided for backward compatibility.
     * 
     * @param returnUrl The full URL returned from Steam OpenID
     * @return True if login was successful, false otherwise
     */
    public boolean processLogin(String returnUrl) {
        return processLogin(returnUrl, "localhost", "Dota2DraftAssistant");
    }
    
    /**
     * Logs out the current user.
     */
    public void logout() {
        if (currentUser != null && currentSession != null) {
            String username = currentUser.getUsername();
            String steamId = currentUser.getSteamId();
            
            logger.info("User logging out: {} ({})", username, steamId);
            userRepository.invalidateSession(currentSession.getSessionToken());
            
            // Clear user state
            currentUser = null;
            currentSession = null;
            
            // Notify listeners about logout
            notifyLoginStateChanged(false);
            
            logger.info("User logged out: {} ({})", username, steamId);
        } else {
            logger.debug("Logout called but no user was logged in");
        }
    }
    
    /**
     * Checks if a user is currently logged in.
     */
    public boolean isLoggedIn() {
        return currentUser != null && currentSession != null && currentSession.isValid();
    }
    
    /**
     * Gets the currently logged in user.
     */
    public Optional<SteamUser> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }
    
    /**
     * Gets a user by their Steam ID.
     */
    public Optional<SteamUser> getUserBySteamId(String steamId) {
        // First check if it's the current user
        if (currentUser != null && steamId.equals(currentUser.getSteamId())) {
            return Optional.of(currentUser);
        }
        
        // Then check in the database
        Optional<SteamUser> user = userRepository.getUserBySteamId(steamId);
        
        // If not found in database, try to fetch from Steam API
        if (user.isEmpty()) {
            user = steamApiService.getUserProfile(steamId);
            
            // If found in Steam API, save to database
            user.ifPresent(userRepository::saveUser);
        }
        
        return user;
    }
    
    /**
     * Restores the user session from a session token.
     * 
     * @param sessionToken The session token to restore
     * @return True if the session was successfully restored
     */
    public boolean restoreSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isEmpty()) {
            return false;
        }
        
        Optional<Session> sessionOpt = userRepository.getSessionByToken(sessionToken);
        if (sessionOpt.isEmpty() || !sessionOpt.get().isValid()) {
            return false;
        }
        
        Session session = sessionOpt.get();
        
        // Get user from database
        Optional<SteamUser> userOpt = getUserByAccountId(session.getAccountId());
        if (userOpt.isEmpty()) {
            return false;
        }
        
        // Set current user and session
        currentUser = userOpt.get();
        currentSession = session;
        
        logger.info("Session restored for user: {}", currentUser.getUsername());
        
        // Notify listeners about the restored session (login)
        notifyLoginStateChanged(true);
        
        return true;
    }
    
    /**
     * Gets a user by their account ID.
     * 
     * @param accountId The account ID to look up
     * @return The user if found
     */
    public Optional<SteamUser> getUserByAccountId(long accountId) {
        // Convert to String representation of steamId for lookup
        String steamId = String.valueOf(accountId);
        return userRepository.getUserBySteamId(steamId);
    }
    
    /**
     * Gets the current session token.
     */
    public Optional<String> getCurrentSessionToken() {
        if (currentSession != null && currentSession.isValid()) {
            return Optional.of(currentSession.getSessionToken());
        }
        return Optional.empty();
    }
    
    /**
     * Gets all preferences for the current user.
     */
    public Map<String, UserPreference> getCurrentUserPreferences() {
        if (!isLoggedIn() || currentSession == null) {
            return Map.of();
        }
        return userRepository.getUserPreferences(currentSession.getAccountId());
    }
    
    /**
     * Gets a preference for the current user.
     * 
     * @param preferenceName The name of the preference
     * @param defaultValue The default value if preference not found
     * @return The preference value
     */
    public String getUserPreference(String preferenceName, String defaultValue) {
        if (!isLoggedIn() || currentSession == null) {
            return defaultValue;
        }
        
        return userRepository.getPreference(currentSession.getAccountId(), preferenceName)
                .orElse(defaultValue);
    }
    
    /**
     * Sets a preference for the current user.
     * 
     * @param preferenceName The name of the preference
     * @param preferenceValue The value of the preference
     */
    public void setUserPreference(String preferenceName, String preferenceValue) {
        if (!isLoggedIn() || currentSession == null) {
            return;
        }
        
        userRepository.savePreference(currentSession.getAccountId(), preferenceName, preferenceValue);
    }
    
    /**
     * Gets all skill metrics for the current user.
     */
    public Map<String, Double> getCurrentUserSkillMetrics() {
        if (!isLoggedIn() || currentSession == null) {
            return Map.of();
        }
        return userRepository.getUserSkillMetrics(currentSession.getAccountId());
    }
    
    /**
     * Sets a skill metric for the current user.
     * 
     * @param metricName The name of the metric
     * @param metricValue The value of the metric
     */
    public void setUserSkillMetric(String metricName, double metricValue) {
        if (!isLoggedIn() || currentSession == null) {
            return;
        }
        
        userRepository.saveSkillMetric(currentSession.getAccountId(), metricName, metricValue);
    }
    
    /**
     * Get access to the Steam API service.
     * This method allows controllers to access the SteamApiService when needed.
     * 
     * @return The SteamApiService instance
     */
    public SteamApiService getSteamApiService() {
        return this.steamApiService;
    }
    
    /**
     * Observable login state change field
     * This can be used by external components to observe login state changes
     */
    private final java.beans.PropertyChangeSupport loginStateObservable = new java.beans.PropertyChangeSupport(this);
    
    /**
     * Add a listener for login state changes.
     * 
     * @param listener The listener to add
     */
    public void addLoginStateListener(java.beans.PropertyChangeListener listener) {
        loginStateObservable.addPropertyChangeListener("loginState", listener);
    }
    
    /**
     * Remove a login state listener.
     * 
     * @param listener The listener to remove
     */
    public void removeLoginStateListener(java.beans.PropertyChangeListener listener) {
        loginStateObservable.removePropertyChangeListener("loginState", listener);
    }
    
    /**
     * Notify all listeners that the login state has changed.
     * 
     * @param isLoggedIn The new login state
     */
    public void notifyLoginStateChanged(boolean isLoggedIn) {
        loginStateObservable.firePropertyChange("loginState", !isLoggedIn, isLoggedIn);
        logger.info("Login state change notification sent: {}", isLoggedIn ? "logged in" : "logged out");
    }
}