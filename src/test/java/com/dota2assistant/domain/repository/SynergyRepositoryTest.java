package com.dota2assistant.domain.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Synergy Repository Default Methods")
class SynergyRepositoryTest {
    
    private SynergyRepository repository;
    
    @BeforeEach
    void setUp() {
        // Create a test implementation that provides known data
        repository = new TestSynergyRepository();
    }
    
    @Test
    @DisplayName("calculateAverageSynergy returns 0.5 for empty allies list")
    void calculateAverageSynergy_emptyAllies_returnsFifty() {
        double result = repository.calculateAverageSynergy(1, List.of());
        
        assertThat(result).isEqualTo(0.5);
    }
    
    @Test
    @DisplayName("calculateAverageSynergy computes average correctly")
    void calculateAverageSynergy_withAllies_computesAverage() {
        // Test hero 1 has synergy 0.8 with hero 2, 0.6 with hero 3
        double result = repository.calculateAverageSynergy(1, List.of(2, 3));
        
        assertThat(result).isEqualTo(0.7); // (0.8 + 0.6) / 2
    }
    
    @Test
    @DisplayName("calculateAverageSynergy returns 0.5 when no data found")
    void calculateAverageSynergy_noData_returnsDefault() {
        // Hero 99 has no synergy data
        double result = repository.calculateAverageSynergy(99, List.of(100, 101));
        
        assertThat(result).isEqualTo(0.5);
    }
    
    @Test
    @DisplayName("calculateAverageSynergy ignores missing pairs")
    void calculateAverageSynergy_partialData_averagesAvailable() {
        // Hero 1 has data with hero 2 but not with hero 99
        double result = repository.calculateAverageSynergy(1, List.of(2, 99));
        
        assertThat(result).isEqualTo(0.8); // Only hero 2's synergy (0.8)
    }
    
    @Test
    @DisplayName("calculateAverageCounter returns 0.5 for empty enemies list")
    void calculateAverageCounter_emptyEnemies_returnsFifty() {
        double result = repository.calculateAverageCounter(1, List.of());
        
        assertThat(result).isEqualTo(0.5);
    }
    
    @Test
    @DisplayName("calculateAverageCounter computes average correctly")
    void calculateAverageCounter_withEnemies_computesAverage() {
        // Test hero 1 counters hero 4 with 0.7, hero 5 with 0.3
        double result = repository.calculateAverageCounter(1, List.of(4, 5));
        
        assertThat(result).isEqualTo(0.5); // (0.7 + 0.3) / 2
    }
    
    @Test
    @DisplayName("calculateAverageCounter returns 0.5 when no data found")
    void calculateAverageCounter_noData_returnsDefault() {
        double result = repository.calculateAverageCounter(99, List.of(100, 101));
        
        assertThat(result).isEqualTo(0.5);
    }
    
    @Test
    @DisplayName("calculateAverageCounter ignores missing pairs")
    void calculateAverageCounter_partialData_averagesAvailable() {
        // Hero 1 has data against hero 4 but not against hero 99
        double result = repository.calculateAverageCounter(1, List.of(4, 99));
        
        assertThat(result).isEqualTo(0.7); // Only hero 4's counter (0.7)
    }
    
    /**
     * Test implementation with known synergy/counter data.
     */
    private static class TestSynergyRepository implements SynergyRepository {
        
        // Synergy data: hero 1 with hero 2 = 0.8, hero 1 with hero 3 = 0.6
        private final Map<String, Double> synergies = Map.of(
            "1-2", 0.8, "2-1", 0.8,
            "1-3", 0.6, "3-1", 0.6
        );
        
        // Counter data: hero 1 vs hero 4 = 0.7, hero 1 vs hero 5 = 0.3
        private final Map<String, Double> counters = Map.of(
            "1-4", 0.7,
            "1-5", 0.3
        );
        
        @Override
        public Optional<Double> getSynergyScore(int heroId, int allyId) {
            String key = heroId + "-" + allyId;
            return Optional.ofNullable(synergies.get(key));
        }
        
        @Override
        public Optional<Double> getCounterScore(int heroId, int enemyId) {
            String key = heroId + "-" + enemyId;
            return Optional.ofNullable(counters.get(key));
        }
        
        @Override
        public Map<Integer, Double> getAllSynergies(int heroId) {
            return Map.of(); // Not needed for default method tests
        }
        
        @Override
        public Map<Integer, Double> getAllCounters(int heroId) {
            return Map.of(); // Not needed for default method tests
        }
        
        @Override
        public List<Integer> getBestSynergies(int heroId, int limit) {
            return List.of(); // Not needed for default method tests
        }
        
        @Override
        public List<Integer> getBestCounters(int heroId, int limit) {
            return List.of(); // Not needed for default method tests
        }
        
        @Override
        public List<Integer> getCounteredBy(int heroId, int limit) {
            return List.of(); // Not needed for default method tests
        }
    }
}

