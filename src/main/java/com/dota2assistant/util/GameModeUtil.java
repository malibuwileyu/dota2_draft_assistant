package com.dota2assistant.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for handling Dota 2 game modes.
 */
public class GameModeUtil {
    
    // Game mode constants
    public static final int UNKNOWN = 0;
    public static final int UNRANKED = 1;
    public static final int RANKED = 2;
    public static final int TURBO = 3;
    
    // Maps for conversion between IDs and names
    private static final Map<Integer, String> modeNames = new HashMap<>();
    private static final Map<String, Integer> modeIds = new HashMap<>();
    
    static {
        // Initialize mappings
        modeNames.put(UNKNOWN, "Unknown");
        modeNames.put(UNRANKED, "Unranked");
        modeNames.put(RANKED, "Ranked");
        modeNames.put(TURBO, "Turbo");
        
        modeIds.put("unknown", UNKNOWN);
        modeIds.put("unranked", UNRANKED);
        modeIds.put("ranked", RANKED);
        modeIds.put("turbo", TURBO);
    }
    
    /**
     * Converts a game mode string to its corresponding integer ID.
     * 
     * @param gameModeString The game mode string to convert
     * @return The corresponding game mode ID (0 for unknown)
     */
    public static int getGameModeId(String gameModeString) {
        if (gameModeString == null) {
            return UNKNOWN;
        }
        
        String lowerCase = gameModeString.toLowerCase();
        
        if (lowerCase.contains("ranked")) {
            return RANKED;
        } else if (lowerCase.contains("turbo")) {
            return TURBO;
        } else if (lowerCase.contains("all pick") || 
                  lowerCase.contains("captains") ||
                  lowerCase.contains("ability") ||
                  lowerCase.contains("random") ||
                  lowerCase.contains("single draft")) {
            return UNRANKED;
        }
        
        try {
            // Try to parse it as an integer
            int modeId = Integer.parseInt(gameModeString);
            if (modeId >= 0 && modeId <= 3) {
                return modeId;
            }
            return UNKNOWN;
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }
    }
    
    /**
     * Gets the game mode name for a given mode ID.
     * 
     * @param gameModeId The game mode ID
     * @return The game mode name
     */
    public static String getGameModeName(int gameModeId) {
        return modeNames.getOrDefault(gameModeId, "Unknown");
    }
    
    /**
     * Formats a game mode string for display.
     * If it's one of our known modes, returns the proper name.
     * Otherwise returns the original string.
     * 
     * @param gameModeString The game mode string
     * @return Formatted game mode string
     */
    public static String formatGameMode(String gameModeString) {
        if (gameModeString == null) {
            return "Unknown";
        }
        
        int modeId = getGameModeId(gameModeString);
        String modeName = getGameModeName(modeId);
        
        // If it's one of our known modes, return it
        if (modeId != UNKNOWN) {
            return modeName;
        }
        
        // Otherwise return the original string
        return gameModeString;
    }
}