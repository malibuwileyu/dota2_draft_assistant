package com.dota2assistant.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AuthCallbackServer class.
 */
public class AuthCallbackServerTest {

    private AuthCallbackServer server;
    private CompletableFuture<Map<String, Object>> callbackFuture;

    @BeforeEach
    public void setUp() throws IOException {
        server = new AuthCallbackServer();
        callbackFuture = server.startAndWaitForCallback();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stopServer();
        }
    }

    @Test
    public void testServerStart() {
        assertNotNull(callbackFuture);
        assertFalse(callbackFuture.isDone());
    }

    @Test
    public void testCallbackHandling() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        // Setup test parameters
        String testParam = "testValue";
        String steamId = "76561198012345678";
        String claimedId = "https://steamcommunity.com/openid/id/" + steamId;
        
        // Build a URL to simulate a callback
        String callbackUrl = "http://localhost:8080/login/oauth2/code/steam?openid.claimed_id=" + claimedId + "&test=" + testParam;
        HttpURLConnection connection = null;
        
        try {
            // Send a GET request to the server
            URL url = new URL(callbackUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "JUnit Test");
            
            // Read the response
            int responseCode = connection.getResponseCode();
            assertEquals(200, responseCode, "Response code should be 200 OK");
            
            // Check the content type
            String contentType = connection.getContentType();
            assertTrue(contentType.contains("text/html"), "Content type should be HTML");
            
            // Read the response body
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder responseBody = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    responseBody.append(line);
                }
                
                // Verify the response contains success message
                String response = responseBody.toString();
                assertTrue(response.contains("Authentication Successful"), "Response should contain success message");
            }
            
            // Wait for the callback future to complete
            Map<String, Object> result = callbackFuture.get(5, TimeUnit.SECONDS);
            
            // Verify callback data
            assertNotNull(result, "Callback result should not be null");
            
            // Verify URL
            String resultUrl = (String) result.get("url");
            assertNotNull(resultUrl, "Result URL should not be null");
            assertTrue(resultUrl.contains(testParam), "Result URL should contain test parameter");
            
            // Verify client info
            AuthCallbackServer.ClientInfo clientInfo = (AuthCallbackServer.ClientInfo) result.get("clientInfo");
            assertNotNull(clientInfo, "Client info should not be null");
            assertEquals("JUnit Test", clientInfo.getUserAgent(), "User agent should match");
            assertNotNull(clientInfo.getIpAddress(), "IP address should not be null");
            assertNotNull(clientInfo.getTimestamp(), "Timestamp should not be null");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}