package com.dota2assistant.data.api;

import com.dota2assistant.data.model.Hero;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface DotaApiClient {
    
    /**
     * Fetches all heroes from the API.
     * 
     * @return List of heroes
     * @throws IOException if an error occurs during the API call
     */
    List<Hero> fetchHeroes() throws IOException;
    
    /**
     * Fetches hero details for a specific hero.
     * 
     * @param heroId The hero ID
     * @return The hero details
     * @throws IOException if an error occurs during the API call
     */
    Hero fetchHeroDetails(int heroId) throws IOException;
    
    /**
     * Fetches match data for a specific match.
     * 
     * @param matchId The match ID
     * @return The match data as a Map
     * @throws IOException if an error occurs during the API call
     */
    Map<String, Object> fetchMatch(long matchId) throws IOException;
    
    /**
     * Fetches recent professional matches.
     * 
     * @param limit The maximum number of matches to fetch
     * @return List of match data
     * @throws IOException if an error occurs during the API call
     */
    List<Map<String, Object>> fetchProMatches(int limit) throws IOException;
    
    /**
     * Fetches match statistics filtered by rank.
     * 
     * @param rank The rank to filter by
     * @param limit The maximum number of matches to fetch
     * @return List of match data
     * @throws IOException if an error occurs during the API call
     */
    List<Map<String, Object>> fetchMatchesByRank(String rank, int limit) throws IOException;
    
    /**
     * Fetches hero win rates.
     * 
     * @return Map of hero IDs to win rates
     * @throws IOException if an error occurs during the API call
     */
    Map<Integer, Double> fetchHeroWinRates() throws IOException;
    
    /**
     * Fetches hero pick rates.
     * 
     * @return Map of hero IDs to pick rates
     * @throws IOException if an error occurs during the API call
     */
    Map<Integer, Double> fetchHeroPickRates() throws IOException;
    
    /**
     * Fetches hero synergy data.
     * 
     * @return Map of hero ID pairs to synergy scores
     * @throws IOException if an error occurs during the API call
     */
    Map<String, Double> fetchHeroSynergies() throws IOException;
    
    /**
     * Fetches hero counter data.
     * 
     * @return Map of hero ID pairs to counter scores
     * @throws IOException if an error occurs during the API call
     */
    Map<String, Double> fetchHeroCounters() throws IOException;
}