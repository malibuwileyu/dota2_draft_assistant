package com.dota2assistant.core.ai;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.data.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for training and managing machine learning models for draft recommendations.
 * This handles the training pipeline, model storage, and inference operations.
 */
@Service
public class MlTrainingService {

    private static final Logger logger = LoggerFactory.getLogger(MlTrainingService.class);
    
    private final HeroRepository heroRepository;
    private final MatchRepository matchRepository;
    private final NlpModelIntegration nlpModel;
    
    // Directory for storing model data
    private static final String MODEL_DIR = "data/models";
    
    // Model data
    private Map<String, Map<Integer, Double>> heroWinRates = new ConcurrentHashMap<>();
    private Map<String, Double> heroSynergies = new ConcurrentHashMap<>();
    private Map<String, Double> heroCounters = new ConcurrentHashMap<>();
    private Map<Integer, double[]> heroEmbeddings = new ConcurrentHashMap<>();

    @Autowired
    public MlTrainingService(HeroRepository heroRepository, MatchRepository matchRepository, NlpModelIntegration nlpModel) {
        this.heroRepository = heroRepository;
        this.matchRepository = matchRepository;
        this.nlpModel = nlpModel;
    }

    @PostConstruct
    public void initialize() {
        // Create model directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(MODEL_DIR));
        } catch (IOException e) {
            logger.error("Failed to create model directory: {}", e.getMessage());
        }
        
        // Load existing models if available
        loadModels();
        
