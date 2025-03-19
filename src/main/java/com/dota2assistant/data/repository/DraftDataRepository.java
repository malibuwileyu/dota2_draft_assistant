package com.dota2assistant.data.repository;

import com.dota2assistant.data.model.DraftData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Repository for accessing draft data from professional matches.
 */
public class DraftDataRepository {
    private static final Logger logger = LoggerFactory.getLogger(DraftDataRepository.class);
    private static final String DRAFTS_DIRECTORY = "/src/main/resources/data/matches/drafts";
    
    private final String basePath;
    private final ObjectMapper objectMapper;
    
    /**
     * Constructor that uses the default base path.
     */
    public DraftDataRepository() {
        this(System.getProperty("user.dir"));
    }
    
    /**
     * Constructor with a custom base path.
     * 
     * @param basePath The base path of the application
     */
    public DraftDataRepository(String basePath) {
        this.basePath = basePath;
        this.objectMapper = new ObjectMapper();
        
        // Configure ObjectMapper to handle snake_case property names
        objectMapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    /**
     * Get all available draft data.
     * 
     * @return List of DraftData objects
     */
    public List<DraftData> getAllDraftData() {
        String draftsPath = basePath + DRAFTS_DIRECTORY;
        File draftsDir = new File(draftsPath);
        
        if (!draftsDir.exists() || !draftsDir.isDirectory()) {
            logger.warn("Drafts directory does not exist: {}", draftsPath);
            return Collections.emptyList();
        }
        
        List<DraftData> draftDataList = new ArrayList<>();
        
        try {
            try (Stream<Path> paths = Files.walk(Paths.get(draftsPath))) {
                List<File> jsonFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .map(Path::toFile)
                        .collect(Collectors.toList());
                
                for (File file : jsonFiles) {
                    try {
                        DraftData draftData = objectMapper.readValue(file, DraftData.class);
                        draftDataList.add(draftData);
                    } catch (IOException e) {
                        logger.error("Error parsing draft data file: " + file.getPath(), e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error walking drafts directory", e);
        }
        
        logger.info("Loaded {} draft data files", draftDataList.size());
        return draftDataList;
    }
    
    /**
     * Get draft data for a specific match.
     * 
     * @param matchId The ID of the match
     * @return DraftData object or null if not found
     */
    public DraftData getDraftDataByMatchId(long matchId) {
        String draftFilePath = basePath + DRAFTS_DIRECTORY + "/draft_" + matchId + ".json";
        File draftFile = new File(draftFilePath);
        
        if (!draftFile.exists() || !draftFile.isFile()) {
            logger.warn("Draft data file not found for match ID: {}", matchId);
            return null;
        }
        
        try {
            return objectMapper.readValue(draftFile, DraftData.class);
        } catch (IOException e) {
            logger.error("Error parsing draft data file for match ID: " + matchId, e);
            return null;
        }
    }
    
    /**
     * Get the total number of available draft data files.
     * 
     * @return The count of draft data files
     */
    public int getDraftDataCount() {
        String draftsPath = basePath + DRAFTS_DIRECTORY;
        File draftsDir = new File(draftsPath);
        
        if (!draftsDir.exists() || !draftsDir.isDirectory()) {
            return 0;
        }
        
        try {
            try (Stream<Path> paths = Files.walk(Paths.get(draftsPath))) {
                return (int) paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .count();
            }
        } catch (IOException e) {
            logger.error("Error counting draft data files", e);
            return 0;
        }
    }
}