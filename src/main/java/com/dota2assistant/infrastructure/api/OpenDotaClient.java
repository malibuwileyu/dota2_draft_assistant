package com.dota2assistant.infrastructure.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client for OpenDota API.
 * Rate limit: 60 requests/minute without API key.
 */
@Component
public class OpenDotaClient {
    
    private static final Logger log = LoggerFactory.getLogger(OpenDotaClient.class);
    private static final String BASE_URL = "https://api.opendota.com/api";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final long RATE_LIMIT_DELAY_MS = 1100; // ~55 req/min to be safe
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private long lastRequestTime = 0;
    
    public OpenDotaClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    }
    
    /**
     * Fetch player's MMR from OpenDota.
     * @param accountId The 32-bit Steam account ID
     * @return The player's solo MMR estimate, or empty if not available
     */
    public CompletableFuture<Optional<Integer>> fetchPlayerMmr(long accountId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = BASE_URL + "/players/" + accountId;
                String json = makeRequest(url);
                
                JsonNode root = objectMapper.readTree(json);
                JsonNode mmrEstimate = root.path("mmr_estimate").path("estimate");
                
                if (!mmrEstimate.isMissingNode() && !mmrEstimate.isNull()) {
                    return Optional.of(mmrEstimate.asInt());
                }
                
                // Try competitive_rank as fallback
                JsonNode rank = root.path("competitive_rank");
                if (!rank.isMissingNode() && !rank.isNull()) {
                    return Optional.of(rank.asInt());
                }
                
                return Optional.empty();
                
            } catch (Exception e) {
                log.warn("Failed to fetch MMR for account {}: {}", accountId, e.getMessage());
                return Optional.empty();
            }
        });
    }
    
    /**
     * Fetch player's most played heroes.
     * @param accountId The 32-bit Steam account ID
     * @param limit Maximum number of heroes to return
     * @return List of hero IDs sorted by games played (descending)
     */
    public CompletableFuture<List<Integer>> fetchMostPlayedHeroes(long accountId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = BASE_URL + "/players/" + accountId + "/heroes";
                String json = makeRequest(url);
                
                List<PlayerHeroStats> stats = objectMapper.readValue(json, new TypeReference<>() {});
                
                return stats.stream()
                    .sorted((a, b) -> Integer.compare(b.games(), a.games()))
                    .limit(limit)
                    .map(PlayerHeroStats::heroId)
                    .toList();
                
            } catch (Exception e) {
                log.warn("Failed to fetch heroes for account {}: {}", accountId, e.getMessage());
                return List.of();
            }
        });
    }
    
    /**
     * Get matchup data for a hero (win rates against all other heroes).
     * @param heroId The hero ID
     * @return List of matchup records
     */
    public List<HeroMatchup> getHeroMatchups(int heroId) throws IOException, InterruptedException {
        String url = BASE_URL + "/heroes/" + heroId + "/matchups";
        String json = makeRequest(url);
        return objectMapper.readValue(json, new TypeReference<>() {});
    }
    
    /**
     * Get hero stats (win rates by rank bracket).
     */
    public List<HeroStats> getHeroStats() throws IOException, InterruptedException {
        String url = BASE_URL + "/heroStats";
        String json = makeRequest(url);
        return objectMapper.readValue(json, new TypeReference<>() {});
    }
    
    private String makeRequest(String url) throws IOException, InterruptedException {
        enforceRateLimit();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
        
        log.debug("Requesting: {}", url);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("OpenDota API error: " + response.statusCode() + " - " + response.body());
        }
        
        return response.body();
    }
    
    private synchronized void enforceRateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < RATE_LIMIT_DELAY_MS) {
            Thread.sleep(RATE_LIMIT_DELAY_MS - elapsed);
        }
        lastRequestTime = System.currentTimeMillis();
    }
    
    /**
     * Matchup data from /heroes/{id}/matchups endpoint.
     */
    public record HeroMatchup(
        int hero_id,
        int games_played,
        int wins
    ) {
        public double winRate() {
            return games_played > 0 ? (double) wins / games_played : 0.5;
        }
    }
    
    /**
     * Hero stats from /heroStats endpoint.
     */
    public record HeroStats(
        int id,
        String localized_name,
        int pro_pick,
        int pro_win,
        int pro_ban
    ) {}
    
    /**
     * Player's hero stats from /players/{id}/heroes endpoint.
     */
    public record PlayerHeroStats(
        @JsonProperty("hero_id") int heroId,
        int games,
        int win
    ) {
        public double winRate() {
            return games > 0 ? (double) win / games : 0.5;
        }
    }
}

