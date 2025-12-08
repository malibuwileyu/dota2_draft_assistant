package com.dota2assistant.infrastructure.api;

import com.fasterxml.jackson.annotation.JsonProperty;
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

/**
 * Client for DotaBASED Supabase API.
 * Provides hero synergy and counter data.
 */
@Component
public class DotaBasedClient {
    
    private static final Logger log = LoggerFactory.getLogger(DotaBasedClient.class);
    private static final String SUPABASE_URL = "https://pwghsmyluoiknbmivwiw.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB3Z2hzbXlsdW9pa25ibWl2d2l3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2NzYzMzE1NzcsImV4cCI6MTk5MTkwNzU3N30.l5UBvkvVM4_dg-g7p_c6Ed2JmYhDoC1v78i9o0BZF5s";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public DotaBasedClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    }
    
    /**
     * Fetch all hero synergy/counter data from DotaBASED.
     * Returns ~7,875 hero pair records with synergy and counter stats.
     */
    public List<HeroPairStats> getHeroSynergyData() throws IOException, InterruptedException {
        String url = SUPABASE_URL + "/rest/v1/rpc/api_herosynergy";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(TIMEOUT)
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer " + SUPABASE_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();
        
        log.info("Fetching synergy data from DotaBASED...");
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("DotaBASED API error: " + response.statusCode());
        }
        
        List<HeroPairStats> data = objectMapper.readValue(
            response.body(), 
            new TypeReference<>() {}
        );
        
        log.info("Received {} hero pair records", data.size());
        return data;
    }
    
    /**
     * Hero pair statistics from DotaBASED.
     */
    public record HeroPairStats(
        int id,
        @JsonProperty("hero_a") String heroA,
        @JsonProperty("hero_b") String heroB,
        
        // Synergy stats (same team)
        int picks,
        int wins,
        @JsonProperty("avg_winrate") double avgWinrate,
        @JsonProperty("avg_mmr") double avgMmr,
        
        // Counter stats (opposing teams)
        @JsonProperty("counter_picks") int counterPicks,
        @JsonProperty("counter_heroawins") int counterHeroAWins,
        @JsonProperty("counter_herobwins") int counterHeroBWins,
        @JsonProperty("heroa_advantage") double heroAAdvantage
    ) {
        /**
         * Synergy score: win rate when both heroes on same team.
         * 0.5 = neutral, >0.5 = good synergy, <0.5 = bad synergy.
         */
        public double synergyScore() {
            return avgWinrate;
        }
        
        /**
         * Counter score for hero A vs hero B.
         * >0.5 = A counters B, <0.5 = B counters A.
         */
        public double counterScoreAvsB() {
            if (counterPicks == 0) return 0.5;
            return (double) counterHeroAWins / counterPicks;
        }
    }
}

