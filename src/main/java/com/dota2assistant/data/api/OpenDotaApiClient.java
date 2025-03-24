package com.dota2assistant.data.api;

import com.dota2assistant.data.model.Ability;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.HeroAttributes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenDotaApiClient implements DotaApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenDotaApiClient.class);
    
    private static final String BASE_URL = "https://api.opendota.com/api";
    private static final String HEROES_ENDPOINT = "/heroes";
    private static final String HERO_STATS_ENDPOINT = "/heroStats";
    private static final String MATCHES_ENDPOINT = "/matches";
    private static final String PRO_MATCHES_ENDPOINT = "/proMatches";
    private static final String PUBLIC_MATCHES_ENDPOINT = "/publicMatches";
    private static final String HERO_MATCHUPS_ENDPOINT = "/heroes/{hero_id}/matchups";
    
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    
    public OpenDotaApiClient(OkHttpClient client, ObjectMapper mapper) {
        this(client, mapper, null);
    }
    
    public OpenDotaApiClient(OkHttpClient client, ObjectMapper mapper, String apiKey) {
        this.client = client;
        this.mapper = mapper;
        this.apiKey = apiKey;
        
        if (apiKey != null && !apiKey.isEmpty()) {
            logger.info("OpenDota API client initialized with API key");
        } else {
            logger.warn("OpenDota API client initialized without API key - rate limiting may occur");
        }
    }
    
    /**
     * Helper method to add API key to a URL if available
     * 
     * @param baseUrl The base URL to add the API key to
     * @return HttpUrl.Builder with API key added if available
     */
    private HttpUrl.Builder createUrlWithApiKey(String baseUrl) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        
        // Add API key if available
        if (apiKey != null && !apiKey.isEmpty()) {
            urlBuilder.addQueryParameter("api_key", apiKey);
        }
        
        return urlBuilder;
    }
    
    @Override
    public List<Hero> fetchHeroes() throws IOException {
        HttpUrl.Builder urlBuilder = createUrlWithApiKey(BASE_URL + HERO_STATS_ENDPOINT);
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch heroes: " + response.code());
            }
            
            String responseBody = response.body().string();
            ArrayNode heroNodes = (ArrayNode) mapper.readTree(responseBody);
            List<Hero> heroes = new ArrayList<>();
            
            for (JsonNode node : heroNodes) {
                Hero hero = parseHeroFromNode(node);
                heroes.add(hero);
            }
            
            logger.info("Fetched {} heroes from OpenDota API", heroes.size());
            return heroes;
        }
    }
    
    @Override
    public Hero fetchHeroDetails(int heroId) throws IOException {
        HttpUrl.Builder urlBuilder = createUrlWithApiKey(BASE_URL + HEROES_ENDPOINT + "/" + heroId);
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch hero: " + response.code());
            }
            
            String responseBody = response.body().string();
            JsonNode heroNode = mapper.readTree(responseBody);
            
            Hero hero = parseHeroFromNode(heroNode);
            fetchAndSetHeroAbilities(hero);
            
            logger.info("Fetched hero details for {}", hero.getLocalizedName());
            return hero;
        }
    }
    
    /**
     * Validates if a match ID is in the correct format
     * 
     * @param matchId The match ID to validate
     * @return true if the match ID appears valid
     */
    public boolean isValidMatchId(long matchId) {
        // Dota 2 match IDs are typically 10 digits
        String matchIdStr = String.valueOf(matchId);
        return matchIdStr.length() == 10;
    }

    @Override
    public Map<String, Object> fetchMatch(long matchId) throws IOException {
        // Validate match ID format first
        if (!isValidMatchId(matchId)) {
            logger.warn("Invalid match ID format: {} (expected 10 digits)", matchId);
            throw new DotaApiException("Invalid match ID format", 400, matchId);
        }
        
        HttpUrl.Builder urlBuilder = createUrlWithApiKey(BASE_URL + MATCHES_ENDPOINT + "/" + matchId);
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                int statusCode = response.code();
                
                if (statusCode == 404) {
                    logger.warn("Match {} not found - may be a private match or invalid ID", matchId);
                } else if (statusCode == 429) {
                    logger.warn("Rate limit hit when fetching match {}", matchId);
                } else if (statusCode >= 500) {
                    logger.error("OpenDota server error when fetching match {}: {}", matchId, statusCode);
                } else {
                    logger.error("API error fetching match {}: status code {}", matchId, statusCode);
                }
                
                throw new DotaApiException("Failed to fetch match", statusCode, matchId);
            }
            
            String responseBody = response.body().string();
            
            // Check if the response is too small or empty, indicating a potential issue
            if (responseBody == null || responseBody.trim().length() < 10) {
                logger.warn("Empty or invalid response for match {}: '{}'", matchId, responseBody);
                throw new DotaApiException("Empty or invalid response for match", 422, matchId);
            }
            
            try {
                Map<String, Object> result = mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
                logger.debug("Successfully fetched match {}", matchId);
                return result;
            } catch (Exception e) {
                logger.error("Error parsing match data JSON for match {}: {}", matchId, e.getMessage());
                throw new DotaApiException("Failed to parse match data: " + e.getMessage(), 422, matchId);
            }
        }
    }
    
    @Override
    public List<Map<String, Object>> fetchProMatches(int limit) throws IOException {
        HttpUrl.Builder urlBuilder = createUrlWithApiKey(BASE_URL + PRO_MATCHES_ENDPOINT);
        urlBuilder.addQueryParameter("limit", String.valueOf(limit));
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch pro matches: " + response.code());
            }
            
            String responseBody = response.body().string();
            return mapper.readValue(responseBody, new TypeReference<List<Map<String, Object>>>() {});
        }
    }
    
    @Override
    public List<Map<String, Object>> fetchMatchesByRank(String rank, int limit) throws IOException {
        // OpenDota API doesn't directly support filtering by rank
        // This is a simplified implementation
        HttpUrl.Builder urlBuilder = createUrlWithApiKey(BASE_URL + PUBLIC_MATCHES_ENDPOINT);
        urlBuilder.addQueryParameter("mmr_descending", "true");
        urlBuilder.addQueryParameter("limit", String.valueOf(limit));
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch matches by rank: " + response.code());
            }
            
            String responseBody = response.body().string();
            return mapper.readValue(responseBody, new TypeReference<List<Map<String, Object>>>() {});
        }
    }
    
    @Override
    public Map<Integer, Double> fetchHeroWinRates() throws IOException {
        HttpUrl.Builder urlBuilder = createUrlWithApiKey(BASE_URL + HERO_STATS_ENDPOINT);
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch hero win rates: " + response.code());
            }
            
            String responseBody = response.body().string();
            ArrayNode heroNodes = (ArrayNode) mapper.readTree(responseBody);
            Map<Integer, Double> winRates = new HashMap<>();
            
            for (JsonNode node : heroNodes) {
                int id = node.get("id").asInt();
                // Win rate is calculated from games won / games played
                int wins = node.has("pro_win") ? node.get("pro_win").asInt() : 0;
                int games = node.has("pro_pick") ? node.get("pro_pick").asInt() : 0;
                double winRate = games > 0 ? (double) wins / games : 0;
                winRates.put(id, winRate);
            }
            
            return winRates;
        }
    }
    
    @Override
    public Map<Integer, Double> fetchHeroPickRates() throws IOException {
        HttpUrl.Builder urlBuilder = createUrlWithApiKey(BASE_URL + HERO_STATS_ENDPOINT);
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch hero pick rates: " + response.code());
            }
            
            String responseBody = response.body().string();
            ArrayNode heroNodes = (ArrayNode) mapper.readTree(responseBody);
            Map<Integer, Double> pickRates = new HashMap<>();
            
            // First calculate total pick count
            int totalPicks = 0;
            for (JsonNode node : heroNodes) {
                int picks = node.has("pro_pick") ? node.get("pro_pick").asInt() : 0;
                totalPicks += picks;
            }
            
            // Then calculate pick rates
            for (JsonNode node : heroNodes) {
                int id = node.get("id").asInt();
                int picks = node.has("pro_pick") ? node.get("pro_pick").asInt() : 0;
                double pickRate = totalPicks > 0 ? (double) picks / totalPicks : 0;
                pickRates.put(id, pickRate);
            }
            
            return pickRates;
        }
    }
    
    @Override
    public Map<String, Double> fetchHeroSynergies() throws IOException {
        // This is a simplified implementation
        // In a real implementation, you would need to analyze match data to calculate synergies
        Map<String, Double> synergies = new HashMap<>();
        
        List<Hero> heroes = fetchHeroes();
        for (Hero hero1 : heroes) {
            for (Hero hero2 : heroes) {
                if (hero1.getId() != hero2.getId()) {
                    // Generate some random synergy scores for demo purposes
                    double synergyScore = Math.random() * 0.5 + 0.25; // Random score between 0.25 and 0.75
                    synergies.put(hero1.getId() + "_" + hero2.getId(), synergyScore);
                }
            }
        }
        
        return synergies;
    }
    
    @Override
    public Map<String, Double> fetchHeroCounters() throws IOException {
        Map<String, Double> counters = new HashMap<>();
        
        List<Hero> heroes = fetchHeroes();
        // Fetch counter data for each hero
        for (Hero hero : heroes) {
            HttpUrl.Builder urlBuilder = createUrlWithApiKey(BASE_URL + HERO_MATCHUPS_ENDPOINT.replace("{hero_id}", String.valueOf(hero.getId())));
            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warn("Failed to fetch counters for hero {}: {}", hero.getId(), response.code());
                    continue;
                }
                
                String responseBody = response.body().string();
                ArrayNode matchupNodes = (ArrayNode) mapper.readTree(responseBody);
                
                for (JsonNode node : matchupNodes) {
                    int versusHeroId = node.get("hero_id").asInt();
                    int wins = node.get("wins").asInt();
                    int games = node.get("games_played").asInt();
                    double winRate = games > 0 ? (double) wins / games : 0;
                    
                    // A lower win rate against another hero means that hero counters this one
                    double counterScore = 1.0 - winRate;
                    counters.put(hero.getId() + "_" + versusHeroId, counterScore);
                }
            }
        }
        
        return counters;
    }
    
    private Hero parseHeroFromNode(JsonNode node) {
        int id = node.get("id").asInt();
        String name = node.get("name").asText();
        String localizedName = node.get("localized_name").asText();
        
        Hero hero = new Hero(id, name, localizedName);
        
        if (node.has("primary_attr")) {
            hero.setPrimaryAttribute(node.get("primary_attr").asText());
        }
        
        if (node.has("attack_type")) {
            hero.setAttackType(node.get("attack_type").asText());
        }
        
        if (node.has("img") || node.has("image")) {
            String imageUrl = node.has("img") ? node.get("img").asText() : node.get("image").asText();
            if (!imageUrl.startsWith("http")) {
                imageUrl = "https://api.opendota.com" + imageUrl;
            }
            hero.setImageUrl(imageUrl);
        }
        
        if (node.has("roles") && node.get("roles").isArray()) {
            ArrayNode rolesNode = (ArrayNode) node.get("roles");
            for (JsonNode roleNode : rolesNode) {
                hero.addRole(roleNode.asText());
            }
        }
        
        // Create hero attributes if available
        if (node.has("base_str") || node.has("base_agi") || node.has("base_int")) {
            HeroAttributes attributes = new HeroAttributes();
            
            if (node.has("base_str")) {
                attributes.setBaseStrength(node.get("base_str").asDouble());
            }
            if (node.has("base_agi")) {
                attributes.setBaseAgility(node.get("base_agi").asDouble());
            }
            if (node.has("base_int")) {
                attributes.setBaseIntelligence(node.get("base_int").asDouble());
            }
            if (node.has("str_gain")) {
                attributes.setStrengthGain(node.get("str_gain").asDouble());
            }
            if (node.has("agi_gain")) {
                attributes.setAgilityGain(node.get("agi_gain").asDouble());
            }
            if (node.has("int_gain")) {
                attributes.setIntelligenceGain(node.get("int_gain").asDouble());
            }
            if (node.has("move_speed")) {
                attributes.setMoveSpeed(node.get("move_speed").asInt());
            }
            if (node.has("base_armor")) {
                attributes.setArmor(node.get("base_armor").asDouble());
            }
            if (node.has("base_attack_min")) {
                attributes.setAttackDamageMin(node.get("base_attack_min").asDouble());
            }
            if (node.has("base_attack_max")) {
                attributes.setAttackDamageMax(node.get("base_attack_max").asDouble());
            }
            if (node.has("attack_rate")) {
                attributes.setAttackRate(node.get("attack_rate").asDouble());
            }
            if (node.has("base_attack_range")) {
                attributes.setAttackRange(node.get("base_attack_range").asInt());
            }
            
            hero.setAttributes(attributes);
        }
        
        return hero;
    }
    
    private void fetchAndSetHeroAbilities(Hero hero) throws IOException {
        // In a real implementation, you would fetch abilities from the API
        // This is a simplified implementation
        Ability ability1 = new Ability(1, "Example Ability 1", "This is an example ability");
        ability1.setType("active");
        ability1.setBehavior("point target");
        ability1.setDamageType("magical");
        ability1.setAffects(new String[]{"enemies"});
        ability1.setCooldown(new double[]{10, 8, 6, 4});
        ability1.setManaCost(new double[]{100, 120, 140, 160});
        hero.addAbility(ability1);
        
        Ability ability2 = new Ability(2, "Example Ability 2", "This is another example ability");
        ability2.setType("passive");
        hero.addAbility(ability2);
    }
}