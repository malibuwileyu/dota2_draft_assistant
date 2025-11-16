package com.dota2assistant.auth;

import com.dota2assistant.util.PropertyLoader;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for SteamAuthenticationManager class.
 */
public class SteamAuthenticationManagerTest {

    @Mock
    private OkHttpClient httpClient;
    
    @Mock
    private PropertyLoader propertyLoader;
    
    private SteamAuthenticationManager authManager;
    
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup property loader mock
        when(propertyLoader.getProperty("steam.auth.return_url", "http://localhost:8080/login/oauth2/code/steam"))
            .thenReturn("http://localhost:8080/login/oauth2/code/steam");
            
        when(propertyLoader.getProperty("steam.auth.realm", "http://localhost:8080"))
            .thenReturn("http://localhost:8080");
        
        authManager = new SteamAuthenticationManager(httpClient, propertyLoader);
    }
    
    @Test
    public void testGetLoginUrl() {
        // Execute 
        String loginUrl = authManager.getLoginUrl();
        
        // Verify
        assertNotNull(loginUrl);
        assertTrue(loginUrl.startsWith("https://steamcommunity.com/openid/login?"));
        assertTrue(loginUrl.contains("openid.ns="));
        assertTrue(loginUrl.contains("openid.mode=checkid_setup"));
        assertTrue(loginUrl.contains("openid.return_to="));
        assertTrue(loginUrl.contains("openid.realm="));
    }
    
    @Test
    public void testIdExtractionFromClaimedId() {
        // Standard format
        String steamId = "76561198012345678";
        String claimedId = "https://steamcommunity.com/openid/id/" + steamId;
        String callbackUrl = buildCallbackUrl("openid.claimed_id", claimedId);
        
        assertEquals(steamId, authManager.validateAndGetSteamId(callbackUrl));
    }
    
    @Test
    public void testIdExtractionFromIdentity() {
        // Standard format for identity
        String steamId = "76561198012345678";
        String identity = "https://steamcommunity.com/openid/id/" + steamId;
        String callbackUrl = buildCallbackUrl("openid.identity", identity);
        
        assertEquals(steamId, authManager.validateAndGetSteamId(callbackUrl));
    }
    
    @Test
    public void testIdExtractionFromUrlEncodedValue() {
        // URL encoded format
        String steamId = "76561198012345678";
        String encodedClaimedId = URLEncoder.encode("https://steamcommunity.com/openid/id/" + steamId, 
                                                   StandardCharsets.UTF_8);
        String callbackUrl = "http://localhost:8080/login/oauth2/code/steam?openid.claimed_id=" + encodedClaimedId;
        
        assertEquals(steamId, authManager.validateAndGetSteamId(callbackUrl));
    }
    
    @Test
    public void testIdExtractionFromMalformedUrl() {
        // Malformed but still parseable
        String steamId = "76561198012345678";
        String callbackUrl = "http://localhost:8080/login/oauth2/code/steam?openid.claimed_id=steamcommunity.com/openid/id/" + steamId;
        
        assertEquals(steamId, authManager.validateAndGetSteamId(callbackUrl));
    }
    
    @Test
    public void testExtractionWithResponseNonce() {
        // Sometimes the nonce contains the ID
        String steamId = "76561198012345678";
        String callbackUrl = "http://localhost:8080/login/oauth2/code/steam?openid.response_nonce=2023-01-01T12:00:00Z-" + steamId;
        
        assertEquals(steamId, authManager.validateAndGetSteamId(callbackUrl));
    }
    
    /**
     * Helper method to build a test callback URL
     */
    private String buildCallbackUrl(String paramName, String paramValue) {
        return "http://localhost:8080/login/oauth2/code/steam?" + 
               paramName + "=" + paramValue;
    }
}