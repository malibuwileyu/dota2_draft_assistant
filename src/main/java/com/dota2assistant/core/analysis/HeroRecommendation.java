package com.dota2assistant.core.analysis;

import com.dota2assistant.data.model.Hero;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a hero recommendation with associated metrics.
 */
public class HeroRecommendation {
    private final Hero hero;
    private final double score;
    private final double winRate;
    private final double synergyScore;
    private final double counterScore;
    private final int pickCount;
    private List<String> synergyReasons = new ArrayList<>();
    private List<String> counterReasons = new ArrayList<>();
    
    /**
     * Constructs a new hero recommendation.
     *
     * @param hero The hero being recommended
     * @param score The overall recommendation score (0.0-1.0)
     * @param winRate The win rate of this hero (-1.0 if no data)
     * @param synergyScore How well this hero synergizes with current team picks (0.0-1.0)
     * @param counterScore How well this hero counters enemy picks (0.0-1.0)
     * @param pickCount Number of times this hero was picked in the analyzed data
     */
    public HeroRecommendation(Hero hero, double score, double winRate, double synergyScore, double counterScore, int pickCount) {
        this.hero = hero;
        this.score = score;
        this.winRate = winRate;
        this.synergyScore = synergyScore;
        this.counterScore = counterScore;
        this.pickCount = pickCount;
        this.synergyReasons = new ArrayList<>();
        this.counterReasons = new ArrayList<>();
    }
    
    public HeroRecommendation(Hero hero, double score, double winRate, double synergyScore, 
                            double counterScore, int pickCount,
                            List<String> synergyReasons, List<String> counterReasons) {
        this.hero = hero;
        this.score = score;
        this.winRate = winRate;
        this.synergyScore = synergyScore;
        this.counterScore = counterScore;
        this.pickCount = pickCount;
        this.synergyReasons = synergyReasons != null ? synergyReasons : new ArrayList<>();
        this.counterReasons = counterReasons != null ? counterReasons : new ArrayList<>();
    }
    
    /**
     * Get the hero being recommended.
     *
     * @return The hero
     */
    public Hero getHero() {
        return hero;
    }
    
    /**
     * Get the overall recommendation score.
     *
     * @return Score between 0.0 and 1.0
     */
    public double getScore() {
        return score;
    }
    
    /**
     * Get the win rate for this hero.
     *
     * @return Win rate between 0.0 and 1.0, or -1.0 if no data
     */
    public double getWinRate() {
        return winRate;
    }
    
    /**
     * Get the synergy score for this hero.
     *
     * @return Synergy score between 0.0 and 1.0
     */
    public double getSynergyScore() {
        return synergyScore;
    }
    
    /**
     * Get the counter score for this hero.
     *
     * @return Counter score between 0.0 and 1.0
     */
    public double getCounterScore() {
        return counterScore;
    }
    
    /**
     * Get the number of picks analyzed for this hero.
     *
     * @return Number of picks
     */
    public int getPickCount() {
        return pickCount;
    }
    
    /**
     * Get the synergy reasons for this recommendation.
     * 
     * @return List of synergy reasons
     */
    public List<String> getSynergyReasons() {
        return synergyReasons;
    }
    
    /**
     * Add a synergy reason.
     * 
     * @param reason The reason to add
     */
    public void addSynergyReason(String reason) {
        if (reason != null && !reason.isEmpty()) {
            this.synergyReasons.add(reason);
        }
    }
    
    /**
     * Get the counter reasons for this recommendation.
     * 
     * @return List of counter reasons
     */
    public List<String> getCounterReasons() {
        return counterReasons;
    }
    
    /**
     * Add a counter reason.
     * 
     * @param reason The reason to add
     */
    public void addCounterReason(String reason) {
        if (reason != null && !reason.isEmpty()) {
            this.counterReasons.add(reason);
        }
    }
    
    /**
     * Get the win rate as a formatted percentage string.
     *
     * @return Win rate as a percentage, or "No Data" if no data
     */
    public String getWinRateFormatted() {
        if (winRate < 0) {
            return "No Data";
        }
        return String.format("%.1f%%", winRate * 100);
    }
    
    /**
     * Get the formatted reasoning for this recommendation.
     *
     * @return A string describing why this hero is recommended
     */
    public String getReasoningFormatted() {
        StringBuilder reasoning = new StringBuilder();
        
        if (winRate >= 0) {
            // For high enough win rates, emphasize this is a strong hero
            if (winRate > 0.55) {
                reasoning.append(String.format("Strong hero with %.1f%% win rate. ", winRate * 100));
            } else {
                reasoning.append(String.format("Win rate: %.1f%%. ", winRate * 100));
            }
        }
        
        // If pick/ban count is significant, mention it's a meta pick
        if (pickCount >= 5) {
            reasoning.append(String.format("Popular in pro meta (%d matches). ", pickCount));
        }
        
        // Add specific synergy reasons if available
        if (!synergyReasons.isEmpty()) {
            // Select top 2 synergy reasons maximum
            int reasonCount = Math.min(2, synergyReasons.size());
            for (int i = 0; i < reasonCount; i++) {
                reasoning.append(synergyReasons.get(i)).append(". ");
            }
        }
        // Otherwise use generic synergy description based on score
        else if (synergyScore > 0.7) {
            reasoning.append("Strong synergy with current picks. ");
        } else if (synergyScore > 0.4) {
            reasoning.append("Good synergy with current picks. ");
        }
        
        // Add specific counter reasons if available
        if (!counterReasons.isEmpty()) {
            // Select top 2 counter reasons maximum
            int reasonCount = Math.min(2, counterReasons.size());
            for (int i = 0; i < reasonCount; i++) {
                reasoning.append(counterReasons.get(i)).append(". ");
            }
        }
        // Otherwise use generic counter description based on score
        else if (counterScore > 0.7) {
            reasoning.append("Strong counter to enemy picks. ");
        } else if (counterScore > 0.4) {
            reasoning.append("Good counter to enemy picks. ");
        }
        
        return reasoning.toString().trim();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HeroRecommendation that = (HeroRecommendation) o;
        return Double.compare(that.score, score) == 0 &&
               Double.compare(that.winRate, winRate) == 0 &&
               Double.compare(that.synergyScore, synergyScore) == 0 &&
               Double.compare(that.counterScore, counterScore) == 0 &&
               pickCount == that.pickCount &&
               Objects.equals(hero, that.hero) &&
               Objects.equals(synergyReasons, that.synergyReasons) &&
               Objects.equals(counterReasons, that.counterReasons);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(hero, score, winRate, synergyScore, counterScore, pickCount, 
                          synergyReasons, counterReasons);
    }
    
    @Override
    public String toString() {
        return "HeroRecommendation{" +
               "hero=" + hero.getName() +
               ", score=" + score +
               ", winRate=" + getWinRateFormatted() +
               ", synergyScore=" + synergyScore +
               ", counterScore=" + counterScore +
               ", pickCount=" + pickCount +
               '}';
    }
}