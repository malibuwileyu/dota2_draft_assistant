package com.dota2assistant.data.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.dota2assistant.data.api.DotaApiClient;
import com.dota2assistant.data.db.DatabaseManager;
import com.dota2assistant.util.PropertyLoader;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for the MatchEnrichmentService.
 */
public class MatchEnrichmentServiceTest {
    
    @Mock
    private DotaApiClient apiClient;
    
    @Mock
    private DatabaseManager databaseManager;
    
    @Mock
    private PropertyLoader propertyLoader;
    
    @Mock
    private Connection connection;
    
    @Mock
    private PreparedStatement statement;
    
    @Mock
    private ResultSet resultSet;
    
    private MatchEnrichmentService enrichmentService;
    
    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Configure mock behavior
        when(databaseManager.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        
        // Set up the test match
        Map<String, Object> testMatchDetails = new HashMap<>();
        testMatchDetails.put("match_id", 123456789L);
        testMatchDetails.put("start_time", 1619966400); // 2021-05-02 12:00:00 UTC
        testMatchDetails.put("duration", 2400); // 40 minutes
        testMatchDetails.put("radiant_win", true);
        testMatchDetails.put("game_mode", 2); // Captain's Mode
        testMatchDetails.put("lobby_type", 1); // Tournament
        testMatchDetails.put("region", 3); // EU West
        
        // Set up players
        List<Map<String, Object>> players = new ArrayList<>();
        Map<String, Object> player1 = new HashMap<>();
        player1.put("account_id", 76561198123456789L);
        player1.put("hero_id", 1);
        player1.put("player_slot", 0); // Radiant side
        player1.put("kills", 10);
        player1.put("deaths", 3);
        player1.put("assists", 15);
        players.add(player1);
        
        testMatchDetails.put("players", players);
        
        // Configure apiClient mock to return our test match
        when(apiClient.fetchMatch(anyLong())).thenReturn(testMatchDetails);
        
        // Create the service
        enrichmentService = new MatchEnrichmentService(apiClient, databaseManager, propertyLoader);
    }
    
    @Test
    public void testEnqueueMatchForEnrichment() throws Exception {
        // Mock checkMatchExists to return true (match exists)
        when(resultSet.next()).thenReturn(true);
        
        // Test that enqueueing works
        boolean result = enrichmentService.enqueueMatchForEnrichment(123456789L, true);
        
        // Should return true as the match should be added to queue
        assertTrue(result);
        
        // Check statistics reflect the queue state
        Map<String, Object> stats = enrichmentService.getStatistics();
        assertTrue((Integer)stats.get("queueSize") > 0);
        
        // Wait a bit to allow processing 
        Thread.sleep(1000);
        
        // Verify api client was called
        verify(apiClient, timeout(5000)).fetchMatch(123456789L);
    }
    
    @Test
    public void testEnqueueMultipleMatches() throws SQLException {
        // Setup multiple match IDs
        List<Long> matchIds = List.of(123456789L, 123456790L, 123456791L);
        
        // Mock checkMatchExists to return true (matches exist)
        when(resultSet.next()).thenReturn(true);
        
        // Test enqueueing multiple matches
        int enqueued = enrichmentService.enqueueMatchesForEnrichment(matchIds, false);
        
        // All matches should be enqueued
        assertEquals(matchIds.size(), enqueued);
        
        // Check statistics again
        Map<String, Object> stats = enrichmentService.getStatistics();
        assertTrue((Integer)stats.get("queueSize") >= matchIds.size());
    }
    
    @Test
    public void testStatistics() {
        // Check initial statistics
        Map<String, Object> stats = enrichmentService.getStatistics();
        
        // Verify required statistics fields are present
        assertTrue(stats.containsKey("queueSize"));
        assertTrue(stats.containsKey("queueCapacity"));
        assertTrue(stats.containsKey("currentlyProcessing"));
        assertTrue(stats.containsKey("processedTotal"));
        assertTrue(stats.containsKey("successfulTotal"));
        assertTrue(stats.containsKey("failedTotal"));
        assertTrue(stats.containsKey("apiRequestsLastMinute"));
        assertTrue(stats.containsKey("apiRateLimit"));
    }
    
    @Test
    public void testShutdown() {
        // Ensure no exceptions when shutting down
        enrichmentService.shutdown();
    }
}