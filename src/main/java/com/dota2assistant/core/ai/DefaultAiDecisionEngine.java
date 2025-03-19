package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.data.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of the AI decision engine that uses hero win rates,
 * synergies, and counter data to make informed drafting decisions.
 */
public class DefaultAiDecisionEngine implements AiDecisionEngine {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAiDecisionEngine.class);
    
    private final HeroRepository heroRepository;
    private final MatchRepository matchRepository;
    private final Random random = new Random();
    
    // AI difficulty level from 0.0 (random) to 1.0 (optimal)
    private double difficultyLevel = 0.8;
    
    // Weights for different factors in decision making
    private static final double WEIGHT_WIN_RATE = 0.3;
    private static final double WEIGHT_PICK_RATE = 0.2;
    private static final double WEIGHT_SYNERGY = 0.25;
    private static final double WEIGHT_COUNTER = 0.25;
    
    // Current rank for statistics
    private String currentRank = MatchRepository.RANK_LEGEND;
    
    /**
     * Constructor for the DefaultAiDecisionEngine
     * 
     * @param heroRepository The repository for hero data
     * @param matchRepository The repository for match data
     */
    public DefaultAiDecisionEngine(HeroRepository heroRepository, MatchRepository matchRepository) {
        this.heroRepository = heroRepository;
        this.matchRepository = matchRepository;
    }
    
    /**
     * Sets the difficulty level of the AI
     * 
     * @param difficultyLevel A value between 0.0 (random) and 1.0 (optimal)
     */
    public void setDifficultyLevel(double difficultyLevel) {
        if (difficultyLevel < 0.0 || difficultyLevel > 1.0) {
            throw new IllegalArgumentException("Difficulty level must be between 0.0 and 1.0");
        }
        this.difficultyLevel = difficultyLevel;
    }
    
    /**
     * Sets the rank to use for statistics
     * 
     * @param rank The rank to use
     */
    public void setCurrentRank(String rank) {
        this.currentRank = rank;
    }

    @Override
    public Hero suggestPick(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes) {
        // This is for the AI (Dire team)
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return null;
        }
        
        // If difficultyLevel is very low, make a random pick
        if (difficultyLevel < 0.2 || random.nextDouble() > difficultyLevel) {
            return available.get(random.nextInt(available.size()));
        }
        
        // Create a map of hero scores for picking
        Map<Hero, Double> heroScores = new HashMap<>();
        Map<Integer, Double> winRates = matchRepository.getHeroWinRatesByRank(currentRank);
        Map<Integer, Double> pickRates = matchRepository.getHeroPickRatesByRank(currentRank);
        Map<String, Double> synergies = matchRepository.getHeroSynergies(currentRank);
        Map<String, Double> counters = matchRepository.getHeroCounters(currentRank);
        
        for (Hero hero : available) {
            double score = 0.0;
            
            // Factor 1: Win rate
            double winRate = winRates.getOrDefault(hero.getId(), 0.5);
            score += WEIGHT_WIN_RATE * winRate;
            
            // Factor 2: Pick rate (popularity)
            double pickRate = pickRates.getOrDefault(hero.getId(), 0.0);
            score += WEIGHT_PICK_RATE * pickRate;
            
            // Factor 3: Synergy with team
            double synergyScore = 0.0;
            for (Hero teammate : direPicks) {
                String key = Math.min(hero.getId(), teammate.getId()) + "_" + 
                             Math.max(hero.getId(), teammate.getId());
                synergyScore += synergies.getOrDefault(key, 0.5);
            }
            if (!direPicks.isEmpty()) {
                synergyScore /= direPicks.size();
            }
            score += WEIGHT_SYNERGY * synergyScore;
            
            // Factor 4: Counter enemy heroes
            double counterScore = 0.0;
            for (Hero enemy : radiantPicks) {
                String key = hero.getId() + "_" + enemy.getId();
                counterScore += counters.getOrDefault(key, 0.5);
            }
            if (!radiantPicks.isEmpty()) {
                counterScore /= radiantPicks.size();
            }
            score += WEIGHT_COUNTER * counterScore;
            
            // Add some randomness
            score += (1.0 - difficultyLevel) * random.nextDouble() * 0.5;
            
            heroScores.put(hero, score);
        }
        
        // Sort heroes by score
        List<Hero> sortedHeroes = heroScores.entrySet().stream()
                .sorted(Map.Entry.<Hero, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        // Choose one of the top heroes based on difficulty level
        int maxIndex = Math.min(5, sortedHeroes.size()) - 1;
        int chosenIndex = (int) (maxIndex * (1.0 - difficultyLevel));
        
        Hero chosen = sortedHeroes.get(chosenIndex);
        logger.info("AI suggests picking {} (score: {})", chosen.getLocalizedName(), 
                heroScores.get(chosen));
                
        return chosen;
    }

    @Override
    public List<Hero> suggestPicks(List<Hero> radiantPicks, List<Hero> direPicks, 
                                 List<Hero> bannedHeroes, int count) {
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Create a map of hero scores for picking
        Map<Hero, Double> heroScores = new HashMap<>();
        Map<Integer, Double> winRates = matchRepository.getHeroWinRatesByRank(currentRank);
        Map<Integer, Double> pickRates = matchRepository.getHeroPickRatesByRank(currentRank);
        Map<String, Double> synergies = matchRepository.getHeroSynergies(currentRank);
        Map<String, Double> counters = matchRepository.getHeroCounters(currentRank);
        
        for (Hero hero : available) {
            double score = 0.0;
            
            // Factor 1: Win rate
            double winRate = winRates.getOrDefault(hero.getId(), 0.5);
            score += WEIGHT_WIN_RATE * winRate;
            
            // Factor 2: Pick rate (popularity)
            double pickRate = pickRates.getOrDefault(hero.getId(), 0.0);
            score += WEIGHT_PICK_RATE * pickRate;
            
            // Factor 3: Synergy with team
            double synergyScore = 0.0;
            for (Hero teammate : direPicks) {
                String key = Math.min(hero.getId(), teammate.getId()) + "_" + 
                             Math.max(hero.getId(), teammate.getId());
                synergyScore += synergies.getOrDefault(key, 0.5);
            }
            if (!direPicks.isEmpty()) {
                synergyScore /= direPicks.size();
            }
            score += WEIGHT_SYNERGY * synergyScore;
            
            // Factor 4: Counter enemy heroes
            double counterScore = 0.0;
            for (Hero enemy : radiantPicks) {
                String key = hero.getId() + "_" + enemy.getId();
                counterScore += counters.getOrDefault(key, 0.5);
            }
            if (!radiantPicks.isEmpty()) {
                counterScore /= radiantPicks.size();
            }
            score += WEIGHT_COUNTER * counterScore;
            
            // Add a small amount of randomness
            score += 0.05 * random.nextDouble();
            
            heroScores.put(hero, score);
        }
        
        // Sort heroes by score and return top N
        return heroScores.entrySet().stream()
                .sorted(Map.Entry.<Hero, Double>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public Hero suggestBan(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes) {
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return null;
        }
        
        // If difficultyLevel is very low, make a random ban
        if (difficultyLevel < 0.2 || random.nextDouble() > difficultyLevel) {
            return available.get(random.nextInt(available.size()));
        }
        
        // For bans, we want to target:
        // 1. Heroes that are strong against our current picks
        // 2. Heroes that have good synergy with enemy picks
        // 3. Heroes with high win rates
        Map<Hero, Double> heroScores = new HashMap<>();
        Map<Integer, Double> winRates = matchRepository.getHeroWinRatesByRank(currentRank);
        Map<Integer, Double> pickRates = matchRepository.getHeroPickRatesByRank(currentRank);
        Map<String, Double> synergies = matchRepository.getHeroSynergies(currentRank);
        Map<String, Double> counters = matchRepository.getHeroCounters(currentRank);
        
        for (Hero hero : available) {
            double score = 0.0;
            
            // Factor 1: Win rate (ban high win rate heroes)
            double winRate = winRates.getOrDefault(hero.getId(), 0.5);
            score += WEIGHT_WIN_RATE * winRate;
            
            // Factor 2: Pick rate (ban popular heroes)
            double pickRate = pickRates.getOrDefault(hero.getId(), 0.0);
            score += WEIGHT_PICK_RATE * pickRate;
            
            // Factor 3: Synergy with enemy team (ban heroes that work well with enemy picks)
            double synergyScore = 0.0;
            for (Hero enemyHero : radiantPicks) {
                String key = Math.min(hero.getId(), enemyHero.getId()) + "_" + 
                             Math.max(hero.getId(), enemyHero.getId());
                synergyScore += synergies.getOrDefault(key, 0.5);
            }
            if (!radiantPicks.isEmpty()) {
                synergyScore /= radiantPicks.size();
            }
            score += WEIGHT_SYNERGY * synergyScore;
            
            // Factor 4: Counter our team (ban heroes that counter our picks)
            double counterScore = 0.0;
            for (Hero ourHero : direPicks) {
                String key = hero.getId() + "_" + ourHero.getId();
                counterScore += counters.getOrDefault(key, 0.5);
            }
            if (!direPicks.isEmpty()) {
                counterScore /= direPicks.size();
            }
            score += WEIGHT_COUNTER * counterScore;
            
            // Add some randomness
            score += (1.0 - difficultyLevel) * random.nextDouble() * 0.5;
            
            heroScores.put(hero, score);
        }
        
        // Sort heroes by score
        List<Hero> sortedHeroes = heroScores.entrySet().stream()
                .sorted(Map.Entry.<Hero, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        // Choose one of the top heroes based on difficulty level
        int maxIndex = Math.min(5, sortedHeroes.size()) - 1;
        int chosenIndex = (int) (maxIndex * (1.0 - difficultyLevel));
        
        Hero chosen = sortedHeroes.get(chosenIndex);
        logger.info("AI suggests banning {} (score: {})", chosen.getLocalizedName(), 
                heroScores.get(chosen));
                
        return chosen;
    }

    @Override
    public List<Hero> suggestBans(List<Hero> radiantPicks, List<Hero> direPicks, 
                                List<Hero> bannedHeroes, int count) {
        List<Hero> available = getAvailableHeroes(radiantPicks, direPicks, bannedHeroes);
        if (available.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Create a map of hero scores for banning
        Map<Hero, Double> heroScores = new HashMap<>();
        Map<Integer, Double> winRates = matchRepository.getHeroWinRatesByRank(currentRank);
        Map<Integer, Double> pickRates = matchRepository.getHeroPickRatesByRank(currentRank);
        Map<String, Double> synergies = matchRepository.getHeroSynergies(currentRank);
        Map<String, Double> counters = matchRepository.getHeroCounters(currentRank);
        
        for (Hero hero : available) {
            double score = 0.0;
            
            // Factor 1: Win rate (ban high win rate heroes)
            double winRate = winRates.getOrDefault(hero.getId(), 0.5);
            score += WEIGHT_WIN_RATE * winRate;
            
            // Factor 2: Pick rate (ban popular heroes)
            double pickRate = pickRates.getOrDefault(hero.getId(), 0.0);
            score += WEIGHT_PICK_RATE * pickRate;
            
            // Factor 3: Synergy with enemy team (ban heroes that work well with enemy picks)
            double synergyScore = 0.0;
            for (Hero enemyHero : radiantPicks) {
                String key = Math.min(hero.getId(), enemyHero.getId()) + "_" + 
                             Math.max(hero.getId(), enemyHero.getId());
                synergyScore += synergies.getOrDefault(key, 0.5);
            }
            if (!radiantPicks.isEmpty()) {
                synergyScore /= radiantPicks.size();
            }
            score += WEIGHT_SYNERGY * synergyScore;
            
            // Factor 4: Counter our team (ban heroes that counter our picks)
            double counterScore = 0.0;
            for (Hero ourHero : direPicks) {
                String key = hero.getId() + "_" + ourHero.getId();
                counterScore += counters.getOrDefault(key, 0.5);
            }
            if (!direPicks.isEmpty()) {
                counterScore /= direPicks.size();
            }
            score += WEIGHT_COUNTER * counterScore;
            
            // Add a small amount of randomness
            score += 0.05 * random.nextDouble();
            
            heroScores.put(hero, score);
        }
        
        // Sort heroes by score and return top N
        return heroScores.entrySet().stream()
                .sorted(Map.Entry.<Hero, Double>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * Helper method to get all available heroes
     */
    private List<Hero> getAvailableHeroes(List<Hero> radiantPicks, List<Hero> direPicks, List<Hero> bannedHeroes) {
        // Get all heroes
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        // Create a set of unavailable hero IDs
        Set<Integer> unavailableIds = new HashSet<>();
        
        for (Hero hero : radiantPicks) {
            unavailableIds.add(hero.getId());
        }
        
        for (Hero hero : direPicks) {
            unavailableIds.add(hero.getId());
        }
        
        for (Hero hero : bannedHeroes) {
            unavailableIds.add(hero.getId());
        }
        
        // Filter the heroes
        return allHeroes.stream()
                .filter(hero -> !unavailableIds.contains(hero.getId()))
                .collect(Collectors.toList());
    }
}