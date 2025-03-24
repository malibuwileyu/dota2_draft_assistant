package com.dota2assistant.auth;

import com.dota2assistant.util.PropertyLoader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom authentication manager for Steam OpenID.
 * Steam uses OpenID 2.0 instead of OAuth2, so we need custom handling.
 */
@Component
public class SteamAuthenticationManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SteamAuthenticationManager.class);
    private static final String STEAM_LOGIN_URL = "https://steamcommunity.com/openid/login";
    private static final Pattern STEAM_ID_PATTERN = Pattern.compile("/id/(\\d+)");
    // Extended pattern that matches both id and openid format: steamcommunity.com/openid/id/76561198xxxxxxxxx
    private static final Pattern STEAM_OPENID_PATTERN = Pattern.compile("(?:steamcommunity\\.com/openid/id|steamcommunity\\.com/id)/(\\d+)");
    // Pattern to extract ID from claimed_id in raw form
    private static final Pattern RAW_ID_PATTERN = Pattern.compile("(?:https?://)?(?:www\\.)?(?:steamcommunity\\.com/openid/id|steamcommunity\\.com/id)/(\\d+)");
    
    private final OkHttpClient httpClient;
    private final PropertyLoader propertyLoader;
    
    public SteamAuthenticationManager(OkHttpClient httpClient, PropertyLoader propertyLoader) {
        this.httpClient = httpClient;
        this.propertyLoader = propertyLoader;
    }
    
    /**
     * Generates a Steam OpenID login URL.
     * 
     * @return URL to redirect the user to for authentication
     */
    public String getLoginUrl() {
        String returnUrl = propertyLoader.getProperty("steam.auth.return_url", "http://localhost:8080/login/oauth2/code/steam");
        String realm = propertyLoader.getProperty("steam.auth.realm", "http://localhost:8080");
        
        // Build parameters for Steam OpenID
        Map<String, String> params = new HashMap<>();
        params.put("openid.ns", "http://specs.openid.net/auth/2.0");
        params.put("openid.mode", "checkid_setup");
        params.put("openid.return_to", returnUrl);
        params.put("openid.realm", realm);
        params.put("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select");
        params.put("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select");
        
        // Build the URL with parameters
        StringBuilder urlBuilder = new StringBuilder(STEAM_LOGIN_URL + "?");
        params.forEach((key, value) -> {
            if (urlBuilder.charAt(urlBuilder.length() - 1) != '?') {
                urlBuilder.append("&");
            }
            urlBuilder.append(key).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        
        logger.debug("Generated Steam login URL: {}", urlBuilder);
        return urlBuilder.toString();
    }
    
    /**
     * Validates the returned OpenID authentication response.
     * 
     * @param returnUrl The full URL returned from Steam
     * @return The authenticated Steam ID or null if validation failed
     */
    public String validateAndGetSteamId(String returnUrl) {
        try {
            logger.debug("Processing authentication return URL: {}", returnUrl);
            
            // Extract parameters from return URL
            URI uri = new URI(returnUrl);
            String query = uri.getQuery();
            
            if (query == null || query.isEmpty()) {
                logger.warn("No query parameters in return URL");
                return null;
            }
            
            // Parse query parameters
            Map<String, String> params = parseQueryString(query);
            logger.debug("Parsed {} parameters from return URL", params.size());
            
            // Try all extraction methods and return the first successful one
            Optional<String> steamId = extractSteamIdFromParams(params);
            if (steamId.isPresent()) {
                logger.info("Successfully extracted Steam ID: {}", steamId.get());
                return steamId.get();
            }
            
            // Last resort: Verify with Steam servers
            if (verifyWithSteam(params)) {
                logger.info("Verified authentication with Steam servers");
                
                // Try one more time to extract from verified parameters
                steamId = extractSteamIdFromParams(params);
                if (steamId.isPresent()) {
                    logger.info("Successfully extracted Steam ID after verification: {}", steamId.get());
                    return steamId.get();
                }
            }
            
            logger.warn("Failed to extract Steam ID from authentication response");
            return null;
        } catch (Exception e) {
            logger.error("Error processing authentication return URL: {}", returnUrl, e);
            return null;
        }
    }
    
    /**
     * Try multiple strategies to extract Steam ID from OpenID parameters.
     * 
     * @param params The parsed OpenID parameters
     * @return Optional containing the Steam ID if extraction was successful
     */
    private Optional<String> extractSteamIdFromParams(Map<String, String> params) {
        // Strategy 1: Claimed ID with regex matching
        String claimedId = params.get("openid.claimed_id");
        if (claimedId != null && !claimedId.isEmpty()) {
            logger.debug("Trying to extract from claimed_id: {}", claimedId);
            
            // Try with each pattern
            for (Pattern pattern : new Pattern[] {STEAM_OPENID_PATTERN, STEAM_ID_PATTERN, RAW_ID_PATTERN}) {
                Matcher matcher = pattern.matcher(claimedId);
                if (matcher.find()) {
                    String steamId = matcher.group(1);
                    logger.debug("Extracted Steam ID {} using pattern {}", steamId, pattern.pattern());
                    return Optional.of(steamId);
                }
            }
            
            // Try URL decoding first in case it's encoded
            try {
                String decodedClaimedId = URLDecoder.decode(claimedId, StandardCharsets.UTF_8.name());
                for (Pattern pattern : new Pattern[] {STEAM_OPENID_PATTERN, STEAM_ID_PATTERN, RAW_ID_PATTERN}) {
                    Matcher matcher = pattern.matcher(decodedClaimedId);
                    if (matcher.find()) {
                        String steamId = matcher.group(1);
                        logger.debug("Extracted Steam ID {} using pattern {} after URL decoding", steamId, pattern.pattern());
                        return Optional.of(steamId);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error decoding claimed_id", e);
            }
            
            // Try simple path extraction
            String[] parts = claimedId.split("/");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                if (lastPart.matches("\\d+")) {
                    logger.debug("Extracted Steam ID {} using path splitting from claimed_id", lastPart);
                    return Optional.of(lastPart);
                }
            }
        }
        
        // Strategy 2: Identity with regex matching
        String identity = params.get("openid.identity");
        if (identity != null && !identity.isEmpty()) {
            logger.debug("Trying to extract from identity: {}", identity);
            
            // Try with each pattern
            for (Pattern pattern : new Pattern[] {STEAM_OPENID_PATTERN, STEAM_ID_PATTERN, RAW_ID_PATTERN}) {
                Matcher matcher = pattern.matcher(identity);
                if (matcher.find()) {
                    String steamId = matcher.group(1);
                    logger.debug("Extracted Steam ID {} using pattern {}", steamId, pattern.pattern());
                    return Optional.of(steamId);
                }
            }
            
            // Try URL decoding first in case it's encoded
            try {
                String decodedIdentity = URLDecoder.decode(identity, StandardCharsets.UTF_8.name());
                for (Pattern pattern : new Pattern[] {STEAM_OPENID_PATTERN, STEAM_ID_PATTERN, RAW_ID_PATTERN}) {
                    Matcher matcher = pattern.matcher(decodedIdentity);
                    if (matcher.find()) {
                        String steamId = matcher.group(1);
                        logger.debug("Extracted Steam ID {} using pattern {} after URL decoding", steamId, pattern.pattern());
                        return Optional.of(steamId);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error decoding identity", e);
            }
            
            // Try simple path extraction
            String[] parts = identity.split("/");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                if (lastPart.matches("\\d+")) {
                    logger.debug("Extracted Steam ID {} using path splitting from identity", lastPart);
                    return Optional.of(lastPart);
                }
            }
        }
        
        // Strategy 3: Direct parameter from response_nonce (sometimes contains the Steam ID)
        String responseNonce = params.get("openid.response_nonce");
        if (responseNonce != null && !responseNonce.isEmpty() && responseNonce.contains("-")) {
            String[] nonceParts = responseNonce.split("-");
            if (nonceParts.length > 1 && nonceParts[1].matches("\\d+")) {
                logger.debug("Extracted Steam ID {} from response_nonce", nonceParts[1]);
                return Optional.of(nonceParts[1]);
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Verifies the authentication response with Steam's servers.
     */
    private boolean verifyWithSteam(Map<String, String> params) {
        try {
            // Convert the parameters to verification mode
            Map<String, String> verifyParams = new HashMap<>(params);
            verifyParams.put("openid.mode", "check_authentication");
            
            // Build the verification request URL
            StringBuilder urlBuilder = new StringBuilder(STEAM_LOGIN_URL + "?");
            verifyParams.forEach((key, value) -> {
                if (urlBuilder.charAt(urlBuilder.length() - 1) != '?') {
                    urlBuilder.append("&");
                }
                urlBuilder.append(key).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            });
            
            // Make the verification request
            Request request = new Request.Builder()
                    .url(urlBuilder.toString())
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("Steam verification failed with status: {}", response.code());
                    return false;
                }
                
                String responseBody = response.body().string();
                return responseBody.contains("is_valid:true");
            }
        } catch (IOException e) {
            logger.error("Failed to verify with Steam", e);
            return false;
        }
    }
    
    /**
     * Parse a query string into a map of parameters.
     * This is an enhanced version that handles special cases in Steam's OpenID implementation.
     */
    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        
        try {
            // Method 1: Standard query parsing
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = pair.substring(0, idx);
                    String value = idx + 1 < pair.length() ? pair.substring(idx + 1) : "";
                    
                    // URL decode the value
                    try {
                        value = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                    } catch (Exception e) {
                        logger.warn("Failed to URL decode value: {}", value, e);
                    }
                    
                    params.put(key, value);
                } else if (pair.endsWith("=")) {
                    // Handle key with empty value
                    String key = pair.substring(0, pair.length() - 1);
                    params.put(key, "");
                }
            }
            
            // Method 2: Try parsing as a query fragment (sometimes Steam returns params in the fragment)
            if (query.contains("#")) {
                String fragment = query.substring(query.indexOf('#') + 1);
                String[] fragmentPairs = fragment.split("&");
                for (String pair : fragmentPairs) {
                    int idx = pair.indexOf("=");
                    if (idx > 0) {
                        String key = pair.substring(0, idx);
                        String value = idx + 1 < pair.length() ? pair.substring(idx + 1) : "";
                        
                        // Only add if not already present
                        if (!params.containsKey(key)) {
                            try {
                                value = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                            } catch (Exception e) {
                                logger.warn("Failed to URL decode value from fragment: {}", value, e);
                            }
                            
                            params.put(key, value);
                        }
                    }
                }
            }
            
            // Special handling for OpenID parameters which might be combined
            // For example: openid.ns:http://specs.openid.net/auth/2.0
            if (params.isEmpty() || params.size() < 2) {
                String[] manualPairs = query.replaceAll("[?&#]", "&").split("&");
                for (String pair : manualPairs) {
                    if (pair.contains(":")) {
                        String[] keyValue = pair.split(":", 2);
                        if (keyValue.length == 2 && keyValue[0].startsWith("openid.")) {
                            params.put(keyValue[0], keyValue[1]);
                        }
                    }
                }
            }
            
            // If we still don't have the expected parameters, try an aggressive approach
            if (!params.containsKey("openid.claimed_id") && !params.containsKey("openid.identity")) {
                for (String key : new String[]{"openid.claimed_id", "openid.identity"}) {
                    int keyIdx = query.indexOf(key + "=");
                    if (keyIdx >= 0) {
                        int valueStart = keyIdx + key.length() + 1;
                        int valueEnd = query.indexOf("&", valueStart);
                        if (valueEnd < 0) valueEnd = query.length();
                        
                        String value = query.substring(valueStart, valueEnd);
                        try {
                            value = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                        } catch (Exception e) {
                            logger.warn("Failed to decode value in aggressive parsing: {}", value, e);
                        }
                        
                        params.put(key, value);
                    }
                }
            }
            
            logger.debug("Parsed query parameters: {}", params);
        } catch (Exception e) {
            logger.error("Error parsing query string: {}", query, e);
            // Despite errors, return whatever we've collected so far
        }
        
        return params;
    }
}