        // Train or update models if needed
        if (heroWinRates.isEmpty() || heroSynergies.isEmpty() || heroCounters.isEmpty()) {
            logger.info("No existing models found. Training new models...");
            trainModels();
        } else {
            logger.info("Models loaded successfully");
        }
    }
    
    /**
     * Load models from disk
     */
    @SuppressWarnings("unchecked")
    private void loadModels() {
        try {
            // Load win rates
            Path winRatesPath = Paths.get(MODEL_DIR, "hero_win_rates.ser");
            if (Files.exists(winRatesPath)) {
                try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(winRatesPath))) {
                    heroWinRates = (Map<String, Map<Integer, Double>>) ois.readObject();
                    logger.info("Loaded hero win rates model with {} records", 
                        heroWinRates.values().stream().mapToInt(Map::size).sum());
                }
            }
            
            // Load synergies
            Path synergiesPath = Paths.get(MODEL_DIR, "hero_synergies.ser");
            if (Files.exists(synergiesPath)) {
                try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(synergiesPath))) {
                    heroSynergies = (Map<String, Double>) ois.readObject();
                    logger.info("Loaded hero synergies model with {} records", heroSynergies.size());
                }
            }
            
            // Load counters
            Path countersPath = Paths.get(MODEL_DIR, "hero_counters.ser");
            if (Files.exists(countersPath)) {
                try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(countersPath))) {
                    heroCounters = (Map<String, Double>) ois.readObject();
                    logger.info("Loaded hero counters model with {} records", heroCounters.size());
                }
            }
            
            // Load embeddings
            Path embeddingsPath = Paths.get(MODEL_DIR, "hero_embeddings.ser");
            if (Files.exists(embeddingsPath)) {
                try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(embeddingsPath))) {
                    heroEmbeddings = (Map<Integer, double[]>) ois.readObject();
                    logger.info("Loaded hero embeddings model with {} records", heroEmbeddings.size());
                }
            }
            
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Error loading models: {}", e.getMessage());
        }
    }
    
    /**
     * Save models to disk
     */
    private void saveModels() {
        try {
            // Save win rates
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    Files.newOutputStream(Paths.get(MODEL_DIR, "hero_win_rates.ser")))) {
                oos.writeObject(heroWinRates);
            }
            
            // Save synergies
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    Files.newOutputStream(Paths.get(MODEL_DIR, "hero_synergies.ser")))) {
                oos.writeObject(heroSynergies);
            }
            
            // Save counters
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    Files.newOutputStream(Paths.get(MODEL_DIR, "hero_counters.ser")))) {
                oos.writeObject(heroCounters);
            }
            
            // Save embeddings
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    Files.newOutputStream(Paths.get(MODEL_DIR, "hero_embeddings.ser")))) {
                oos.writeObject(heroEmbeddings);
            }
            
            logger.info("Models saved successfully");
            
        } catch (IOException e) {
            logger.error("Error saving models: {}", e.getMessage());
        }
    }

    /**
     * Train machine learning models using match data
     */
    public void trainModels() {
        logger.info("Starting model training...");
        
        // 1. Train win rates model
        trainWinRatesModel();
        
        // 2. Train synergies model
        trainSynergiesModel();
        
        // 3. Train counters model
        trainCountersModel();
        
        // 4. Generate hero embeddings
        generateHeroEmbeddings();
        
        // Save the trained models
        saveModels();
        
        logger.info("Model training completed");
    }
    
    /**
     * Generate win rates for heroes from match data
     */
    private void trainWinRatesModel() {
        logger.info("Training hero win rates model...");
        Map<String, Map<Integer, Double>> winRates = new HashMap<>();
        Map<String, Map<Integer, Integer>> winCounts = new HashMap<>();
        Map<String, Map<Integer, Integer>> totalCounts = new HashMap<>();
        
        // Ranks to track
        List<String> ranks = Arrays.asList("all", "herald", "guardian", "crusader", 
                                         "archon", "legend", "ancient", "divine", "immortal");
        
        // Initialize counters for each rank and hero
        for (String rank : ranks) {
            winCounts.put(rank, new HashMap<>());
            totalCounts.put(rank, new HashMap<>());
            
            for (Hero hero : heroRepository.getAllHeroes()) {
                winCounts.get(rank).put(hero.getId(), 0);
                totalCounts.get(rank).put(hero.getId(), 0);
            }
        }
        
        // Process match data to count wins and picks
        // In a real implementation, this would process all match data from the database
        // For simulation, we'll create synthetic data
        simulateMatchData(winCounts, totalCounts);
        
        // Calculate win rates for each hero and rank
        for (String rank : ranks) {
            Map<Integer, Double> rankWinRates = new HashMap<>();
            Map<Integer, Integer> rankWinCount = winCounts.get(rank);
            Map<Integer, Integer> rankTotalCount = totalCounts.get(rank);
            
            for (Integer heroId : rankWinCount.keySet()) {
                int wins = rankWinCount.get(heroId);
                int total = rankTotalCount.get(heroId);
                
                // Calculate win rate with minimum game threshold
                if (total >= 10) {
                    rankWinRates.put(heroId, (double) wins / total);
                } else {
                    // Not enough data, use global average
                    rankWinRates.put(heroId, 0.5);  // Default to 50%
                }
            }
            
            // Store win rates for this rank
            winRates.put(rank, rankWinRates);
        }
        
        this.heroWinRates = winRates;
        logger.info("Hero win rates model trained with {} records", 
            winRates.values().stream().mapToInt(Map::size).sum());
    }
    
    /**
     * Generate synergy scores between hero pairs
     */
    private void trainSynergiesModel() {
        logger.info("Training hero synergies model...");
        Map<String, Double> synergies = new HashMap<>();
        
        // Process match data to compute synergies
        // In a real implementation, this would analyze co-occurrence and win rates
        // For simulation, we'll use NLP-based ability analysis
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        for (int i = 0; i < allHeroes.size(); i++) {
            Hero hero1 = allHeroes.get(i);
            
            for (int j = i + 1; j < allHeroes.size(); j++) {
                Hero hero2 = allHeroes.get(j);
                
                // Compute synergy score based on ability interactions and similarity
                double synergyScore = nlpModel.getHeroSimilarity(hero1.getId(), hero2.getId());
                
                // Add bonus for strong ability synergies
                List<NlpAbilityAnalyzer.AbilitySynergy> abilitySynergies = 
                    nlpModel.findAbilitySynergies(hero1.getId(), hero2.getId());
                
                if (!abilitySynergies.isEmpty()) {
                    double maxSynergyScore = abilitySynergies.stream()
                        .mapToDouble(NlpAbilityAnalyzer.AbilitySynergy::getScore)
                        .max()
                        .orElse(0.0);
                    
                    // Combine scores with equal weight
                    synergyScore = (synergyScore + maxSynergyScore) / 2.0;
                }
                
                // Store the synergy score using a consistent key format: smaller_id_larger_id
                String key = Math.min(hero1.getId(), hero2.getId()) + "_" + Math.max(hero1.getId(), hero2.getId());
                synergies.put(key, synergyScore);
            }
        }
        
        this.heroSynergies = synergies;
        logger.info("Hero synergies model trained with {} records", synergies.size());
    }
    
    /**
     * Generate counter scores between hero pairs
     */
    private void trainCountersModel() {
        logger.info("Training hero counters model...");
        Map<String, Double> counters = new HashMap<>();
        
        // Process match data to compute counter relationships
        // In a real implementation, this would analyze win rates when heroes face each other
        // For simulation, we'll use synthetic data and NLP-based ability analysis
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        // For each hero pair, compute how well hero1 counters hero2
        for (Hero hero1 : allHeroes) {
            for (Hero hero2 : allHeroes) {
                if (hero1.getId() == hero2.getId()) {
                    continue;  // Skip same hero
                }
                
                // Get hero feature vectors
                Map<String, Double> vector1 = nlpModel.getHeroFeatureVector(hero1.getId());
                Map<String, Double> vector2 = nlpModel.getHeroFeatureVector(hero2.getId());
                
                // Compute counter score based on ability characteristics
                double counterScore = 0.5;  // Default
                
                // Example heuristics:
                // 1. Control heroes counter mobility heroes
                if (vector1.getOrDefault("stun_score", 0.0) > 0.5 && vector2.getOrDefault("mobility_score", 0.0) > 0.7) {
                    counterScore += 0.2;
                }
                
                // 2. Silence counters spell-reliant heroes
                if (vector1.getOrDefault("silence_score", 0.0) > 0.5 && vector2.getOrDefault("magical_damage", 0.0) > 0.7) {
                    counterScore += 0.2;
                }
                
                // 3. Mobility counters low-mobility heroes
                if (vector1.getOrDefault("mobility_score", 0.0) > 0.7 && vector2.getOrDefault("mobility_score", 0.0) < 0.3) {
                    counterScore += 0.15;
                }
                
                // 4. Pure damage counters high-armor heroes
                if (vector1.getOrDefault("pure_damage", 0.0) > 0.7 && hero2.getLocalizedName().toLowerCase().contains("armor")) {
                    counterScore += 0.15;
                }
                
                // Cap the score
                counterScore = Math.min(1.0, counterScore);
                
                // Store the counter relationship: hero1_id_hero2_id
                // This indicates how well hero1 counters hero2
                String key = hero1.getId() + "_" + hero2.getId();
                counters.put(key, counterScore);
            }
        }
        
        this.heroCounters = counters;
        logger.info("Hero counters model trained with {} records", counters.size());
    }
    
    /**
     * Generate embedding vectors for heroes based on their abilities
     */
    private void generateHeroEmbeddings() {
        logger.info("Generating hero embeddings...");
        Map<Integer, double[]> embeddings = new HashMap<>();
        
        // Get all heroes
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        // Generate embedding for each hero
        for (Hero hero : allHeroes) {
            // Get hero feature vector
            Map<String, Double> featureVector = nlpModel.getHeroFeatureVector(hero.getId());
            
            // Convert feature vector to fixed-size embedding
            // This is a simplified approach - real embeddings would be more sophisticated
            double[] embedding = new double[12];
            
            embedding[0] = featureVector.getOrDefault("stun_score", 0.0);
            embedding[1] = featureVector.getOrDefault("silence_score", 0.0);
            embedding[2] = featureVector.getOrDefault("root_score", 0.0);
            embedding[3] = featureVector.getOrDefault("slow_score", 0.0);
            embedding[4] = featureVector.getOrDefault("magical_damage", 0.0);
            embedding[5] = featureVector.getOrDefault("physical_damage", 0.0);
            embedding[6] = featureVector.getOrDefault("pure_damage", 0.0);
            embedding[7] = featureVector.getOrDefault("aoe_impact", 0.0);
            embedding[8] = featureVector.getOrDefault("control_duration", 0.0);
            embedding[9] = featureVector.getOrDefault("mobility_score", 0.0);
            embedding[10] = featureVector.getOrDefault("sustain_score", 0.0);
            embedding[11] = featureVector.getOrDefault("utility_score", 0.0);
            
            // Store the embedding
            embeddings.put(hero.getId(), embedding);
        }
        
        this.heroEmbeddings = embeddings;
        logger.info("Hero embeddings generated for {} heroes", embeddings.size());
    }
    
    /**
     * Creates synthetic match data for demonstration purposes
     */
    private void simulateMatchData(Map<String, Map<Integer, Integer>> winCounts, Map<String, Map<Integer, Integer>> totalCounts) {
        Random random = new Random(42);  // Fixed seed for reproducibility
        
        // Get all heroes
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        
        // Simulate 10,000 matches
        for (int i = 0; i < 10000; i++) {
            // Pick a random rank
            String rank = "all";  // Default
            double rankRoll = random.nextDouble();
            if (rankRoll < 0.2) rank = "herald";
            else if (rankRoll < 0.4) rank = "guardian";
            else if (rankRoll < 0.55) rank = "crusader";
            else if (rankRoll < 0.7) rank = "archon";
            else if (rankRoll < 0.82) rank = "legend";
            else if (rankRoll < 0.9) rank = "ancient";
            else if (rankRoll < 0.97) rank = "divine";
            else rank = "immortal";
            
            // Pick 10 random heroes (5 per team)
            List<Hero> pickPool = new ArrayList<>(allHeroes);
            Collections.shuffle(pickPool, random);
            
            List<Hero> radiantTeam = pickPool.subList(0, 5);
            List<Hero> direTeam = pickPool.subList(5, 10);
            
            // Determine the winner
            boolean radiantWins = random.nextDouble() < 0.5;
            
            // Update statistics
            List<Hero> winners = radiantWins ? radiantTeam : direTeam;
            List<Hero> losers = radiantWins ? direTeam : radiantTeam;
            
            // Update win counts
            for (Hero hero : winners) {
                // Update for 'all' rank
                winCounts.get("all").put(hero.getId(), winCounts.get("all").get(hero.getId()) + 1);
                totalCounts.get("all").put(hero.getId(), totalCounts.get("all").get(hero.getId()) + 1);
                
                // Update for specific rank
                winCounts.get(rank).put(hero.getId(), winCounts.get(rank).get(hero.getId()) + 1);
                totalCounts.get(rank).put(hero.getId(), totalCounts.get(rank).get(hero.getId()) + 1);
            }
            
            // Update total counts for losers
            for (Hero hero : losers) {
                // Update for 'all' rank
                totalCounts.get("all").put(hero.getId(), totalCounts.get("all").get(hero.getId()) + 1);
                
                // Update for specific rank
                totalCounts.get(rank).put(hero.getId(), totalCounts.get(rank).get(hero.getId()) + 1);
            }
        }
        
        logger.debug("Generated synthetic match data for training");
    }
    
    /**
     * Get the trained hero win rates
     * @return Map of rank to hero ID to win rate
     */
    public Map<String, Map<Integer, Double>> getHeroWinRates() {
        return heroWinRates;
    }
    
    /**
     * Get the trained hero synergies
     * @return Map of hero pair key to synergy score
     */
    public Map<String, Double> getHeroSynergies() {
        return heroSynergies;
    }
    
    /**
     * Get the trained hero counters
     * @return Map of hero pair key to counter score
     */
    public Map<String, Double> getHeroCounters() {
        return heroCounters;
    }
    
    /**
     * Get the hero embeddings
     * @return Map of hero ID to embedding vector
     */
    public Map<Integer, double[]> getHeroEmbeddings() {
        return heroEmbeddings;
    }
    
    /**
     * Find heroes with similar embeddings to the given hero ID
     * @param heroId Hero to find similar heroes for
     * @param count Number of similar heroes to return
     * @return List of hero IDs sorted by similarity
     */
    public List<Integer> findSimilarHeroes(int heroId, int count) {
        // Get the target hero's embedding
        double[] targetEmbedding = heroEmbeddings.get(heroId);
        if (targetEmbedding == null) {
            return Collections.emptyList();
        }
        
        // Calculate similarity to all other heroes
        Map<Integer, Double> similarities = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : heroEmbeddings.entrySet()) {
            int otherHeroId = entry.getKey();
            if (otherHeroId == heroId) {
                continue;  // Skip the target hero
            }
            
            double[] otherEmbedding = entry.getValue();
            double similarity = cosineSimilarity(targetEmbedding, otherEmbedding);
            similarities.put(otherHeroId, similarity);
        }
        
        // Sort by similarity and return top N
        return similarities.entrySet().stream()
            .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
            .limit(count)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    private double cosineSimilarity(double[] vector1, double[] vector2) {
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }
        
        // Handle potential division by zero
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}