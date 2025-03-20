package com.dota2assistant.core.analysis;

import com.dota2assistant.core.draft.DraftMode;
import com.dota2assistant.core.draft.DraftPhase;
import com.dota2assistant.core.draft.DraftState;
import com.dota2assistant.core.draft.Team;
import com.dota2assistant.data.model.DraftData;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.DraftDataRepository;
import com.dota2assistant.data.repository.HeroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for analyzing draft data and providing recommendations.
 */
@Service
public class DraftAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(DraftAnalysisService.class);
    
    private final DraftDataRepository draftDataRepository;
    private final HeroRepository heroRepository;
    private final AbilityInteractionAnalyzer abilityAnalyzer;
    private final AbilityClassifier abilityClassifier;
    
    @Autowired
    public DraftAnalysisService(DraftDataRepository draftDataRepository, HeroRepository heroRepository) {
        this(draftDataRepository, heroRepository, new AbilityInteractionAnalyzer(), new AbilityClassifier());
    }
    
    @Autowired
    public DraftAnalysisService(DraftDataRepository draftDataRepository, HeroRepository heroRepository, 
                              AbilityInteractionAnalyzer abilityAnalyzer, AbilityClassifier abilityClassifier) {
        this.draftDataRepository = draftDataRepository;
        this.heroRepository = heroRepository;
        this.abilityAnalyzer = abilityAnalyzer;
        this.abilityClassifier = abilityClassifier;
        
        loadDraftData();
    }
    
    /**
     * Load draft data from the repository.
     */
    public void loadDraftData() {
        int draftDataCount = draftDataRepository.getDraftDataCount();
        logger.info("Loading draft data, found {} files", draftDataCount);
    }
    
    /**
     * Get pick recommendations for the current draft state.
     *
     * @param draftState The current state of the draft
     * @param team The team to get recommendations for
     * @param limit The maximum number of recommendations to return
     * @return List of recommended heroes with their win rates
     */
    public List<HeroRecommendation> getPickRecommendations(DraftState draftState, Team team, int limit) {
        logger.info("Generating pick recommendations for team {}", team);
        
        // Get all available heroes
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        // Filter out already picked/banned heroes
        Set<Integer> unavailableHeroes = getUnavailableHeroes(draftState);
        
        // Get all draft data
        List<DraftData> draftDataList = draftDataRepository.getAllDraftData();
        if (draftDataList.isEmpty()) {
            logger.warn("No draft data available for analysis");
            return Collections.emptyList();
        }
        
        // Calculate pick rates for all heroes
        Map<Integer, Integer> pickCounts = new HashMap<>();
        Map<Integer, Integer> winCounts = new HashMap<>();
        
        // Analyze all drafts to find most popular/successful heroes
        for (DraftData draftData : draftDataList) {
            boolean isRadiantWin = draftData.isRadiantWin();
            
            // Process all picks to find global pick rates
            for (DraftData.DraftAction pick : draftData.getRadiantPicks()) {
                pickCounts.merge(pick.getHeroId(), 1, Integer::sum);
                if (isRadiantWin) {
                    winCounts.merge(pick.getHeroId(), 1, Integer::sum);
                }
            }
            
            for (DraftData.DraftAction pick : draftData.getDirePicks()) {
                pickCounts.merge(pick.getHeroId(), 1, Integer::sum);
                if (!isRadiantWin) {
                    winCounts.merge(pick.getHeroId(), 1, Integer::sum);
                }
            }
        }
        
        // Calculate win rates for each hero
        Map<Integer, HeroRecommendation> heroRecommendations = new HashMap<>();
        
        for (Hero hero : allHeroes) {
            if (!unavailableHeroes.contains(hero.getId())) {
                int heroId = hero.getId();
                
                // Get total picks and wins for this hero
                int totalPicks = pickCounts.getOrDefault(heroId, 0);
                int wins = winCounts.getOrDefault(heroId, 0);
                
                // Calculate win rate
                double winRate = totalPicks > 0 ? (double) wins / totalPicks : 0.0;
                
                // Calculate overall popularity (normalize by total drafts)
                double pickRate = (double) totalPicks / draftDataList.size();
                
                // Check for synergies with already picked heroes
                double synergyScore = calculateSynergyScore(hero, draftState, team);
                
                // Check for counters against enemy heroes
                double counterScore = calculateCounterScore(hero, draftState, team);
                
                // Evaluate how this hero enhances team composition
                double compositionScore = evaluateTeamCompositionBalance(hero, draftState, team);
                
                // Heavily weight pick rate to prioritize meta heroes, but also consider team composition
                double combinedScore = (pickRate * 0.5) + (winRate * 0.1) + 
                                     (synergyScore * 0.15) + (counterScore * 0.15) + 
                                     (compositionScore * 0.1);
                
                // Create recommendation with ability-based reasons
                HeroRecommendation recommendation = new HeroRecommendation(
                        hero, 
                        combinedScore, 
                        totalPicks > 0 ? winRate : -1.0, 
                        synergyScore, 
                        counterScore, 
                        totalPicks
                );
                
                // Add synergy reasons from ability analysis
                List<String> synergyReasons = collectSynergyReasons(hero, draftState, team);
                for (String reason : synergyReasons) {
                    recommendation.addSynergyReason(reason);
                }
                
                // Add counter reasons from ability analysis
                List<String> counterReasons = collectCounterReasons(hero, draftState, team);
                for (String reason : counterReasons) {
                    recommendation.addCounterReason(reason);
                }
                
                // Add composition-based reasons
                List<String> compositionReasons = collectCompositionReasons(hero, draftState, team);
                for (String reason : compositionReasons) {
                    recommendation.addSynergyReason("Team Composition: " + reason);
                }
                
                heroRecommendations.put(heroId, recommendation);
            }
        }
        
        // Sort by combined score (heavily influenced by pick rate) and limit recommendations
        return heroRecommendations.values().stream()
                .sorted(Comparator.comparingDouble(HeroRecommendation::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Get ban recommendations for the current draft state.
     *
     * @param draftState The current state of the draft
     * @param team The team to get recommendations for
     * @param limit The maximum number of recommendations to return
     * @return List of recommended heroes to ban with their win rates
     */
    public List<HeroRecommendation> getBanRecommendations(DraftState draftState, Team team, int limit) {
        logger.info("Generating ban recommendations for team {}", team);
        
        // Get all available heroes
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        Set<Integer> unavailableHeroes = getUnavailableHeroes(draftState);
        List<DraftData> draftDataList = draftDataRepository.getAllDraftData();
        
        if (draftDataList.isEmpty()) {
            logger.warn("No draft data available for analysis");
            return Collections.emptyList();
        }
        
        // Calculate ban rates for all heroes
        Map<Integer, Integer> banCounts = new HashMap<>();
        Map<Integer, Integer> pickCounts = new HashMap<>();
        Map<Integer, Integer> winCounts = new HashMap<>();
        Map<Integer, Integer> firstPhaseBanCounts = new HashMap<>();
        
        // Track which team has first pick in each match
        Map<DraftData, Team> firstPickTeams = new HashMap<>();
        
        // Analyze all drafts to find most banned heroes and first pick patterns
        for (DraftData draftData : draftDataList) {
            boolean isRadiantWin = draftData.isRadiantWin();
            
            // Determine which team had first pick based on pick order
            Team firstPickTeam = null;
            if (!draftData.getRadiantPicks().isEmpty() && !draftData.getDirePicks().isEmpty()) {
                // Compare the order of first picks from each team
                int radiantFirstOrder = draftData.getRadiantPicks().get(0).getOrder();
                int direFirstOrder = draftData.getDirePicks().get(0).getOrder();
                firstPickTeam = radiantFirstOrder < direFirstOrder ? Team.RADIANT : Team.DIRE;
                firstPickTeams.put(draftData, firstPickTeam);
            }
            
            // Count all bans and identify first phase bans (typically orders 0-5)
            for (DraftData.DraftAction ban : draftData.getRadiantBans()) {
                banCounts.merge(ban.getHeroId(), 1, Integer::sum);
                
                // Track first phase bans (usually orders 0-5)
                if (ban.getOrder() <= 5) {
                    firstPhaseBanCounts.merge(ban.getHeroId(), 1, Integer::sum);
                }
            }
            
            for (DraftData.DraftAction ban : draftData.getDireBans()) {
                banCounts.merge(ban.getHeroId(), 1, Integer::sum);
                
                // Track first phase bans
                if (ban.getOrder() <= 5) {
                    firstPhaseBanCounts.merge(ban.getHeroId(), 1, Integer::sum);
                }
            }
            
            // Count picks and wins to calculate hero strength
            for (DraftData.DraftAction pick : draftData.getRadiantPicks()) {
                pickCounts.merge(pick.getHeroId(), 1, Integer::sum);
                if (isRadiantWin) {
                    winCounts.merge(pick.getHeroId(), 1, Integer::sum);
                }
            }
            
            for (DraftData.DraftAction pick : draftData.getDirePicks()) {
                pickCounts.merge(pick.getHeroId(), 1, Integer::sum);
                if (!isRadiantWin) {
                    winCounts.merge(pick.getHeroId(), 1, Integer::sum);
                }
            }
        }
        
        // Build recommendations
        Map<Integer, HeroRecommendation> heroBanRecommendations = new HashMap<>();
        
        // Determine if we have first pick (needs to be known for draft phase-specific recommendations)
        boolean isFirstPick = false;
        DraftPhase currentPhase = draftState.getCurrentPhase();
        
        // For Captain's Mode, first and second phases determine different ban strategies
        boolean isFirstBanPhase = currentPhase == DraftPhase.CM_BAN_1 || currentPhase == DraftPhase.CM_BAN_2;
        
        // Get our team's picks so far
        List<Hero> ourPicks = team == Team.RADIANT ? draftState.getRadiantPicks() : draftState.getDirePicks();
        
        // For All Pick, we can determine first pick by whether our team has the first pick slot
        if (draftState.getMode() == DraftMode.CAPTAINS_MODE) {
            // In Captain's Mode, the team taking the current action is determined by the phase
            isFirstPick = (currentPhase == DraftPhase.CM_BAN_1 && draftState.getCurrentTeam() == team) ||
                         (currentPhase == DraftPhase.CM_BAN_2 && draftState.getCurrentTeam() != team);
        }
        
        // Different ban strategy based on phase and pick position
        for (Hero hero : allHeroes) {
            if (!unavailableHeroes.contains(hero.getId())) {
                int heroId = hero.getId();
                
                // Get total bans, picks and wins for this hero
                int totalBans = banCounts.getOrDefault(heroId, 0);
                int totalPicks = pickCounts.getOrDefault(heroId, 0);
                int firstPhaseBans = firstPhaseBanCounts.getOrDefault(heroId, 0);
                int wins = winCounts.getOrDefault(heroId, 0);
                
                // Calculate rates normalized by total drafts
                double banRate = (double) totalBans / draftDataList.size();
                double firstPhaseBanRate = (double) firstPhaseBans / draftDataList.size();
                
                // Calculate win rate
                double winRate = totalPicks > 0 ? (double) wins / totalPicks : 0.0;
                
                // Calculate threat score against our current picks
                double threatScore = calculateThreatScore(hero, draftState, team);
                
                // Modify scoring based on ban phase and first pick status
                double combinedScore;
                
                if (isFirstBanPhase) {
                    if (isFirstPick) {
                        // First pick team should focus on banning heroes that counter their strategy
                        // rather than universally strong heroes (which 2nd pick will ban anyway)
                        combinedScore = (threatScore * 0.5) + (winRate * 0.3) + (banRate * 0.2);
                        
                        // Reduce priority for heroes commonly banned in first phase by second pick teams
                        if (firstPhaseBanRate > 0.4) {
                            combinedScore *= 0.7; // Reduce score - likely to be banned by 2nd pick team anyway
                        }
                    } else {
                        // Second pick team should ban the universally strong heroes
                        combinedScore = (banRate * 0.5) + (winRate * 0.3) + (threatScore * 0.2);
                    }
                } else {
                    // Later ban phases - prioritize counters to our existing picks or synergies with enemy picks
                    combinedScore = (threatScore * 0.6) + (winRate * 0.3) + (banRate * 0.1);
                }
                
                // Create recommendation with ability-based reasons
                HeroRecommendation recommendation = new HeroRecommendation(
                        hero, 
                        combinedScore, 
                        totalPicks > 0 ? winRate : -1.0, 
                        banRate,  // Use ban rate instead of synergy score
                        threatScore,
                        totalBans + totalPicks  // Total times this hero was picked or banned
                );
                
                // Add ban strategy explanation based on pick position
                if (isFirstBanPhase && isFirstPick && firstPhaseBanRate > 0.4) {
                    recommendation.addCounterReason(
                        "This hero will likely be banned by second-pick team anyway; focus on specific counters to your strategy instead");
                }
                
                // Collect threat reasons for bans
                List<String> threatReasons = collectThreatReasons(hero, draftState, team);
                for (String reason : threatReasons) {
                    recommendation.addCounterReason(reason);
                }
                
                heroBanRecommendations.put(heroId, recommendation);
            }
        }
        
        // Sort by combined score and limit recommendations
        return heroBanRecommendations.values().stream()
                .sorted(Comparator.comparingDouble(HeroRecommendation::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all heroes that are unavailable (already picked or banned).
     *
     * @param draftState The current state of the draft
     * @return Set of hero IDs that are unavailable
     */
    private Set<Integer> getUnavailableHeroes(DraftState draftState) {
        Set<Integer> unavailableHeroes = new HashSet<>();
        
        // Add radiant picks
        for (Hero hero : draftState.getRadiantPicks()) {
            unavailableHeroes.add(hero.getId());
        }
        
        // Add dire picks
        for (Hero hero : draftState.getDirePicks()) {
            unavailableHeroes.add(hero.getId());
        }
        
        // Add all bans
        for (Hero hero : draftState.getBannedHeroes()) {
            unavailableHeroes.add(hero.getId());
        }
        
        return unavailableHeroes;
    }
    
    /**
     * Calculate synergy score of a hero with the team's current picks.
     * 
     * @param hero The hero to check
     * @param draftState The current draft state
     * @param team The team to check synergies for
     * @return A synergy score between 0.0 and 1.0
     */
    private double calculateSynergyScore(Hero hero, DraftState draftState, Team team) {
        List<Hero> teamPicks = team == Team.RADIANT ? 
                draftState.getRadiantPicks() : draftState.getDirePicks();
        
        if (teamPicks.isEmpty()) {
            return 0.5; // Neutral score if no picks yet
        }
        
        // Calculate average synergy with all existing team picks
        double totalSynergy = 0;
        List<String> synergyReasons = new ArrayList<>();
        
        for (Hero teamHero : teamPicks) {
            // Use the ability analyzer to calculate synergy
            AbilityInteractionAnalyzer.SynergyAnalysis analysis = 
                abilityAnalyzer.analyzeSynergy(hero, teamHero);
            
            totalSynergy += analysis.getScore();
            
            // Collect the top reason if there is one
            if (!analysis.getReasons().isEmpty()) {
                synergyReasons.add(analysis.getReasons().get(0));
            }
        }
        
        // Average the synergy scores
        double synergyScore = teamPicks.isEmpty() ? 0.5 : totalSynergy / teamPicks.size();
        
        // Log top synergy reasons
        if (!synergyReasons.isEmpty() && synergyScore > 0.6) {
            logger.debug("High synergy for {}: {}", hero.getLocalizedName(), 
                       String.join("; ", synergyReasons.subList(0, Math.min(2, synergyReasons.size()))));
        }
        
        // Use ability-based synergy if data is available, otherwise use 50% as default
        return synergyScore > 0 ? synergyScore : 0.5;
    }
    
    /**
     * Calculate counter score of a hero against the enemy team's current picks.
     * 
     * @param hero The hero to check
     * @param draftState The current draft state
     * @param team The team to check counters for
     * @return A counter score between 0.0 and 1.0
     */
    private double calculateCounterScore(Hero hero, DraftState draftState, Team team) {
        List<Hero> enemyPicks = team == Team.RADIANT ? 
                draftState.getDirePicks() : draftState.getRadiantPicks();
        
        if (enemyPicks.isEmpty()) {
            return 0.5; // Neutral score if no enemy picks yet
        }
        
        // Calculate average counter potential against all existing enemy picks
        double totalCounter = 0;
        List<String> counterReasons = new ArrayList<>();
        
        for (Hero enemyHero : enemyPicks) {
            // Use the ability analyzer to calculate counter score
            AbilityInteractionAnalyzer.CounterAnalysis analysis = 
                abilityAnalyzer.analyzeCounter(hero, enemyHero);
            
            totalCounter += analysis.getScore();
            
            // Collect the top reason if there is one
            if (!analysis.getReasons().isEmpty()) {
                counterReasons.add(analysis.getReasons().get(0));
            }
        }
        
        // Average the counter scores
        double counterScore = enemyPicks.isEmpty() ? 0.5 : totalCounter / enemyPicks.size();
        
        // Log top counter reasons
        if (!counterReasons.isEmpty() && counterScore > 0.6) {
            logger.debug("Strong counter with {}: {}", hero.getLocalizedName(), 
                      String.join("; ", counterReasons.subList(0, Math.min(2, counterReasons.size()))));
        }
        
        // Use ability-based counter if data is available, otherwise use 50% as default
        return counterScore > 0 ? counterScore : 0.5;
    }
    
    /**
     * Calculate threat score of a hero against our team's current picks.
     * 
     * @param hero The hero to check
     * @param draftState The current draft state
     * @param team The team to check threats for
     * @return A threat score between 0.0 and 1.0
     */
    private double calculateThreatScore(Hero hero, DraftState draftState, Team team) {
        // Similar to counter score, but reversed perspective
        List<Hero> ourPicks = team == Team.RADIANT ? 
                draftState.getRadiantPicks() : draftState.getDirePicks();
        
        if (ourPicks.isEmpty()) {
            return 0.5; // Neutral score if no picks yet
        }
        
        // Calculate average threat level against all our picks
        double totalThreat = 0;
        List<String> threatReasons = new ArrayList<>();
        
        for (Hero ourHero : ourPicks) {
            // Use the ability analyzer with reversed order to see how the hero counters our picks
            AbilityInteractionAnalyzer.CounterAnalysis analysis = 
                abilityAnalyzer.analyzeCounter(hero, ourHero);
            
            totalThreat += analysis.getScore();
            
            // Collect the top reason if there is one
            if (!analysis.getReasons().isEmpty()) {
                threatReasons.add(analysis.getReasons().get(0));
            }
        }
        
        // Average the threat scores
        double threatScore = ourPicks.isEmpty() ? 0.5 : totalThreat / ourPicks.size();
        
        // Log top threat reasons
        if (!threatReasons.isEmpty() && threatScore > 0.6) {
            logger.debug("High threat from {}: {}", hero.getLocalizedName(), 
                       String.join("; ", threatReasons.subList(0, Math.min(2, threatReasons.size()))));
        }
        
        // Use ability-based threat if data is available, otherwise use 50% as default
        return threatScore > 0 ? threatScore : 0.5;
    }
    
    /**
     * Collect synergy reasons for a hero based on ability interactions with team picks.
     * 
     * @param hero The hero to check
     * @param draftState The current draft state
     * @param team The team to check synergies for
     * @return A list of synergy reasons
     */
    private List<String> collectSynergyReasons(Hero hero, DraftState draftState, Team team) {
        List<String> reasons = new ArrayList<>();
        List<Hero> teamPicks = team == Team.RADIANT ? 
                draftState.getRadiantPicks() : draftState.getDirePicks();
        
        if (teamPicks.isEmpty()) {
            return reasons;
        }
        
        // Collect synergy reasons from ability analysis for each team pick
        for (Hero teamHero : teamPicks) {
            AbilityInteractionAnalyzer.SynergyAnalysis analysis = 
                abilityAnalyzer.analyzeSynergy(hero, teamHero);
            
            // Add top 2 reasons if score is significant
            if (analysis.getScore() >= 0.4 && !analysis.getReasons().isEmpty()) {
                int reasonCount = Math.min(2, analysis.getReasons().size());
                for (int i = 0; i < reasonCount; i++) {
                    reasons.add(analysis.getReasons().get(i));
                }
            }
        }
        
        return reasons;
    }
    
    /**
     * Collect counter reasons for a hero based on ability interactions with enemy picks.
     * 
     * @param hero The hero to check
     * @param draftState The current draft state
     * @param team The team to check counters for
     * @return A list of counter reasons
     */
    private List<String> collectCounterReasons(Hero hero, DraftState draftState, Team team) {
        List<String> reasons = new ArrayList<>();
        List<Hero> enemyPicks = team == Team.RADIANT ? 
                draftState.getDirePicks() : draftState.getRadiantPicks();
        
        if (enemyPicks.isEmpty()) {
            return reasons;
        }
        
        // Collect counter reasons from ability analysis for each enemy pick
        for (Hero enemyHero : enemyPicks) {
            AbilityInteractionAnalyzer.CounterAnalysis analysis = 
                abilityAnalyzer.analyzeCounter(hero, enemyHero);
            
            // Add top 2 reasons if score is significant
            if (analysis.getScore() >= 0.4 && !analysis.getReasons().isEmpty()) {
                int reasonCount = Math.min(2, analysis.getReasons().size());
                for (int i = 0; i < reasonCount; i++) {
                    reasons.add(analysis.getReasons().get(i));
                }
            }
        }
        
        return reasons;
    }
    
    /**
     * Collect threat reasons for a hero based on how it counters our team's picks.
     * Used for ban recommendations.
     * 
     * @param hero The hero to check
     * @param draftState The current draft state
     * @param team The team to check threats for
     * @return A list of threat reasons
     */
    private List<String> collectThreatReasons(Hero hero, DraftState draftState, Team team) {
        List<String> reasons = new ArrayList<>();
        List<Hero> ourPicks = team == Team.RADIANT ? 
                draftState.getRadiantPicks() : draftState.getDirePicks();
        
        if (ourPicks.isEmpty()) {
            return reasons;
        }
        
        // Collect threat reasons from ability analysis
        for (Hero ourHero : ourPicks) {
            AbilityInteractionAnalyzer.CounterAnalysis analysis = 
                abilityAnalyzer.analyzeCounter(hero, ourHero);
            
            // Add top 2 reasons if score is significant
            if (analysis.getScore() >= 0.4 && !analysis.getReasons().isEmpty()) {
                int reasonCount = Math.min(2, analysis.getReasons().size());
                for (int i = 0; i < reasonCount; i++) {
                    reasons.add(analysis.getReasons().get(i));
                }
            }
        }
        
        return reasons;
    }
    
    /**
     * Evaluates how a hero contributes to team composition balance
     * 
     * @param hero The hero to evaluate
     * @param draftState The current draft state
     * @param team The team to evaluate for
     * @return A score representing how well the hero complements the team's needs
     */
    private double evaluateTeamCompositionBalance(Hero hero, DraftState draftState, Team team) {
        List<Hero> teamPicks = team == Team.RADIANT ? 
                draftState.getRadiantPicks() : draftState.getDirePicks();
        
        if (teamPicks.isEmpty()) {
            // For the first pick, we have no composition yet, so evaluate the hero individually
            AbilityClassifier.HeroAbilityProfile profile = abilityClassifier.classifyHeroAbilities(hero);
            
            // For first pick, versatile heroes or those with teamfight potential are valued
            double firstPickScore = 0.5;  // Base score for any hero
            
            // Bonus for heroes with teamfight abilities
            if (profile.hasSignificantTeamfight()) {
                firstPickScore += 0.2;
            }
            
            // Bonus for heroes with control abilities
            if (profile.hasSignificantControl()) {
                firstPickScore += 0.1;
            }
            
            // Bonus for versatile heroes
            if (profile.getStrengthsSummary().split(",").length >= 3) {
                firstPickScore += 0.2;
            }
            
            return firstPickScore;
        }
        
        // Analyze current team composition
        Map<String, Double> teamScores = analyzeTeamComposition(teamPicks);
        
        // Analyze what this hero brings
        AbilityClassifier.HeroAbilityProfile heroProfile = abilityClassifier.classifyHeroAbilities(hero);
        
        // Scores for evaluating how well the hero complements the team
        double compositionScore = 0.5;  // Base score
        List<String> compositionReasons = new ArrayList<>();
        
        // Check for teamfight capability - very important to have at least one strong teamfighter
        double teamfightNeed = 0.8 - teamScores.getOrDefault("teamfight", 0.0);
        if (teamfightNeed > 0.3 && heroProfile.getTeamfightScore() > 0.6) {
            compositionScore += 0.3;
            compositionReasons.add("Adds needed teamfight capability");
        }
        
        // Check for control capability - teams need some control
        double controlNeed = 0.8 - teamScores.getOrDefault("control", 0.0);
        if (controlNeed > 0.3 && heroProfile.getControlScore() > 0.6) {
            compositionScore += 0.2;
            compositionReasons.add("Adds needed control capability");
        }
        
        // Check for damage balance - teams need a mix of burst and sustained damage
        double burstDamageNeed = 0.7 - teamScores.getOrDefault("burstDamage", 0.0);
        double sustainedDamageNeed = 0.7 - teamScores.getOrDefault("sustainedDamage", 0.0);
        
        if (burstDamageNeed > 0.3 && heroProfile.getBurstDamageScore() > 0.6) {
            compositionScore += 0.2;
            compositionReasons.add("Adds needed burst damage");
        }
        
        if (sustainedDamageNeed > 0.3 && heroProfile.getSustainedDamageScore() > 0.6) {
            compositionScore += 0.2;
            compositionReasons.add("Adds needed sustained damage");
        }
        
        // Check for save capability - important but not mandatory
        double saveNeed = 0.5 - teamScores.getOrDefault("save", 0.0);
        if (saveNeed > 0.3 && heroProfile.getSaveScore() > 0.6) {
            compositionScore += 0.15;
            compositionReasons.add("Adds ability to save teammates");
        }
        
        // Check for initiation - teams often need at least one initiator
        double initiationNeed = 0.6 - teamScores.getOrDefault("initiation", 0.0);
        if (initiationNeed > 0.3 && heroProfile.getInitiationScore() > 0.6) {
            compositionScore += 0.2;
            compositionReasons.add("Adds needed initiation capability");
        }
        
        // Log the composition analysis for debugging
        if (!compositionReasons.isEmpty()) {
            logger.debug("Composition benefits from {}: {}", hero.getLocalizedName(), 
                       String.join(", ", compositionReasons));
        }
        
        // Cap the score at 1.0
        return Math.min(compositionScore, 1.0);
    }
    
    /**
     * Analyze team composition by aggregating hero abilities
     * 
     * @param heroes The current team picks
     * @return Scores for different team composition aspects
     */
    private Map<String, Double> analyzeTeamComposition(List<Hero> heroes) {
        Map<String, Double> scores = new HashMap<>();
        
        // Initialize scores
        scores.put("teamfight", 0.0);
        scores.put("control", 0.0);
        scores.put("burstDamage", 0.0);
        scores.put("sustainedDamage", 0.0);
        scores.put("initiation", 0.0);
        scores.put("save", 0.0);
        scores.put("mobility", 0.0);
        scores.put("vision", 0.0);
        scores.put("bkbPierce", 0.0);
        
        for (Hero hero : heroes) {
            AbilityClassifier.HeroAbilityProfile profile = abilityClassifier.classifyHeroAbilities(hero);
            
            // Update scores based on hero strengths (using diminishing returns)
            updateScoreWithDiminishingReturns(scores, "teamfight", profile.getTeamfightScore());
            updateScoreWithDiminishingReturns(scores, "control", profile.getControlScore());
            updateScoreWithDiminishingReturns(scores, "burstDamage", profile.getBurstDamageScore());
            updateScoreWithDiminishingReturns(scores, "sustainedDamage", profile.getSustainedDamageScore());
            updateScoreWithDiminishingReturns(scores, "initiation", profile.getInitiationScore());
            updateScoreWithDiminishingReturns(scores, "save", profile.getSaveScore());
            updateScoreWithDiminishingReturns(scores, "mobility", profile.getMobilityScore());
            updateScoreWithDiminishingReturns(scores, "vision", profile.getVisionScore());
            updateScoreWithDiminishingReturns(scores, "bkbPierce", profile.getBkbPierceScore());
        }
        
        return scores;
    }
    
    /**
     * Update score with diminishing returns
     * This ensures that having 5 heroes with the same strength doesn't give a perfect score,
     * but rather diminishing value after adequate coverage
     */
    private void updateScoreWithDiminishingReturns(Map<String, Double> scores, String key, double value) {
        if (value < 0.3) {
            // Low values don't contribute significantly
            return;
        }
        
        double currentScore = scores.get(key);
        // Formula gives diminishing returns as the score approaches 1.0
        double updatedScore = currentScore + (1.0 - currentScore) * value * 0.5;
        scores.put(key, updatedScore);
    }
    
    /**
     * Collect reasons why this hero improves team composition balance
     * 
     * @param hero The hero to check
     * @param draftState The current draft state
     * @param team The team to check for
     * @return List of composition-based reasons
     */
    private List<String> collectCompositionReasons(Hero hero, DraftState draftState, Team team) {
        List<String> reasons = new ArrayList<>();
        List<Hero> teamPicks = team == Team.RADIANT ? 
                draftState.getRadiantPicks() : draftState.getDirePicks();
        
        if (teamPicks.isEmpty()) {
            // For first pick, focus on versatility and teamfight potential
            AbilityClassifier.HeroAbilityProfile profile = abilityClassifier.classifyHeroAbilities(hero);
            
            if (profile.hasSignificantTeamfight()) {
                reasons.add("Strong teamfight hero good for first pick");
            }
            
            if (profile.getStrengthsSummary().split(",").length >= 3) {
                reasons.add("Versatile hero with multiple strengths");
            }
            
            return reasons;
        }
        
        // Analyze current team composition
        Map<String, Double> teamScores = analyzeTeamComposition(teamPicks);
        
        // Analyze what this hero brings
        AbilityClassifier.HeroAbilityProfile heroProfile = abilityClassifier.classifyHeroAbilities(hero);
        
        // Check if the team needs teamfight capability
        if (teamScores.getOrDefault("teamfight", 0.0) < 0.5 && 
            heroProfile.hasSignificantTeamfight()) {
            reasons.add("Adds essential teamfight capability");
            
            // Add specific teamfight abilities
            if (!heroProfile.getTeamfightAbilities().isEmpty()) {
                String abilities = String.join(", ", heroProfile.getTeamfightAbilities());
                if (abilities.length() <= 50) {  // Keep reason concise
                    reasons.add("Key teamfight abilities: " + abilities);
                }
            }
        }
        
        // Check if the team needs control
        if (teamScores.getOrDefault("control", 0.0) < 0.5 && 
            heroProfile.hasSignificantControl()) {
            reasons.add("Adds needed control abilities");
        }
        
        // Check for damage type balance
        if (teamScores.getOrDefault("burstDamage", 0.0) < 0.5 && 
            heroProfile.getBurstDamageScore() > 0.6) {
            reasons.add("Adds burst damage to balance team output");
        }
        
        if (teamScores.getOrDefault("sustainedDamage", 0.0) < 0.5 && 
            heroProfile.getSustainedDamageScore() > 0.6) {
            reasons.add("Adds sustained damage for extended fights");
        }
        
        // Check for initiation
        if (teamScores.getOrDefault("initiation", 0.0) < 0.5 && 
            heroProfile.getInitiationScore() > 0.6) {
            reasons.add("Adds needed fight initiation capability");
        }
        
        // Check for save capability
        if (teamScores.getOrDefault("save", 0.0) < 0.4 && 
            heroProfile.getSaveScore() > 0.6) {
            reasons.add("Adds valuable defensive/save abilities");
        }
        
        // Check for BKB-piercing abilities (valuable in late game)
        if (teamScores.getOrDefault("bkbPierce", 0.0) < 0.4 && 
            heroProfile.getBkbPierceScore() > 0.6) {
            reasons.add("Adds BKB-piercing control for late game");
        }
        
        return reasons;
    }
}