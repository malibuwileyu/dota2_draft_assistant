package com.dota2assistant;

import com.dota2assistant.core.ai.AiDecisionEngine;
import com.dota2assistant.core.ai.DefaultAiDecisionEngine;
import com.dota2assistant.core.ai.ProMatchDataAiDecisionEngine;
import com.dota2assistant.core.analysis.AnalysisEngine;
import com.dota2assistant.core.analysis.DefaultAnalysisEngine;
import com.dota2assistant.core.analysis.DraftAnalysisService;
import com.dota2assistant.core.draft.CaptainsModeDraftEngine;
import com.dota2assistant.core.draft.DraftEngine;
import com.dota2assistant.data.api.DotaApiClient;
import com.dota2assistant.data.api.OpenDotaApiClient;
import com.dota2assistant.data.db.DatabaseManager;
import com.dota2assistant.data.repository.DraftDataRepository;
import com.dota2assistant.data.repository.HeroAbilitiesRepository;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.data.repository.MatchRepository;
import com.dota2assistant.ui.controller.MainController;
import com.dota2assistant.util.PropertyLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spring application configuration class.
 */
@Configuration
@ComponentScan(basePackages = "com.dota2assistant")
@PropertySource("classpath:application.properties")
@PropertySource(value = "file:./application.properties.override", ignoreResourceNotFound = true)
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public PropertyLoader propertyLoader() {
        return new PropertyLoader();
    }

    @Bean
    public OkHttpClient okHttpClient(PropertyLoader propertyLoader) {
        int timeout = propertyLoader.getIntProperty("opendota.api.timeout", 30);
        return new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .build();
    }
    
    @Bean
    public static org.springframework.context.support.PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Bean
    public DotaApiClient dotaApiClient(OkHttpClient client, ObjectMapper mapper, PropertyLoader propertyLoader) {
        String baseUrl = propertyLoader.getProperty("opendota.api.baseUrl", "https://api.opendota.com/api");
        // Load API key from properties
        String apiKey = propertyLoader.getProperty("opendota.api.key", "");
        
        if (apiKey != null && !apiKey.isEmpty()) {
            logger.info("Initializing OpenDota API client with API key");
        } else {
            logger.warn("No OpenDota API key found in configuration, API rate limits will apply");
        }
        
        return new OpenDotaApiClient(client, mapper, apiKey);
    }

    // Hold static references to the injected services for components that can't use DI
    private static DatabaseManager staticDatabaseManager;
    private static com.dota2assistant.data.service.MatchHistoryService staticMatchHistoryService;
    private static com.dota2assistant.data.service.AutomatedMatchSyncService staticAutomatedSyncService;
    private static com.dota2assistant.data.service.MatchEnrichmentService staticMatchEnrichmentService;
    private static com.dota2assistant.data.service.DatabaseMigrationService staticDatabaseMigrationService;
    
    /**
     * Static access to the database manager for services that can't use DI.
     * This method will return the PostgreSQL database manager instance.
     *
     * @return the database manager instance
     */
    public static DatabaseManager getDatabaseManager() {
        if (staticDatabaseManager == null) {
            throw new IllegalStateException("Database manager not initialized. Application context may not be ready.");
        }
        return staticDatabaseManager;
    }
    
    /**
     * Static access to the MatchHistoryService for services that can't use DI.
     *
     * @return the MatchHistoryService instance
     */
    public static com.dota2assistant.data.service.MatchHistoryService getMatchHistoryService() {
        if (staticMatchHistoryService == null) {
            throw new IllegalStateException("MatchHistoryService not initialized. Application context may not be ready.");
        }
        return staticMatchHistoryService;
    }
    
    /**
     * Static access to the AutomatedMatchSyncService for services that can't use DI.
     *
     * @return the AutomatedMatchSyncService instance
     */
    public static com.dota2assistant.data.service.AutomatedMatchSyncService getAutomatedSyncService() {
        if (staticAutomatedSyncService == null) {
            throw new IllegalStateException("AutomatedMatchSyncService not initialized. Application context may not be ready.");
        }
        return staticAutomatedSyncService;
    }
    
    /**
     * Static access to the MatchEnrichmentService for services that can't use DI.
     *
     * @return the MatchEnrichmentService instance
     */
    public static com.dota2assistant.data.service.MatchEnrichmentService getMatchEnrichmentService() {
        if (staticMatchEnrichmentService == null) {
            throw new IllegalStateException("MatchEnrichmentService not initialized. Application context may not be ready.");
        }
        return staticMatchEnrichmentService;
    }
    
    /**
     * Static access to the DatabaseMigrationService for services that can't use DI.
     *
     * @return the DatabaseMigrationService instance
     */
    public static com.dota2assistant.data.service.DatabaseMigrationService getDatabaseMigrationService() {
        if (staticDatabaseMigrationService == null) {
            throw new IllegalStateException("DatabaseMigrationService not initialized. Application context may not be ready.");
        }
        return staticDatabaseMigrationService;
    }
    
    /**
     * Set the database manager instance - used by Spring to inject the proper instance
     * @param databaseManager The Spring-managed database manager instance
     */
    @Bean
    public DatabaseManagerInitializer databaseManagerInitializer(DatabaseManager databaseManager) {
        staticDatabaseManager = databaseManager;
        return new DatabaseManagerInitializer(databaseManager);
    }
    
    /**
     * Set the MatchHistoryService instance - used by Spring to inject the proper instance
     * @param matchHistoryService The Spring-managed MatchHistoryService instance
     */
    @Bean
    public MatchHistoryServiceInitializer matchHistoryServiceInitializer(
            com.dota2assistant.data.service.MatchHistoryService matchHistoryService) {
        staticMatchHistoryService = matchHistoryService;
        return new MatchHistoryServiceInitializer(matchHistoryService);
    }
    
    /**
     * Set the AutomatedMatchSyncService instance - used by Spring to inject the proper instance
     * @param automatedSyncService The Spring-managed AutomatedMatchSyncService instance
     */
    @Bean
    public AutomatedSyncServiceInitializer automatedSyncServiceInitializer(
            com.dota2assistant.data.service.AutomatedMatchSyncService automatedSyncService) {
        staticAutomatedSyncService = automatedSyncService;
        return new AutomatedSyncServiceInitializer(automatedSyncService);
    }
    
    /**
     * Simple class to help initialize the static database manager
     */
    public static class DatabaseManagerInitializer {
        private final DatabaseManager databaseManager;
        
        public DatabaseManagerInitializer(DatabaseManager databaseManager) {
            this.databaseManager = databaseManager;
        }
    }
    
    /**
     * Simple class to help initialize the static match history service
     */
    public static class MatchHistoryServiceInitializer {
        private final com.dota2assistant.data.service.MatchHistoryService matchHistoryService;
        
        public MatchHistoryServiceInitializer(com.dota2assistant.data.service.MatchHistoryService matchHistoryService) {
            this.matchHistoryService = matchHistoryService;
        }
    }
    
    /**
     * Simple class to help initialize the static automated sync service
     */
    public static class AutomatedSyncServiceInitializer {
        private final com.dota2assistant.data.service.AutomatedMatchSyncService automatedSyncService;
        
        public AutomatedSyncServiceInitializer(com.dota2assistant.data.service.AutomatedMatchSyncService automatedSyncService) {
            this.automatedSyncService = automatedSyncService;
        }
    }
    
    /**
     * Set the MatchEnrichmentService instance - used by Spring to inject the proper instance
     * @param matchEnrichmentService The Spring-managed MatchEnrichmentService instance
     */
    @Bean
    public MatchEnrichmentServiceInitializer matchEnrichmentServiceInitializer(
            com.dota2assistant.data.service.MatchEnrichmentService matchEnrichmentService) {
        staticMatchEnrichmentService = matchEnrichmentService;
        return new MatchEnrichmentServiceInitializer(matchEnrichmentService);
    }
    
    /**
     * Simple class to help initialize the static match enrichment service
     */
    public static class MatchEnrichmentServiceInitializer {
        private final com.dota2assistant.data.service.MatchEnrichmentService matchEnrichmentService;
        
        public MatchEnrichmentServiceInitializer(com.dota2assistant.data.service.MatchEnrichmentService matchEnrichmentService) {
            this.matchEnrichmentService = matchEnrichmentService;
        }
    }
    
    /**
     * Set the DatabaseMigrationService instance - used by Spring to inject the proper instance
     * @param databaseMigrationService The Spring-managed DatabaseMigrationService instance
     */
    @Bean
    public DatabaseMigrationServiceInitializer databaseMigrationServiceInitializer(
            com.dota2assistant.data.service.DatabaseMigrationService databaseMigrationService) {
        staticDatabaseMigrationService = databaseMigrationService;
        return new DatabaseMigrationServiceInitializer(databaseMigrationService);
    }
    
    /**
     * Simple class to help initialize the static database migration service
     */
    public static class DatabaseMigrationServiceInitializer {
        private final com.dota2assistant.data.service.DatabaseMigrationService databaseMigrationService;
        
        public DatabaseMigrationServiceInitializer(com.dota2assistant.data.service.DatabaseMigrationService databaseMigrationService) {
            this.databaseMigrationService = databaseMigrationService;
        }
    }

    @Bean
    public HeroRepository heroRepository(DatabaseManager databaseManager, DotaApiClient apiClient, HeroAbilitiesRepository heroAbilitiesRepository) {
        return new HeroRepository(databaseManager, apiClient, heroAbilitiesRepository);
    }

    @Bean
    public MatchRepository matchRepository(DatabaseManager databaseManager, DotaApiClient apiClient) {
        return new MatchRepository(databaseManager, apiClient);
    }
    
    @Bean
    public com.dota2assistant.data.repository.UserMatchRepository userMatchRepository(
            DatabaseManager databaseManager, 
            HeroRepository heroRepository) {
        return new com.dota2assistant.data.repository.UserMatchRepository(databaseManager, heroRepository);
    }

    @Bean
    public DraftEngine draftEngine(HeroRepository heroRepository, PropertyLoader propertyLoader) {
        return new CaptainsModeDraftEngine(heroRepository);
    }

    @Bean
    public DraftDataRepository draftDataRepository() {
        return new DraftDataRepository();
    }
    
    @Bean
    public HeroAbilitiesRepository heroAbilitiesRepository(ObjectMapper objectMapper) {
        return new HeroAbilitiesRepository(objectMapper);
    }
    
    @Bean
    public DraftAnalysisService draftAnalysisService(DraftDataRepository draftDataRepository, 
                                                   HeroRepository heroRepository) {
        return new DraftAnalysisService(draftDataRepository, heroRepository);
    }

    @Bean
    public DefaultAiDecisionEngine defaultAiDecisionEngine(HeroRepository heroRepository, 
                                                         MatchRepository matchRepository,
                                                         PropertyLoader propertyLoader) {
        DefaultAiDecisionEngine engine = new DefaultAiDecisionEngine(heroRepository, matchRepository);
        
        // Set properties from configuration
        double difficulty = propertyLoader.getDoubleProperty("default.difficulty", 0.8);
        String rank = propertyLoader.getProperty("default.rank", MatchRepository.RANK_LEGEND);
        
        engine.setDifficultyLevel(difficulty);
        engine.setCurrentRank(rank);
        
        return engine;
    }
    
    @Bean
    @Primary
    public AiDecisionEngine proMatchDataAiDecisionEngine(HeroRepository heroRepository,
                                                       DraftAnalysisService draftAnalysisService,
                                                       PropertyLoader propertyLoader) {
        ProMatchDataAiDecisionEngine engine = new ProMatchDataAiDecisionEngine(heroRepository, draftAnalysisService);
        
        // Set properties from configuration
        double difficulty = propertyLoader.getDoubleProperty("default.difficulty", 0.8);
        engine.setDifficultyLevel(difficulty);
        
        return engine;
    }

    @Bean
    public AnalysisEngine analysisEngine(HeroRepository heroRepository, 
                                        MatchRepository matchRepository,
                                        PropertyLoader propertyLoader) {
        DefaultAnalysisEngine engine = new DefaultAnalysisEngine(heroRepository, matchRepository);
        
        // Set properties from configuration
        String rank = propertyLoader.getProperty("default.rank", MatchRepository.RANK_LEGEND);
        engine.setCurrentRank(rank);
        
        return engine;
    }

    @Bean
    public MainController mainController(DraftEngine draftEngine, 
                                        AiDecisionEngine aiDecisionEngine, 
                                        AnalysisEngine analysisEngine,
                                        ExecutorService executorService,
                                        ScheduledExecutorService scheduledExecutorService,
                                        PropertyLoader propertyLoader) {
        return new MainController(draftEngine, aiDecisionEngine, analysisEngine, 
                                 executorService, scheduledExecutorService, propertyLoader);
    }

    @Bean
    public ExecutorService executorService() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }
    
    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(2);
    }
    
    /**
     * Auth-related beans 
     */
    @Bean
    public com.dota2assistant.auth.AuthCallbackServer authCallbackServer() {
        return new com.dota2assistant.auth.AuthCallbackServer();
    }
    
    @Bean
    public com.dota2assistant.auth.SteamApiService steamApiService(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            PropertyLoader propertyLoader,
            com.dota2assistant.auth.SteamAuthenticationManager steamAuthManager
    ) {
        return new com.dota2assistant.auth.SteamApiService(httpClient, objectMapper, propertyLoader, steamAuthManager);
    }
    
    @Bean
    public com.dota2assistant.auth.SteamAuthenticationManager steamAuthenticationManager(
            OkHttpClient httpClient,
            PropertyLoader propertyLoader
    ) {
        return new com.dota2assistant.auth.SteamAuthenticationManager(httpClient, propertyLoader);
    }
    
    @Bean
    public com.dota2assistant.data.repository.UserRepository userRepository(
            DatabaseManager databaseManager,
            ObjectMapper objectMapper
    ) {
        return new com.dota2assistant.data.repository.UserRepository(databaseManager, objectMapper);
    }
    
    @Bean
    public com.dota2assistant.auth.UserService userService(
            com.dota2assistant.auth.SteamApiService steamApiService,
            com.dota2assistant.auth.SteamAuthenticationManager steamAuthenticationManager,
            com.dota2assistant.data.repository.UserRepository userRepository
    ) {
        return new com.dota2assistant.auth.UserService(steamApiService, steamAuthenticationManager, userRepository);
    }
    
    @Bean
    public com.dota2assistant.ui.controller.LoginController loginController(
            com.dota2assistant.auth.UserService userService,
            com.dota2assistant.auth.AuthCallbackServer authCallbackServer
    ) {
        return new com.dota2assistant.ui.controller.LoginController(userService, authCallbackServer);
    }
    
    @Bean
    public com.dota2assistant.ui.controller.UserStatusController userStatusController(
            com.dota2assistant.auth.UserService userService
    ) {
        return new com.dota2assistant.ui.controller.UserStatusController(userService);
    }
    
    /**
     * Player match history and recommendation services
     */
    @Bean
    public com.dota2assistant.data.service.AutomatedMatchSyncService automatedMatchSyncService(
            com.dota2assistant.data.repository.UserMatchRepository userMatchRepository, 
            DatabaseManager databaseManager,
            com.dota2assistant.data.service.MatchHistoryService matchHistoryService,
            PropertyLoader propertyLoader
    ) {
        return new com.dota2assistant.data.service.AutomatedMatchSyncService(
            userMatchRepository, databaseManager, matchHistoryService, propertyLoader);
    }
    
    @Bean
    public com.dota2assistant.data.service.UserMatchService userMatchService(
            com.dota2assistant.data.repository.UserMatchRepository userMatchRepository,
            HeroRepository heroRepository,
            com.dota2assistant.auth.SteamApiService steamApiService,
            com.dota2assistant.data.service.MatchHistoryService matchHistoryService,
            com.dota2assistant.data.service.AutomatedMatchSyncService automatedMatchSyncService
    ) {
        return new com.dota2assistant.data.service.UserMatchService(
            userMatchRepository, heroRepository, steamApiService, 
            matchHistoryService, automatedMatchSyncService);
    }
    
    @Bean
    public com.dota2assistant.data.service.MatchEnrichmentService matchEnrichmentService(
            DotaApiClient apiClient,
            DatabaseManager databaseManager,
            PropertyLoader propertyLoader
    ) {
        return new com.dota2assistant.data.service.MatchEnrichmentService(apiClient, databaseManager, propertyLoader);
    }
    
    @Bean
    public com.dota2assistant.data.service.MatchHistoryService matchHistoryService(
            com.dota2assistant.data.repository.UserMatchRepository userMatchRepository,
            DatabaseManager databaseManager,
            PropertyLoader propertyLoader,
            com.dota2assistant.data.service.MatchEnrichmentService matchEnrichmentService
    ) {
        return new com.dota2assistant.data.service.MatchHistoryService(
            userMatchRepository, databaseManager, propertyLoader, matchEnrichmentService);
    }
    
    @Bean
    public com.dota2assistant.data.service.PlayerRecommendationService playerRecommendationService(
            com.dota2assistant.data.repository.UserMatchRepository userMatchRepository,
            HeroRepository heroRepository,
            DatabaseManager databaseManager
    ) {
        return new com.dota2assistant.data.service.PlayerRecommendationService(userMatchRepository, heroRepository, databaseManager);
    }
    
    /**
     * Admin monitoring controller for system statistics and diagnostics
     */
    @Bean
    public com.dota2assistant.ui.controller.AdminMonitoringController adminMonitoringController() {
        return new com.dota2assistant.ui.controller.AdminMonitoringController();
    }
}