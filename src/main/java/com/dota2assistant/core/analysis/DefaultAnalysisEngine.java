package com.dota2assistant.core.analysis;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.data.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of the AnalysisEngine interface that provides
 * analysis of drafts based on hero statistics and interactions.
 */
public class DefaultAnalysisEngine implements AnalysisEngine {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAnalysisEngine.class);
    
    private final HeroRepository heroRepository;
    private final MatchRepository matchRepository;
    
    // Current rank for statistics
    private String currentRank = MatchRepository.RANK_LEGEND;
    
    // Cached data for performance
    private Map<Integer, Double> winRates;
    private Map<String, Double> synergies;
    private Map<String, Double> counters;
    
    /**
     * Constructor for the DefaultAnalysisEngine
     * 
     * @param heroRepository The repository for hero data
     * @param matchRepository The repository for match data
     */
    public DefaultAnalysisEngine(HeroRepository heroRepository, MatchRepository matchRepository) {
        this.heroRepository = heroRepository;
        this.matchRepository = matchRepository;
        this.refreshData();
    }
    
    /**
     * Sets the current rank and refreshes the data
     * 
     * @param rank The rank to use for analysis
     */
    public void setCurrentRank(String rank) {
        this.currentRank = rank;
        refreshData();
    }
    
    /**
     * Refreshes the cached data from the repositories
     */
    private void refreshData() {
        this.winRates = matchRepository.getHeroWinRatesByRank(currentRank);
        this.synergies = matchRepository.getHeroSynergies(currentRank);
        this.counters = matchRepository.getHeroCounters(currentRank);
        logger.info("Refreshed analysis data for rank {}", currentRank);
    }

    @Override
    public double calculateTeamStrength(List<Hero> teamPicks) {
        if (teamPicks == null || teamPicks.isEmpty()) {
            return 0.0;
        }
        
        // Factor 1: Individual hero win rates (40%)
        double winRateScore = teamPicks.stream()
                .mapToDouble(hero -> winRates.getOrDefault(hero.getId(), 0.5))
                .average()
                .orElse(0.5);
        
        // Factor 2: Team synergy (60%)
        double synergyScore = 0.0;
        int synergyCount = 0;
        
        for (int i = 0; i < teamPicks.size(); i++) {
            for (int j = i + 1; j < teamPicks.size(); j++) {
                Hero hero1 = teamPicks.get(i);
                Hero hero2 = teamPicks.get(j);
                String key = Math.min(hero1.getId(), hero2.getId()) + "_" + 
                             Math.max(hero1.getId(), hero2.getId());
                synergyScore += synergies.getOrDefault(key, 0.5);
                synergyCount++;
            }
        }
        
        if (synergyCount > 0) {
            synergyScore /= synergyCount;
        } else {
            synergyScore = 0.5;
        }
        
        // Combine scores with weights
        double strength = (winRateScore * 0.4) + (synergyScore * 0.6);
        
        // Normalize to 0.3-0.8 range for UI purposes (no team is completely weak or strong)
        return 0.3 + (strength * 0.5);
    }

    @Override
    public String analyzeDraft(List<Hero> radiantPicks, List<Hero> direPicks) {
        if (radiantPicks.isEmpty() || direPicks.isEmpty()) {
            return "More heroes need to be drafted for analysis.";
        }
        
        StringBuilder analysis = new StringBuilder();
        
        // Calculate team strengths
        double radiantStrength = calculateTeamStrength(radiantPicks);
        double direStrength = calculateTeamStrength(direPicks);
        
        // Heading
        if (radiantStrength > direStrength + 0.1) {
            analysis.append("Radiant draft has an advantage. ");
        } else if (direStrength > radiantStrength + 0.1) {
            analysis.append("Dire draft has an advantage. ");
        } else {
            analysis.append("Both drafts are evenly matched. ");
        }
        
        // Team synergies
        List<String> radiantSynergies = identifyTeamSynergies(radiantPicks);
        if (!radiantSynergies.isEmpty()) {
            analysis.append("\n\nRadiant synergies:");
            for (String synergy : radiantSynergies.subList(0, Math.min(3, radiantSynergies.size()))) {
                analysis.append("\n- ").append(synergy);
            }
        }
        
        List<String> direSynergies = identifyTeamSynergies(direPicks);
        if (!direSynergies.isEmpty()) {
            analysis.append("\n\nDire synergies:");
            for (String synergy : direSynergies.subList(0, Math.min(3, direSynergies.size()))) {
                analysis.append("\n- ").append(synergy);
            }
        }
        
        // Counter matchups
        List<String> counterDescription = identifyCounters(radiantPicks, direPicks);
        if (!counterDescription.isEmpty()) {
            analysis.append("\n\nKey counter matchups:");
            for (String counter : counterDescription.subList(0, Math.min(3, counterDescription.size()))) {
                analysis.append("\n- ").append(counter);
            }
        }
        
        // Damage types and timing windows
        analysis.append("\n\nRadiant: ").append(analyzeDamageTypes(radiantPicks));
        analysis.append("\nDire: ").append(analyzeDamageTypes(direPicks));
        
        analysis.append("\n\nTiming windows:");
        analysis.append("\nRadiant: ").append(analyzeTimingWindows(radiantPicks));
        analysis.append("\nDire: ").append(analyzeTimingWindows(direPicks));
        
        // Win probability
        double radiantWinProb = predictWinProbability(radiantPicks, direPicks);
        double direWinProb = 1.0 - radiantWinProb;
        
        analysis.append("\n\nEstimated win probability: Radiant ")
                .append(Math.round(radiantWinProb * 100))
                .append("% / Dire ")
                .append(Math.round(direWinProb * 100))
                .append("%");
        
        return analysis.toString();
    }

    @Override
    public String getDraftSummary(List<Hero> radiantPicks, List<Hero> direPicks) {
        if (radiantPicks.isEmpty() || direPicks.isEmpty()) {
            return "Draft incomplete.";
        }
        
        StringBuilder summary = new StringBuilder();
        
        // Calculate team strengths
        double radiantStrength = calculateTeamStrength(radiantPicks);
        double direStrength = calculateTeamStrength(direPicks);
        double radiantWinProb = predictWinProbability(radiantPicks, direPicks);
        double direWinProb = 1.0 - radiantWinProb;
        
        // Determine overall advantage
        summary.append("Draft Summary:\n\n");
        
        if (radiantWinProb > 0.55) {
            summary.append("Radiant has drafted a stronger team composition (")
                    .append(Math.round(radiantWinProb * 100))
                    .append("% win probability).\n\n");
        } else if (radiantWinProb < 0.45) {
            summary.append("Dire has drafted a stronger team composition (")
                    .append(Math.round(direWinProb * 100))
                    .append("% win probability).\n\n");
        } else {
            summary.append("Both teams have drafted well-balanced compositions.\n\n");
        }
        
        // Key strengths of each draft
        summary.append("Radiant draft strengths:\n");
        summary.append("- ").append(analyzeTimingWindows(radiantPicks)).append("\n");
        List<String> radiantSynergies = identifyTeamSynergies(radiantPicks);
        if (!radiantSynergies.isEmpty()) {
            summary.append("- Strong synergy: ").append(radiantSynergies.get(0)).append("\n");
        }
        
        summary.append("\nDire draft strengths:\n");
        summary.append("- ").append(analyzeTimingWindows(direPicks)).append("\n");
        List<String> direSynergies = identifyTeamSynergies(direPicks);
        if (!direSynergies.isEmpty()) {
            summary.append("- Strong synergy: ").append(direSynergies.get(0)).append("\n");
        }
        
        // Winning conditions
        summary.append("\nKey factors for Radiant victory:\n");
        if (radiantWinProb > 0.5) {
            summary.append("- Leverage stronger team composition\n");
        }
        summary.append("- ").append(generateWinCondition(radiantPicks, direPicks));
        
        summary.append("\n\nKey factors for Dire victory:\n");
        if (radiantWinProb < 0.5) {
            summary.append("- Leverage stronger team composition\n");
        }
        summary.append("- ").append(generateWinCondition(direPicks, radiantPicks));
        
        return summary.toString();
    }
    
    /**
     * Generates a win condition suggestion based on draft analysis
     */
    private String generateWinCondition(List<Hero> team, List<Hero> opponents) {
        // This is a simplified implementation
        // In a real application, you'd have more detailed hero data and analysis
        
        // Check if this team is early game or late game focused
        boolean isEarlyGame = team.stream()
                .anyMatch(hero -> hero.getRoles() != null && 
                                 hero.getRoles().contains("Pusher"));
        
        boolean hasHardCarry = team.stream()
                .anyMatch(hero -> hero.getRoles() != null && 
                                 hero.getRoles().contains("Carry"));
        
        if (isEarlyGame) {
            return "Focus on early aggression and objective-based gameplay";
        } else if (hasHardCarry) {
            return "Protect your core and scale into the late game";
        } else {
            return "Control the map and look for advantageous teamfights";
        }
    }

    @Override
    public List<String> identifyTeamSynergies(List<Hero> teamPicks) {
        List<SynergyPair> synergyPairs = new ArrayList<>();
        
        for (int i = 0; i < teamPicks.size(); i++) {
            for (int j = i + 1; j < teamPicks.size(); j++) {
                Hero hero1 = teamPicks.get(i);
                Hero hero2 = teamPicks.get(j);
                String key = Math.min(hero1.getId(), hero2.getId()) + "_" + 
                             Math.max(hero1.getId(), hero2.getId());
                
                double synergyScore = synergies.getOrDefault(key, 0.5);
                
                // Only include significant synergies
                if (synergyScore > 0.55) {
                    synergyPairs.add(new SynergyPair(
                            hero1.getLocalizedName(), 
                            hero2.getLocalizedName(), 
                            synergyScore));
                }
            }
        }
        
        // Sort by synergy score
        synergyPairs.sort(Comparator.comparing(SynergyPair::getScore).reversed());
        
        // Convert to description strings
        return synergyPairs.stream()
                .map(pair -> {
                    // Cap the synergy percentage at 100% for display purposes
                    int displayPercentage = (int)Math.min(Math.round(pair.getScore() * 100), 100);
                    return pair.getHero1() + " + " + pair.getHero2() + 
                           " (" + displayPercentage + "% win rate together)";
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<String> identifyCounters(List<Hero> team1Picks, List<Hero> team2Picks) {
        List<CounterPair> counterPairs = new ArrayList<>();
        
        // Check each hero from team1 against each hero from team2
        for (Hero hero1 : team1Picks) {
            for (Hero hero2 : team2Picks) {
                String key = hero1.getId() + "_" + hero2.getId();
                double counterScore = counters.getOrDefault(key, 0.5);
                
                // Only include significant counters
                if (counterScore > 0.55) {
                    counterPairs.add(new CounterPair(
                            hero1.getLocalizedName(),
                            hero2.getLocalizedName(),
                            counterScore));
                }
            }
        }
        
        // Check each hero from team2 against each hero from team1
        for (Hero hero1 : team2Picks) {
            for (Hero hero2 : team1Picks) {
                String key = hero1.getId() + "_" + hero2.getId();
                double counterScore = counters.getOrDefault(key, 0.5);
                
                // Only include significant counters
                if (counterScore > 0.55) {
                    counterPairs.add(new CounterPair(
                            hero1.getLocalizedName(),
                            hero2.getLocalizedName(),
                            counterScore));
                }
            }
        }
        
        // Sort by counter score
        counterPairs.sort(Comparator.comparing(CounterPair::getScore).reversed());
        
        // Convert to description strings
        return counterPairs.stream()
                .map(pair -> {
                    // Cap the advantage percentage at 100% for display purposes
                    int displayPercentage = (int)Math.min(Math.round(pair.getScore() * 100), 100);
                    return pair.getCounterHero() + " counters " + pair.getCounteredHero() + 
                           " (" + displayPercentage + "% advantage)";
                })
                .collect(Collectors.toList());
    }

    @Override
    public String analyzeDamageTypes(List<Hero> teamPicks) {
        // In a real implementation, this would use detailed hero data
        // about damage output types (physical, magical, pure)
        
        // Dummy implementation for now
        int physical = 0;
        int magical = 0;
        int mixed = 0;
        
        for (Hero hero : teamPicks) {
            if (hero.getPrimaryAttribute() != null) {
                if (hero.getPrimaryAttribute().equals("str") || hero.getPrimaryAttribute().equals("agi")) {
                    physical++;
                } else if (hero.getPrimaryAttribute().equals("int")) {
                    magical++;
                } else {
                    mixed++;
                }
            } else {
                mixed++;
            }
        }
        
        if (physical > magical + mixed) {
            return "Primarily physical damage";
        } else if (magical > physical + mixed) {
            return "Primarily magical damage";
        } else {
            return "Mixed damage types";
        }
    }

    @Override
    public String analyzeTimingWindows(List<Hero> teamPicks) {
        // In a real implementation, this would use detailed hero data
        // about power spikes, level dependencies, item timings, etc.
        
        // Dummy implementation based on roles
        int earlyGameHeroes = 0;
        int midGameHeroes = 0;
        int lateGameHeroes = 0;
        
        for (Hero hero : teamPicks) {
            List<String> roles = hero.getRoles();
            if (roles != null) {
                if (roles.contains("Pusher") || roles.contains("Support")) {
                    earlyGameHeroes++;
                } else if (roles.contains("Carry") && roles.contains("Durable")) {
                    lateGameHeroes++;
                } else {
                    midGameHeroes++;
                }
            }
        }
        
        if (earlyGameHeroes > midGameHeroes && earlyGameHeroes > lateGameHeroes) {
            return "Strong early game with good pushing potential";
        } else if (lateGameHeroes > earlyGameHeroes && lateGameHeroes > midGameHeroes) {
            return "Strong late game scaling with good carry potential";
        } else {
            return "Well-balanced team with mid-game power spike";
        }
    }

    @Override
    public double predictWinProbability(List<Hero> radiantPicks, List<Hero> direPicks) {
        if (radiantPicks.isEmpty() || direPicks.isEmpty()) {
            return 0.5; // Equal chance if teams are not yet drafted
        }
        
        double radiantStrength = calculateTeamStrength(radiantPicks);
        double direStrength = calculateTeamStrength(direPicks);
        
        // Calculate direct counter advantage
        double radiantCounterAdvantage = 0.0;
        double direCounterAdvantage = 0.0;
        
        // Radiant heroes countering Dire heroes
        for (Hero radiantHero : radiantPicks) {
            for (Hero direHero : direPicks) {
                String key = radiantHero.getId() + "_" + direHero.getId();
                double counterScore = counters.getOrDefault(key, 0.5);
                radiantCounterAdvantage += (counterScore - 0.5) * 2; // Convert from [0.5, 1.0] to [0.0, 1.0]
            }
        }
        
        // Dire heroes countering Radiant heroes
        for (Hero direHero : direPicks) {
            for (Hero radiantHero : radiantPicks) {
                String key = direHero.getId() + "_" + radiantHero.getId();
                double counterScore = counters.getOrDefault(key, 0.5);
                direCounterAdvantage += (counterScore - 0.5) * 2; // Convert from [0.5, 1.0] to [0.0, 1.0]
            }
        }
        
        // Normalize counter advantages by team size
        int radiantSize = radiantPicks.size();
        int direSize = direPicks.size();
        int totalPairings = radiantSize * direSize;
        
        if (totalPairings > 0) {
            radiantCounterAdvantage /= totalPairings;
            direCounterAdvantage /= totalPairings;
        }
        
        // Final win probability calculation
        // 70% based on team strength, 30% based on counter advantage
        double radiantScore = (radiantStrength * 0.7) + (radiantCounterAdvantage * 0.3);
        double direScore = (direStrength * 0.7) + (direCounterAdvantage * 0.3);
        
        // Convert to probability (using logistic function)
        double diff = radiantScore - direScore;
        // Calculate probability using logistic function
        double probability = 1.0 / (1.0 + Math.exp(-diff * 5)); // Scaling factor 5
        
        // Cap extreme probabilities to keep the UI more balanced
        // Cap at 0.75 (75%) to 0.25 (25%) range
        return Math.max(0.25, Math.min(0.75, probability));
    }
    
    /**
     * Helper class for representing hero synergies
     */
    private static class SynergyPair {
        private final String hero1;
        private final String hero2;
        private final double score;
        
        SynergyPair(String hero1, String hero2, double score) {
            this.hero1 = hero1;
            this.hero2 = hero2;
            this.score = score;
        }
        
        public String getHero1() {
            return hero1;
        }
        
        public String getHero2() {
            return hero2;
        }
        
        public double getScore() {
            return score;
        }
    }
    
    /**
     * Helper class for representing hero counters
     */
    private static class CounterPair {
        private final String counterHero;
        private final String counteredHero;
        private final double score;
        
        CounterPair(String counterHero, String counteredHero, double score) {
            this.counterHero = counterHero;
            this.counteredHero = counteredHero;
            this.score = score;
        }
        
        public String getCounterHero() {
            return counterHero;
        }
        
        public String getCounteredHero() {
            return counteredHero;
        }
        
        public double getScore() {
            return score;
        }
    }
}