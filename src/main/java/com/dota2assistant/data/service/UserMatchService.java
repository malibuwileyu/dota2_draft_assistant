package com.dota2assistant.data.service;

import com.dota2assistant.auth.SteamApiService;
import com.dota2assistant.auth.SteamUser;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.PlayerHeroStat;
import com.dota2assistant.data.model.PlayerMatch;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.data.repository.UserMatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for managing user match history and player statistics.
 */
@Service
public class UserMatchService {

    private static final Logger logger = LoggerFactory.getLogger(UserMatchService.class);
    
    private final UserMatchRepository userMatchRepository;
    private final HeroRepository heroRepository;
    private final SteamApiService steamApiService;
    private MatchHistoryService matchHistoryService;
    private AutomatedMatchSyncService automatedSyncService;
    
    public UserMatchService(
            UserMatchRepository userMatchRepository, 
            HeroRepository heroRepository,
            SteamApiService steamApiService) {
        this.userMatchRepository = userMatchRepository;
        this.heroRepository = heroRepository;
        this.steamApiService = steamApiService;
        // Match history service and automated sync service will be set separately
    }
    
    /**
     * Alternative constructor that accepts all services at once.
     */
    public UserMatchService(
            UserMatchRepository userMatchRepository, 
            HeroRepository heroRepository,
            SteamApiService steamApiService,
            MatchHistoryService matchHistoryService,
            AutomatedMatchSyncService automatedSyncService) {
        this.userMatchRepository = userMatchRepository;
        this.heroRepository = heroRepository;
        this.steamApiService = steamApiService;
        this.matchHistoryService = matchHistoryService;
        this.automatedSyncService = automatedSyncService;
    }
    
