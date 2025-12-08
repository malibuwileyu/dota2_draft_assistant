package com.dota2assistant.infrastructure.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client for Dota 2 Draft Assistant backend API.
 * Handles recommendation requests to the Railway-deployed backend.
 */
@Component
public class BackendApiClient {
    
    private static final Logger log = LoggerFactory.getLogger(BackendApiClient.class);
    private static final String BASE_URL = "https://d2draftassistantbackend-production.up.railway.app";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public BackendApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    }
    
    /**
     * Get hero recommendations based on current draft state.
     * @param allyPicks List of ally hero IDs already picked
     * @param enemyPicks List of enemy hero IDs already picked
     * @param bannedHeroes List of banned hero IDs
     * @param draftPhase Current draft phase (e.g., "pick", "ban")
     * @param includeExplanations Whether to include LLM explanations (slower)
     * @return Recommendation response with scored heroes
     */
    public CompletableFuture<RecommendationResponse> getRecommendations(
            List<Integer> allyPicks,
            List<Integer> enemyPicks,
            List<Integer> bannedHeroes,
            String draftPhase,
            boolean includeExplanations) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                RecommendRequest request = new RecommendRequest(
                    allyPicks, enemyPicks, bannedHeroes, draftPhase, null, includeExplanations
                );
                
                String requestBody = objectMapper.writeValueAsString(request);
                
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/v1/recommend"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
                
                log.debug("Requesting recommendations...");
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 200) {
                    log.error("Backend API error: {} - {}", response.statusCode(), response.body());
                    return RecommendationResponse.empty();
                }
                
                return objectMapper.readValue(response.body(), RecommendationResponse.class);
                
            } catch (IOException | InterruptedException e) {
                log.error("Failed to get recommendations: {}", e.getMessage());
                return RecommendationResponse.empty();
            }
        });
    }
    
    // Request/Response DTOs
    
    public record RecommendRequest(
        List<Integer> allyPicks,
        List<Integer> enemyPicks,
        List<Integer> bannedHeroes,
        String draftPhase,
        UserPreferences userPreferences,
        boolean includeExplanations
    ) {}
    
    public record UserPreferences(
        List<Integer> favoriteHeroes,
        List<String> preferredRoles,
        Integer mmr
    ) {}
    
    public record RecommendationResponse(
        List<HeroRecommendation> recommendations,
        double winProbability,
        long computeTimeMs
    ) {
        public static RecommendationResponse empty() {
            return new RecommendationResponse(List.of(), 0.5, 0);
        }
    }
    
    public record HeroRecommendation(
        int heroId,
        String heroName,
        double totalScore,
        List<ScoreBreakdown> breakdown,
        String iconUrl,
        String explanation
    ) {}
    
    public record ScoreBreakdown(
        String type,
        double value,
        String description
    ) {}
}

