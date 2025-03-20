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
import com.dota2assistant.data.db.SqliteDatabaseManager;
import com.dota2assistant.data.repository.DraftDataRepository;
import com.dota2assistant.data.repository.HeroAbilitiesRepository;
import com.dota2assistant.data.repository.HeroRepository;
import com.dota2assistant.data.repository.MatchRepository;
import com.dota2assistant.ui.controller.MainController;
import com.dota2assistant.util.PropertyLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
        return new OpenDotaApiClient(client, mapper);
    }

    @Bean
    @ConditionalOnProperty(name = "database.type", havingValue = "sqlite", matchIfMissing = true)
    public DatabaseManager sqliteDatabaseManager(PropertyLoader propertyLoader) {
        String dbFile = propertyLoader.getProperty("database.file", "dota2assistant.db");
        return new SqliteDatabaseManager(dbFile);
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
                                        ScheduledExecutorService scheduledExecutorService) {
        return new MainController(draftEngine, aiDecisionEngine, analysisEngine, 
                                 executorService, scheduledExecutorService);
    }

    @Bean
    public ExecutorService executorService() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }
    
    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(2);
    }
}