    /**
     * Start a synchronization of match history for a user.
     * This will fetch recent matches from the Steam API and save them to the database.
     * 
     * @param steamId The user's Steam ID
     * @return CompletableFuture that will complete when the sync is done
     */
    @Async
    public CompletableFuture<Integer> startMatchHistorySync(String steamId) {
        try {
            // Convert Steam ID to account ID
            long steam64Id = Long.parseLong(steamId);
            int accountId32 = (int) (steam64Id & 0xFFFFFFFFL);
            
            logger.info("Starting match history sync for user {}", steamId);
            
            // Mark sync as started
            boolean syncStarted = userMatchRepository.startMatchHistorySync(accountId32);
            if (!syncStarted) {
                logger.warn("Match history sync already in progress for account: {}", accountId32);
                return CompletableFuture.completedFuture(0);
            }
            
            // Fetch recent matches from API
            List<Long> recentMatchIds = steamApiService.getRecentMatches(steamId, 50);
            logger.info("Found {} recent matches for player {}", recentMatchIds.size(), steamId);
            
            if (recentMatchIds.isEmpty()) {
                // No matches found, mark sync as complete
                userMatchRepository.completeMatchHistorySync(accountId32, 0, (Long)null, false, "DAILY");
                return CompletableFuture.completedFuture(0);
            }
            
            // In a real implementation, we'd fetch match details from the API
            // Here we'll create mock match data with the real match IDs
            List<PlayerMatch> playerMatches = createMatchesFromIds(recentMatchIds);
            
            // Save the matches to the database
            int savedCount = userMatchRepository.savePlayerMatches(accountId32, playerMatches);
            
            // Mark sync as complete
            userMatchRepository.completeMatchHistorySync(
                accountId32,
                savedCount,
                recentMatchIds.get(0),  // The most recent match ID
                true,
                "DAILY"
            );
            
            // Add notification
            String message = String.format("Added %d new matches to your history", savedCount);
            userMatchRepository.addNotification(accountId32, "SYNC_COMPLETE", null, message);
            
            logger.info("Match history sync completed for user {}, saved {} matches", steamId, savedCount);
            return CompletableFuture.completedFuture(savedCount);
        } catch (Exception e) {
            logger.error("Error syncing match history for user {}", steamId, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Get a player's match history from the database.
     * 
     * @param steamId The user's Steam ID
     * @param page The page number (0-based)
     * @param pageSize The number of items per page
     * @param includeHidden Whether to include hidden matches
     * @return List of player matches
     */
    public List<PlayerMatch> getPlayerMatches(String steamId, int page, int pageSize, boolean includeHidden) {
        try {
            // Convert Steam ID to account ID
            long steam64Id = Long.parseLong(steamId);
            int accountId32 = (int) (steam64Id & 0xFFFFFFFFL);
            
            int offset = page * pageSize;
            return userMatchRepository.getPlayerMatches(accountId32, pageSize, offset, includeHidden);
        } catch (Exception e) {
            logger.error("Error getting player matches for user {}", steamId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Get a player's hero statistics from the database.
     * 
     * @param steamId The user's Steam ID
     * @param limit Maximum number of heroes to retrieve
     * @param sortBy Field to sort by (games, winRate, kdaRatio)
     * @return List of player hero statistics
     */
    public List<PlayerHeroStat> getPlayerHeroStats(String steamId, int limit, String sortBy) {
        try {
            // Convert Steam ID to account ID
            long steam64Id = Long.parseLong(steamId);
            int accountId32 = (int) (steam64Id & 0xFFFFFFFFL);
            
            return userMatchRepository.getPlayerHeroStats(accountId32, limit, sortBy);
        } catch (Exception e) {
            logger.error("Error getting player hero stats for user {}", steamId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Get the total number of matches for a player.
     * 
     * @param steamId The user's Steam ID
     * @param includeHidden Whether to include hidden matches
     * @return The total number of matches
     */
    public int getPlayerMatchCount(String steamId, boolean includeHidden) {
        try {
            // Convert Steam ID to account ID
            long steam64Id = Long.parseLong(steamId);
            int accountId32 = (int) (steam64Id & 0xFFFFFFFFL);
            
            return userMatchRepository.getPlayerMatchCount(accountId32, includeHidden);
        } catch (Exception e) {
            logger.error("Error getting player match count for user {}", steamId, e);
            return 0;
        }
    }
    
    /**
     * Create mock player matches from match IDs.
     * In a real implementation, this would fetch match details from the API.
     * 
     * @param matchIds The match IDs to create matches for
     * @return List of player matches
     */
    private List<PlayerMatch> createMatchesFromIds(List<Long> matchIds) {
        List<PlayerMatch> matches = new ArrayList<>();
        Random random = new Random();
        List<Hero> heroes = heroRepository.getAllHeroes();
        
        // Nothing to do if we have no heroes
        if (heroes.isEmpty()) {
            return matches;
        }
        
        // Create a distribution of heroes that's weighted toward favorites
        // Players tend to play a core set of heroes more frequently
        List<Hero> favoriteHeroes = new ArrayList<>();
        // Select 5-8 favorite heroes
        int favoriteCount = 5 + random.nextInt(4);
        Set<Integer> selectedIndices = new HashSet<>();
        
        // Select random favorite heroes
        while (favoriteHeroes.size() < favoriteCount && favoriteHeroes.size() < heroes.size()) {
            int index = random.nextInt(heroes.size());
            if (!selectedIndices.contains(index)) {
                selectedIndices.add(index);
                favoriteHeroes.add(heroes.get(index));
            }
        }
        
        // Current time to base our match timestamps on
        LocalDateTime now = LocalDateTime.now();
        
        // Generate matches from the actual match IDs
        for (int i = 0; i < matchIds.size(); i++) {
            Long matchId = matchIds.get(i);
            
            Hero hero;
            // 70% chance to play a favorite hero
            if (random.nextDouble() < 0.7 && !favoriteHeroes.isEmpty()) {
                hero = favoriteHeroes.get(random.nextInt(favoriteHeroes.size()));
            } else {
                // 30% chance to play another hero
                hero = heroes.get(random.nextInt(heroes.size()));
            }
            
            // Generate match timestamp with exponential distribution (more recent = more common)
            double timeFactor = Math.exp(-0.1 * i);
            int daysAgo = (int) (30 * (1 - timeFactor));
            int hoursAgo = random.nextInt(24);
            int minutesAgo = random.nextInt(60);
            LocalDateTime date = now.minusDays(daysAgo).minusHours(hoursAgo).minusMinutes(minutesAgo);
            
            // Calculate if player's side won
            boolean isRadiant = random.nextBoolean();
            boolean radiantWon = random.nextBoolean();
            boolean won = (isRadiant && radiantWon) || (!isRadiant && !radiantWon);
            
            // Win rate tends to be higher on favorite heroes
            if (favoriteHeroes.contains(hero)) {
                // For favorite heroes, adjust the win probability
                if (won && random.nextDouble() > 0.6) {
                    // 60% chance to keep a win on favorite heroes
                    won = true;
                    radiantWon = isRadiant;
                } else if (!won && random.nextDouble() < 0.4) {
                    // 40% chance to flip a loss on favorite heroes to a win
                    won = true;
                    radiantWon = isRadiant;
                }
            }
            
            // Match duration
            int duration;
            double durationRandom = random.nextDouble();
            if (durationRandom < 0.05) {
                // Very short game (15-25 minutes) - stomp or early abandon
                duration = 900 + random.nextInt(600);
            } else if (durationRandom < 0.8) {
                // Normal game (25-45 minutes)
                duration = 1500 + random.nextInt(1200);
            } else if (durationRandom < 0.95) {
                // Long game (45-60 minutes)
                duration = 2700 + random.nextInt(900);
            } else {
                // Epic game (60-90 minutes)
                duration = 3600 + random.nextInt(1800);
            }
            
            // Game performance more likely to be good if they won
            int kills = won ? 
                4 + random.nextInt(16) : // 4-20 kills for wins
                2 + random.nextInt(10);  // 2-12 kills for losses
            
            int deaths = won ? 
                1 + random.nextInt(7) :  // 1-8 deaths for wins
                3 + random.nextInt(10);  // 3-13 deaths for losses
            
            int assists = 3 + random.nextInt(20); // 3-23 assists
            
            // Game modes with realistic distribution
            String mode;
            double modeRand = random.nextDouble();
            if (modeRand < 0.6) {
                mode = "Ranked All Pick";
            } else if (modeRand < 0.8) {
                mode = "Turbo";
            } else if (modeRand < 0.95) {
                mode = "All Pick";
            } else {
                mode = "Captains Mode";
            }
            
            // Create the player match object
            PlayerMatch match = new PlayerMatch(matchId, hero, won, duration, date, kills, deaths, assists, mode);
            match.setRadiantSide(isRadiant);
            match.setRadiantWin(radiantWon);
            
            matches.add(match);
        }
        
        // Sort matches by date (most recent first)
        matches.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        
        return matches;
    }
    
    /**
     * Get the match history sync status for a user.
     * 
     * @param steamId The user's Steam ID
     * @return Map containing sync status information
     */
    public Map<String, Object> getMatchHistorySyncStatus(String steamId) {
        try {
            // Convert Steam ID to account ID
            long steam64Id = Long.parseLong(steamId);
            int accountId32 = (int) (steam64Id & 0xFFFFFFFFL);
            
            Map<String, Object> status = userMatchRepository.getMatchHistorySyncStatus(accountId32);
            
            // If status is empty, create a default status
            if (status == null || status.isEmpty()) {
                status = new HashMap<>();
                status.put("accountId", accountId32);
                status.put("lastSyncTimestamp", null);
                status.put("syncInProgress", false);
                status.put("fullSyncCompleted", false);
                status.put("matchesCount", 0);
                status.put("lastMatchId", null);
                status.put("nextSyncDate", null);
                status.put("syncFrequency", "DAILY");
            }
            
            return status;
        } catch (Exception e) {
            logger.error("Error getting match history sync status for user {}", steamId, e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * Gets the MatchHistoryService instance.
     * 
     * @return The MatchHistoryService instance
     */
    public MatchHistoryService getMatchHistoryService() {
        return matchHistoryService;
    }
    
    /**
     * Sets the MatchHistoryService instance.
     * 
     * @param matchHistoryService The MatchHistoryService instance to set
     */
    public void setMatchHistoryService(MatchHistoryService matchHistoryService) {
        this.matchHistoryService = matchHistoryService;
    }
    
    /**
     * Gets the AutomatedMatchSyncService instance.
     * 
     * @return The AutomatedMatchSyncService instance
     */
    public AutomatedMatchSyncService getAutomatedSyncService() {
        return automatedSyncService;
    }
    
    /**
     * Sets the AutomatedMatchSyncService instance.
     * 
     * @param automatedSyncService The AutomatedMatchSyncService instance to set
     */
    public void setAutomatedSyncService(AutomatedMatchSyncService automatedSyncService) {
        this.automatedSyncService = automatedSyncService;
    }
    
    /**
     * Mark a match as favorite or unfavorite for a user.
     * 
     * @param steamId The user's Steam ID
     * @param matchId The match ID
     * @param isFavorite Whether the match should be marked as favorite
     * @return True if the update was successful
     */
    public boolean setMatchFavorite(String steamId, long matchId, boolean isFavorite) {
        try {
            // Convert Steam ID to account ID
            long steam64Id = Long.parseLong(steamId);
            int accountId32 = (int) (steam64Id & 0xFFFFFFFFL);
            
            return userMatchRepository.updateUserMatchDetails(accountId32, matchId, isFavorite, null, null);
        } catch (Exception e) {
            logger.error("Error setting match favorite status for user {} and match {}", steamId, matchId, e);
            return false;
        }
    }
    
    /**
     * Mark a match as hidden or unhidden for a user.
     * 
     * @param steamId The user's Steam ID
     * @param matchId The match ID
     * @param isHidden Whether the match should be marked as hidden
     * @return True if the update was successful
     */
    public boolean setMatchHidden(String steamId, long matchId, boolean isHidden) {
        try {
            // Convert Steam ID to account ID
            long steam64Id = Long.parseLong(steamId);
            int accountId32 = (int) (steam64Id & 0xFFFFFFFFL);
            
            return userMatchRepository.updateUserMatchDetails(accountId32, matchId, null, isHidden, null);
        } catch (Exception e) {
            logger.error("Error setting match hidden status for user {} and match {}", steamId, matchId, e);
            return false;
        }
    }
    
    /**
     * Update the notes for a match.
     * 
     * @param steamId The user's Steam ID
     * @param matchId The match ID
     * @param notes The notes to set
     * @return True if the update was successful
     */
    public boolean updateMatchNotes(String steamId, long matchId, String notes) {
        try {
            // Convert Steam ID to account ID
            long steam64Id = Long.parseLong(steamId);
            int accountId32 = (int) (steam64Id & 0xFFFFFFFFL);
            
            return userMatchRepository.updateUserMatchDetails(accountId32, matchId, null, null, notes);
        } catch (Exception e) {
            logger.error("Error updating match notes for user {} and match {}", steamId, matchId, e);
            return false;
        }
    }
}