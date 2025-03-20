package com.dota2assistant.data.repository;

import com.dota2assistant.data.api.DotaApiClient;
import com.dota2assistant.data.db.DatabaseManager;
import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.model.HeroAttributes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HeroRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(HeroRepository.class);
    
    private final DatabaseManager dbManager;
    private final DotaApiClient apiClient;
    private final Map<Integer, Hero> heroCache;
    private final HeroAbilitiesRepository heroAbilitiesRepository;
    
    public HeroRepository(DatabaseManager dbManager, DotaApiClient apiClient) {
        this(dbManager, apiClient, null);
    }
    
    public HeroRepository(DatabaseManager dbManager, DotaApiClient apiClient, HeroAbilitiesRepository heroAbilitiesRepository) {
        this.dbManager = dbManager;
        this.apiClient = apiClient;
        this.heroAbilitiesRepository = heroAbilitiesRepository;
        this.heroCache = new ConcurrentHashMap<>();
        
        try {
            initDatabase();
        } catch (SQLException e) {
            logger.error("Failed to initialize hero repository database tables", e);
        }
    }
    
    private void initDatabase() throws SQLException {
        dbManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS heroes (" +
                "id INTEGER PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "localized_name TEXT NOT NULL, " +
                "primary_attr TEXT, " +
                "attack_type TEXT, " +
                "image_path TEXT, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
        );
        
        dbManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS hero_roles (" +
                "hero_id INTEGER, " +
                "role TEXT NOT NULL, " +
                "PRIMARY KEY (hero_id, role), " +
                "FOREIGN KEY (hero_id) REFERENCES heroes(id)" +
                ")"
        );
        
        dbManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS hero_role_frequency (" +
                "hero_id INTEGER, " +
                "position INTEGER, " +
                "frequency REAL, " +
                "PRIMARY KEY (hero_id, position), " +
                "FOREIGN KEY (hero_id) REFERENCES heroes(id)" +
                ")"
        );
        
        dbManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS hero_attributes (" +
                "hero_id INTEGER PRIMARY KEY, " +
                "base_str REAL, " +
                "base_agi REAL, " +
                "base_int REAL, " +
                "str_gain REAL, " +
                "agi_gain REAL, " +
                "int_gain REAL, " +
                "move_speed INTEGER, " +
                "armor REAL, " +
                "attack_min REAL, " +
                "attack_max REAL, " +
                "attack_rate REAL, " +
                "attack_range INTEGER, " +
                "FOREIGN KEY (hero_id) REFERENCES heroes(id)" +
                ")"
        );
        
        dbManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS hero_abilities (" +
                "id INTEGER PRIMARY KEY, " +
                "hero_id INTEGER, " +
                "name TEXT NOT NULL, " +
                "description TEXT, " +
                "type TEXT, " +
                "behavior TEXT, " +
                "damage_type TEXT, " +
                "FOREIGN KEY (hero_id) REFERENCES heroes(id)" +
                ")"
        );
        
        dbManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS hero_synergies (" +
                "hero1_id INTEGER, " +
                "hero2_id INTEGER, " +
                "synergy_score REAL, " +
                "games INTEGER, " +
                "wins INTEGER, " +
                "PRIMARY KEY (hero1_id, hero2_id), " +
                "FOREIGN KEY (hero1_id) REFERENCES heroes(id), " +
                "FOREIGN KEY (hero2_id) REFERENCES heroes(id)" +
                ")"
        );
        
        dbManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS hero_counters (" +
                "hero_id INTEGER, " +
                "counter_id INTEGER, " +
                "counter_score REAL, " +
                "games INTEGER, " +
                "wins INTEGER, " +
                "PRIMARY KEY (hero_id, counter_id), " +
                "FOREIGN KEY (hero_id) REFERENCES heroes(id), " +
                "FOREIGN KEY (counter_id) REFERENCES heroes(id)" +
                ")"
        );
    }
    
    /**
     * Loads heroes from local resources (JSON file)
     * @return List of heroes loaded from resources
     */
    public List<Hero> loadHeroesFromLocalResources() {
        List<Hero> heroes = new ArrayList<>();
        
        // Check if heroes.json exists in various paths
        String[] possiblePaths = {
            "/data/heroes.json",  // This is the correct path for resources
            "data/heroes.json",   // Alternative path format
            "/heroes.json",       // Root level check
            "heroes.json"         // Simple filename check
        };
        
        InputStream inputStream = null;
        String foundPath = null;
        
        // Try different paths
        for (String path : possiblePaths) {
            logger.info("Attempting to load heroes from: {}", path);
            // First try the direct class loader approach
            inputStream = getClass().getResourceAsStream(path);
            
            // If that fails, try the context class loader
            if (inputStream == null) {
                inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            }
            
            if (inputStream != null) {
                foundPath = path;
                logger.info("Found heroes.json at path: {}", path);
                break;
            }
        }
        
        if (inputStream == null) {
            logger.error("Could not find heroes.json in any of the attempted paths");
            logger.info("Current working directory: {}", System.getProperty("user.dir"));
            logger.info("Classpath: {}", System.getProperty("java.class.path"));
            
            // Emergency fallback - create some mock hero data
            logger.warn("Creating mock hero data for testing");
            return createMockHeroes(20);  // Create 20 mock heroes
        }
        
        logger.info("Successfully opened heroes.json from {}", foundPath);
        
        // Use Jackson to parse the JSON
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode;
        
        try {
            // Read the entire file content into a byte array to avoid stream closure issues
            byte[] jsonData = inputStream.readAllBytes();
            inputStream.close();
            
            rootNode = mapper.readTree(jsonData);
            logger.info("Successfully parsed JSON with {} bytes", jsonData.length);
            if (!rootNode.isArray()) {
                logger.error("Expected heroes.json to contain an array, but found: {}", rootNode.getNodeType());
                return createMockHeroes(20);
            }
            logger.info("Found {} heroes in JSON array", rootNode.size());
            
            // Process each hero node
            for (JsonNode node : rootNode) {
                try {
                    if (!node.has("id") || !node.has("name") || !node.has("localized_name")) {
                        logger.warn("Skipping hero node without required fields: {}", node);
                        continue;
                    }
                    
                    Hero hero = new Hero();
                    
                    hero.setId(node.get("id").asInt());
                    hero.setName(node.get("name").asText());
                    hero.setLocalizedName(node.get("localized_name").asText());
                    
                    // Special logging for Dawnbreaker
                    if (hero.getName().contains("dawn") || 
                        (node.has("id") && node.get("id").asInt() == 135)) {
                        logger.error("FOUND DAWNBREAKER IN JSON - ID: {}, Name: {}", 
                                    hero.getId(), hero.getName());
                        if (node.has("img")) {
                            logger.error("DAWNBREAKER IMAGE PATH: {}", node.get("img").asText());
                        }
                    }
                    
                    if (node.has("primary_attr")) {
                        hero.setPrimaryAttribute(node.get("primary_attr").asText());
                    }
                    
                    if (node.has("attack_type")) {
                        hero.setAttackType(node.get("attack_type").asText());
                    }
                    
                    // Set the image URL to local resource path
                    if (node.has("img")) {
                        // Convert the image URL format from /images/heroes/1_anti_mage.png 
                        // to a format that works with our resources
                        String imgPath = node.get("img").asText();
                        
                        // Check if the file actually exists in our resources
                        InputStream testStream = getClass().getResourceAsStream(imgPath);
                        if (testStream == null) {
                            // File doesn't exist, use placeholder based on attribute
                            logger.warn("Image file not found for {}: {}, using placeholder", 
                                        hero.getLocalizedName(), imgPath);
                            imgPath = "/images/heroes/placeholder_" + hero.getPrimaryAttribute() + ".png";
                        } else {
                            try {
                                testStream.close();
                            } catch (IOException e) {
                                logger.warn("Error closing test stream for {}", imgPath);
                            }
                        }
                        
                        if (!imgPath.startsWith("/images/heroes/")) {
                            // If it doesn't have the expected format, use attribute-based placeholder
                            imgPath = "/images/heroes/placeholder_" + hero.getPrimaryAttribute() + ".png";
                        }
                        hero.setImageUrl(imgPath);
                        logger.debug("Using image path: {}", imgPath);
                    } else if (node.has("icon")) {
                        // Fallback to icon if img is not available
                        String iconPath = node.get("icon").asText();
                        hero.setImageUrl(iconPath);
                        logger.debug("Using icon path: {}", iconPath);
                    } else {
                        // If neither img nor icon is available, use a placeholder based on attribute
                        String placeholderPath = "/images/heroes/placeholder_" + hero.getPrimaryAttribute() + ".png";
                        hero.setImageUrl(placeholderPath);
                        logger.debug("Using placeholder path: {}", placeholderPath);
                    }
                    
                    // Log the image path for debugging
                    logger.debug("Hero {}: image path = {}", hero.getLocalizedName(), hero.getImageUrl());
                    
                    // Add roles
                    if (node.has("roles") && node.get("roles").isArray()) {
                        ArrayNode rolesNode = (ArrayNode) node.get("roles");
                        for (JsonNode roleNode : rolesNode) {
                            hero.addRole(roleNode.asText());
                        }
                    }
                    
                    // Create hero attributes
                    HeroAttributes attributes = new HeroAttributes();
                    if (node.has("base_str")) attributes.setBaseStrength(node.get("base_str").asDouble());
                    if (node.has("base_agi")) attributes.setBaseAgility(node.get("base_agi").asDouble());
                    if (node.has("base_int")) attributes.setBaseIntelligence(node.get("base_int").asDouble());
                    if (node.has("str_gain")) attributes.setStrengthGain(node.get("str_gain").asDouble());
                    if (node.has("agi_gain")) attributes.setAgilityGain(node.get("agi_gain").asDouble());
                    if (node.has("int_gain")) attributes.setIntelligenceGain(node.get("int_gain").asDouble());
                    if (node.has("move_speed")) attributes.setMoveSpeed(node.get("move_speed").asInt());
                    if (node.has("attack_range")) attributes.setAttackRange(node.get("attack_range").asInt());
                    if (node.has("attack_rate")) attributes.setAttackRate(node.get("attack_rate").asDouble());
                    if (node.has("base_attack_min")) attributes.setAttackDamageMin(node.get("base_attack_min").asDouble());
                    if (node.has("base_attack_max")) attributes.setAttackDamageMax(node.get("base_attack_max").asDouble());
                    if (node.has("base_armor")) attributes.setArmor(node.get("base_armor").asDouble());
                    
                    hero.setAttributes(attributes);
                    heroes.add(hero);
                } catch (Exception e) {
                    logger.error("Error processing hero node: {}", e.getMessage(), e);
                }
            }
            
            logger.info("Successfully loaded {} heroes from local resources", heroes.size());
        } catch (Exception e) {
            logger.error("Error loading heroes from local resources: {}", e.getMessage(), e);
        }
        
        if (heroes.isEmpty()) {
            logger.warn("No heroes were loaded from hero.json - creating mock heroes");
            return createMockHeroes(20);
        }
        
        return heroes;
    }
    
    /**
     * Creates a set of mock heroes for testing when local resources are not available
     * @param count The number of heroes to create
     * @return A list of mock heroes
     */
    private List<Hero> createMockHeroes(int count) {
        logger.info("Creating {} mock heroes for testing", count);
        List<Hero> heroes = new ArrayList<>();
        
        // Include these heroes in the mock set
        String[] names = {
            "Anti-Mage", "Axe", "Crystal Maiden", "Drow Ranger", "Earthshaker", 
            "Juggernaut", "Mirana", "Morphling", "Shadow Fiend", "Phantom Lancer",
            "Puck", "Pudge", "Razor", "Sand King", "Storm Spirit",
            "Sven", "Tiny", "Vengeful Spirit", "Windranger", "Zeus"
        };
        
        String[] attributes = {
            "agi", "str", "int", "agi", "str", 
            "agi", "agi", "agi", "agi", "agi",
            "int", "str", "agi", "str", "int",
            "str", "str", "agi", "int", "int"
        };
        
        // Ensure we don't exceed the available names
        int heroCount = Math.min(count, names.length);
        
        for (int i = 0; i < heroCount; i++) {
            Hero hero = new Hero(i + 1, names[i].toLowerCase().replace(" ", "_"), names[i]);
            hero.setPrimaryAttribute(attributes[i]);
            
            // For local development, use placeholder images that are bundled with the app
            hero.setImageUrl("/images/heroes/placeholder_" + attributes[i] + ".png");
            
            // Add roles based on attribute
            if (attributes[i].equals("str")) {
                hero.addRole("Tank");
                hero.addRole("Initiator");
            } else if (attributes[i].equals("agi")) {
                hero.addRole("Carry");
                hero.addRole("Escape");
            } else {
                hero.addRole("Support");
                hero.addRole("Nuker");
            }
            
            heroes.add(hero);
        }
        
        logger.info("Successfully created {} mock heroes", heroes.size());
        return heroes;
    }

    public List<Hero> getAllHeroes() {
        List<Hero> heroes = new ArrayList<>();
        
        // First try to load from cache
        if (!heroCache.isEmpty()) {
            heroes.addAll(heroCache.values());
            logger.debug("Loaded {} heroes from cache", heroes.size());
            return heroes;
        }
        
        logger.debug("Cache is empty, attempting to load heroes from database");
        
        // Try to load from database first
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM heroes")) {
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Hero hero = new Hero(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("localized_name")
                );
                hero.setPrimaryAttribute(rs.getString("primary_attr"));
                hero.setAttackType(rs.getString("attack_type"));
                hero.setImageUrl(rs.getString("image_path"));
                
                loadHeroRoles(conn, hero);
                loadHeroRoleFrequency(conn, hero);
                loadHeroAttributes(conn, hero);
                loadHeroAbilities(conn, hero);
                loadHeroSynergies(conn, hero);
                loadHeroCounters(conn, hero);
                
                heroes.add(hero);
                heroCache.put(hero.getId(), hero);
                logger.debug("Loaded hero from database: {} ({})", hero.getLocalizedName(), hero.getId());
            }
            
            if (!heroes.isEmpty()) {
                logger.info("Loaded {} heroes from database", heroes.size());
                
                // Enhance heroes with ability data if available
                if (heroAbilitiesRepository != null) {
                    logger.debug("Enhancing heroes with ability data from HeroAbilitiesRepository");
                    heroAbilitiesRepository.enhanceHeroesWithAbilities(heroes);
                }
                
                return heroes;
            }
        } catch (SQLException e) {
            logger.error("Failed to load heroes from database: {}", e.getMessage(), e);
        }
        
        logger.info("No heroes loaded from database, trying local resources");
        
        // Try loading from local resources as fallback
        heroes = loadHeroesFromLocalResources();
        if (!heroes.isEmpty()) {
            logger.info("Successfully loaded {} heroes from local resources", heroes.size());
            
            // Enhance heroes with ability data if available
            if (heroAbilitiesRepository != null) {
                logger.debug("Enhancing heroes with ability data from HeroAbilitiesRepository");
                heroAbilitiesRepository.enhanceHeroesWithAbilities(heroes);
            }
            
            // Cache the heroes for future use
            for (Hero hero : heroes) {
                heroCache.put(hero.getId(), hero);
                logger.debug("Cached hero: {} ({})", hero.getLocalizedName(), hero.getId());
            }
            return heroes;
        }
        
        logger.info("No heroes loaded from local resources or database");
        
        // Try to load from database as fallback
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM heroes")) {
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Hero hero = new Hero(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("localized_name")
                );
                hero.setPrimaryAttribute(rs.getString("primary_attr"));
                hero.setAttackType(rs.getString("attack_type"));
                hero.setImageUrl(rs.getString("image_path"));
                
                loadHeroRoles(conn, hero);
                loadHeroRoleFrequency(conn, hero);
                loadHeroAttributes(conn, hero);
                loadHeroAbilities(conn, hero);
                loadHeroSynergies(conn, hero);
                loadHeroCounters(conn, hero);
                
                heroes.add(hero);
                heroCache.put(hero.getId(), hero);
                logger.debug("Loaded hero from database: {} ({})", hero.getLocalizedName(), hero.getId());
            }
            logger.info("Loaded {} heroes from database", heroes.size());
        } catch (SQLException e) {
            logger.error("Failed to load heroes from database: {}", e.getMessage(), e);
        }
        
        // If all else fails, try to load from API as last resort
        if (heroes.isEmpty()) {
            logger.info("No heroes loaded from database, attempting to load from API");
            try {
                heroes = apiClient.fetchHeroes();
                for (Hero hero : heroes) {
                    saveHero(hero);
                    heroCache.put(hero.getId(), hero);
                    logger.debug("Loaded and cached hero from API: {} ({})", hero.getLocalizedName(), hero.getId());
                }
                logger.info("Successfully loaded {} heroes from API", heroes.size());
            } catch (IOException e) {
                logger.error("Failed to fetch heroes from API: {}", e.getMessage(), e);
            }
        }
        
        if (heroes.isEmpty()) {
            logger.error("Failed to load any heroes from any source! Creating mock heroes as last resort");
            heroes = createMockHeroes(20);
            for (Hero hero : heroes) {
                heroCache.put(hero.getId(), hero);
                logger.debug("Created mock hero: {} ({})", hero.getLocalizedName(), hero.getId());
            }
        }
        
        return heroes;
    }
    
    public Hero getHeroById(int id) {
        // First try to load from cache
        if (heroCache.containsKey(id)) {
            return heroCache.get(id);
        }
        
        // Try to load from database
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM heroes WHERE id = ?")) {
            
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                Hero hero = new Hero(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("localized_name")
                );
                hero.setPrimaryAttribute(rs.getString("primary_attr"));
                hero.setAttackType(rs.getString("attack_type"));
                hero.setImageUrl(rs.getString("image_path"));
                
                loadHeroRoles(conn, hero);
                loadHeroRoleFrequency(conn, hero);
                loadHeroAttributes(conn, hero);
                loadHeroAbilities(conn, hero);
                loadHeroSynergies(conn, hero);
                loadHeroCounters(conn, hero);
                
                heroCache.put(id, hero);
                return hero;
            }
        } catch (SQLException e) {
            logger.error("Failed to load hero from database", e);
        }
        
        // Try to load from API
        try {
            Hero hero = apiClient.fetchHeroDetails(id);
            if (hero != null) {
                saveHero(hero);
                heroCache.put(id, hero);
                return hero;
            }
        } catch (IOException e) {
            logger.error("Failed to fetch hero from API", e);
        }
        
        return null;
    }
    
    public void saveHero(Hero hero) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO heroes (id, name, localized_name, primary_attr, attack_type, image_path) " +
                     "VALUES (?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (id) DO UPDATE SET " +
                     "name = EXCLUDED.name, localized_name = EXCLUDED.localized_name, " +
                     "primary_attr = EXCLUDED.primary_attr, attack_type = EXCLUDED.attack_type, " +
                     "image_path = EXCLUDED.image_path")) {
            
            stmt.setInt(1, hero.getId());
            stmt.setString(2, hero.getName());
            stmt.setString(3, hero.getLocalizedName());
            stmt.setString(4, hero.getPrimaryAttribute());
            stmt.setString(5, hero.getAttackType());
            stmt.setString(6, hero.getImageUrl());
            stmt.executeUpdate();
            
            saveHeroRoles(conn, hero);
            saveHeroRoleFrequency(conn, hero);
            saveHeroAttributes(conn, hero);
            saveHeroAbilities(conn, hero);
            saveHeroSynergies(conn, hero);
            saveHeroCounters(conn, hero);
            
        } catch (SQLException e) {
            logger.error("Failed to save hero to database", e);
        }
    }
    
    public Map<Integer, Double> getHeroWinRates() {
        try {
            return apiClient.fetchHeroWinRates();
        } catch (IOException e) {
            logger.error("Failed to fetch hero win rates", e);
            return Map.of();
        }
    }
    
    public Map<Integer, Double> getHeroPickRates() {
        try {
            return apiClient.fetchHeroPickRates();
        } catch (IOException e) {
            logger.error("Failed to fetch hero pick rates", e);
            return Map.of();
        }
    }
    
    public void refreshHeroData() {
        try {
            List<Hero> heroes = apiClient.fetchHeroes();
            for (Hero hero : heroes) {
                saveHero(hero);
                heroCache.put(hero.getId(), hero);
            }
            
            Map<String, Double> synergies = apiClient.fetchHeroSynergies();
            saveSynergies(synergies);
            
            Map<String, Double> counters = apiClient.fetchHeroCounters();
            saveCounters(counters);
        } catch (IOException e) {
            logger.error("Failed to refresh hero data", e);
        }
    }
    
    private void loadHeroRoles(Connection conn, Hero hero) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT role FROM hero_roles WHERE hero_id = ?")) {
            
            stmt.setInt(1, hero.getId());
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                hero.addRole(rs.getString("role"));
            }
        }
    }
    
    private void saveHeroRoles(Connection conn, Hero hero) throws SQLException {
        try (PreparedStatement deleteStmt = conn.prepareStatement(
                "DELETE FROM hero_roles WHERE hero_id = ?")) {
            
            deleteStmt.setInt(1, hero.getId());
            deleteStmt.executeUpdate();
        }
        
        if (hero.getRoles() == null || hero.getRoles().isEmpty()) {
            return;
        }
        
        try (PreparedStatement insertStmt = conn.prepareStatement(
                "INSERT INTO hero_roles (hero_id, role) VALUES (?, ?)")) {
            
            for (String role : hero.getRoles()) {
                insertStmt.setInt(1, hero.getId());
                insertStmt.setString(2, role);
                insertStmt.executeUpdate();
            }
        }
    }
    
    private void loadHeroRoleFrequency(Connection conn, Hero hero) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT position, frequency FROM hero_role_frequency WHERE hero_id = ?")) {
            
            stmt.setInt(1, hero.getId());
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                hero.addRoleFrequency(rs.getInt("position"), rs.getDouble("frequency"));
            }
        }
    }
    
    private void saveHeroRoleFrequency(Connection conn, Hero hero) throws SQLException {
        try (PreparedStatement deleteStmt = conn.prepareStatement(
                "DELETE FROM hero_role_frequency WHERE hero_id = ?")) {
            
            deleteStmt.setInt(1, hero.getId());
            deleteStmt.executeUpdate();
        }
        
        if (hero.getRoleFrequency() == null || hero.getRoleFrequency().isEmpty()) {
            return;
        }
        
        try (PreparedStatement insertStmt = conn.prepareStatement(
                "INSERT INTO hero_role_frequency (hero_id, position, frequency) VALUES (?, ?, ?)")) {
            
            for (Map.Entry<Integer, Double> entry : hero.getRoleFrequency().entrySet()) {
                insertStmt.setInt(1, hero.getId());
                insertStmt.setInt(2, entry.getKey());
                insertStmt.setDouble(3, entry.getValue());
                insertStmt.executeUpdate();
            }
        }
    }
    
    private void loadHeroAttributes(Connection conn, Hero hero) throws SQLException {
        // Implementation left as an exercise
    }
    
    private void saveHeroAttributes(Connection conn, Hero hero) throws SQLException {
        // Implementation left as an exercise
    }
    
    private void loadHeroAbilities(Connection conn, Hero hero) throws SQLException {
        // Implementation left as an exercise
    }
    
    private void saveHeroAbilities(Connection conn, Hero hero) throws SQLException {
        // Implementation left as an exercise
    }
    
    private void loadHeroSynergies(Connection conn, Hero hero) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT hero2_id, synergy_score FROM hero_synergies WHERE hero1_id = ?")) {
            
            stmt.setInt(1, hero.getId());
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                hero.addSynergy(rs.getInt("hero2_id"), rs.getDouble("synergy_score"));
            }
        }
    }
    
    private void saveHeroSynergies(Connection conn, Hero hero) throws SQLException {
        try (PreparedStatement deleteStmt = conn.prepareStatement(
                "DELETE FROM hero_synergies WHERE hero1_id = ?")) {
            
            deleteStmt.setInt(1, hero.getId());
            deleteStmt.executeUpdate();
        }
        
        if (hero.getSynergies() == null || hero.getSynergies().isEmpty()) {
            return;
        }
        
        try (PreparedStatement insertStmt = conn.prepareStatement(
                "INSERT INTO hero_synergies (hero1_id, hero2_id, synergy_score, games, wins) VALUES (?, ?, ?, 10, 5)")) {
            
            for (Map.Entry<Integer, Double> entry : hero.getSynergies().entrySet()) {
                insertStmt.setInt(1, hero.getId());
                insertStmt.setInt(2, entry.getKey());
                insertStmt.setDouble(3, entry.getValue());
                insertStmt.executeUpdate();
            }
        }
    }
    
    private void loadHeroCounters(Connection conn, Hero hero) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT counter_id, counter_score FROM hero_counters WHERE hero_id = ?")) {
            
            stmt.setInt(1, hero.getId());
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                hero.addCounter(rs.getInt("counter_id"), rs.getDouble("counter_score"));
            }
        }
    }
    
    private void saveHeroCounters(Connection conn, Hero hero) throws SQLException {
        try (PreparedStatement deleteStmt = conn.prepareStatement(
                "DELETE FROM hero_counters WHERE hero_id = ?")) {
            
            deleteStmt.setInt(1, hero.getId());
            deleteStmt.executeUpdate();
        }
        
        if (hero.getCounters() == null || hero.getCounters().isEmpty()) {
            return;
        }
        
        try (PreparedStatement insertStmt = conn.prepareStatement(
                "INSERT INTO hero_counters (hero_id, counter_id, counter_score) VALUES (?, ?, ?)")) {
            
            for (Map.Entry<Integer, Double> entry : hero.getCounters().entrySet()) {
                insertStmt.setInt(1, hero.getId());
                insertStmt.setInt(2, entry.getKey());
                insertStmt.setDouble(3, entry.getValue());
                insertStmt.executeUpdate();
            }
        }
    }
    
    private void saveSynergies(Map<String, Double> synergies) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO hero_synergies (hero1_id, hero2_id, synergy_score, games, wins) VALUES (?, ?, ?, 10, 5) " +
                     "ON CONFLICT (hero1_id, hero2_id) DO UPDATE SET synergy_score = EXCLUDED.synergy_score")) {
            
            for (Map.Entry<String, Double> entry : synergies.entrySet()) {
                String[] ids = entry.getKey().split("_");
                int heroId1 = Integer.parseInt(ids[0]);
                int heroId2 = Integer.parseInt(ids[1]);
                double score = entry.getValue();
                
                stmt.setInt(1, heroId1);
                stmt.setInt(2, heroId2);
                stmt.setDouble(3, score);
                stmt.executeUpdate();
                
                // Update cache if heroes are loaded
                if (heroCache.containsKey(heroId1)) {
                    heroCache.get(heroId1).addSynergy(heroId2, score);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save hero synergies", e);
        }
    }
    
    private void saveCounters(Map<String, Double> counters) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO hero_counters (hero_id, counter_id, counter_score, games, wins) VALUES (?, ?, ?, 10, 5) " +
                     "ON CONFLICT (hero_id, counter_id) DO UPDATE SET counter_score = EXCLUDED.counter_score")) {
            
            for (Map.Entry<String, Double> entry : counters.entrySet()) {
                String[] ids = entry.getKey().split("_");
                int heroId1 = Integer.parseInt(ids[0]);
                int heroId2 = Integer.parseInt(ids[1]);
                double score = entry.getValue();
                
                stmt.setInt(1, heroId1);
                stmt.setInt(2, heroId2);
                stmt.setDouble(3, score);
                stmt.executeUpdate();
                
                // Update cache if heroes are loaded
                if (heroCache.containsKey(heroId1)) {
                    heroCache.get(heroId1).addCounter(heroId2, score);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save hero counters", e);
        }
    }
}