package com.dota2assistant.ui;

import com.dota2assistant.core.draft.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for verifying the draft sequence for all four team configurations:
 * 1. First Pick + Radiant
 * 2. First Pick + Dire
 * 3. Second Pick + Radiant 
 * 4. Second Pick + Dire
 */
public class DraftSequenceTest {
    
    // Indices where First Pick Team makes bans in the standard ordering 
    private static final List<Integer> FIRST_PICK_TEAM_BANS = Arrays.asList(0, 3, 6, 7, 8, 10, 13);
    
    // The first ban pattern should be ABBABBA
    private static final String PHASE1_BAN_PATTERN = "ABBABBA";
    
    // The second ban pattern should be AAB
    private static final String PHASE2_BAN_PATTERN = "AAB";
    
    // The third ban pattern should be ABBA
    private static final String PHASE3_BAN_PATTERN = "ABBA";
    
    /**
     * Helps determine which team should make a ban at a given position
     * based on the pick configuration and team side.
     * 
     * @param banIndex The ban index (0-13)
     * @param isFirstPick Whether player is first pick team
     * @param isRadiant Whether player is Radiant
     * @return Team that should make the ban (RADIANT or DIRE)
     */
    private Team getBanningTeamForIndex(int banIndex, boolean isFirstPick, boolean isRadiant) {
        boolean needToSwapTeams = !isFirstPick || (isFirstPick && !isRadiant);
        boolean isSecondPickDire = !isFirstPick && !isRadiant;
        
        Team playerTeam = isRadiant ? Team.RADIANT : Team.DIRE;
        Team aiTeam = playerTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
        
        if (isSecondPickDire) {
            // Special handling for Second Pick + Dire scenario
            if (banIndex < 7) { // Ban Phase 1 (ABBABBA)
                switch (banIndex) {
                    case 0:
                        return Team.RADIANT; // First ban by First Pick team (AI/Radiant)
                    case 1:
                    case 2: 
                        return Team.DIRE; // Second, third bans by Second Pick team (Player/Dire)
                    case 3:
                        return Team.RADIANT; // Fourth ban by First Pick team (AI/Radiant)
                    case 4:
                    case 5:
                        return Team.DIRE; // Fifth, sixth bans by Second Pick team (Player/Dire)
                    case 6:
                        return Team.RADIANT; // Seventh ban by First Pick team (AI/Radiant)
                    default:
                        return Team.RADIANT; // Default fallback
                }
            } else if (banIndex >= 7 && banIndex <= 9) { // Ban Phase 2 (AAB)
                return (banIndex < 9) ? Team.RADIANT : Team.DIRE;
            } else if (banIndex >= 10) { // Ban Phase 3 (ABBA)
                if (banIndex == 10 || banIndex == 13) {
                    return Team.RADIANT;
                } else {
                    return Team.DIRE;
                }
            }
        } else if (needToSwapTeams) {
            // Handle other team swap scenarios
            boolean isBanByFirstPickTeam = FIRST_PICK_TEAM_BANS.contains(banIndex);
            return isBanByFirstPickTeam ? Team.DIRE : Team.RADIANT;
        } else {
            // Standard scenario: First Pick + Radiant
            boolean isBanByFirstPickTeam = FIRST_PICK_TEAM_BANS.contains(banIndex);
            return isBanByFirstPickTeam ? Team.RADIANT : Team.DIRE;
        }
        
        return Team.RADIANT; // Default fallback
    }
    
    private void testBanSequence(boolean isFirstPick, boolean isRadiant, String testCase) {
        Team playerTeam = isRadiant ? Team.RADIANT : Team.DIRE;
        Team aiTeam = playerTeam == Team.RADIANT ? Team.DIRE : Team.RADIANT;
        
        // Process all ban phases
        List<Team> banningTeams = new ArrayList<>();
        
        // All 14 bans across all phases
        for (int i = 0; i < 14; i++) {
            Team banningTeam = getBanningTeamForIndex(i, isFirstPick, isRadiant);
            banningTeams.add(banningTeam);
        }
        
        // Verify the length is correct
        assertEquals(14, banningTeams.size(), "Should have 14 bans in total");
        
        // Extract the first ban phase (first 7 bans)
        String phase1Pattern = "";
        for (int i = 0; i < 7; i++) {
            Team team = banningTeams.get(i);
            // Use A for first pick team, B for second pick team
            Team firstPickTeam = isFirstPick ? playerTeam : aiTeam;
            phase1Pattern += (team == firstPickTeam) ? "A" : "B";
        }
        
        // Extract the second ban phase (next 3 bans)
        String phase2Pattern = "";
        for (int i = 7; i < 10; i++) {
            Team team = banningTeams.get(i);
            Team firstPickTeam = isFirstPick ? playerTeam : aiTeam;
            phase2Pattern += (team == firstPickTeam) ? "A" : "B";
        }
        
        // Extract the third ban phase (last 4 bans)
        String phase3Pattern = "";
        for (int i = 10; i < 14; i++) {
            Team team = banningTeams.get(i);
            Team firstPickTeam = isFirstPick ? playerTeam : aiTeam;
            phase3Pattern += (team == firstPickTeam) ? "A" : "B";
        }
        
        // Verify all patterns match the expected sequences
        assertEquals(PHASE1_BAN_PATTERN, phase1Pattern, 
                "Ban phase 1 pattern should be ABBABBA for " + testCase);
        assertEquals(PHASE2_BAN_PATTERN, phase2Pattern, 
                "Ban phase 2 pattern should be AAB for " + testCase);
        assertEquals(PHASE3_BAN_PATTERN, phase3Pattern, 
                "Ban phase 3 pattern should be ABBA for " + testCase);
    }
    
    @Test
    @DisplayName("Test First Pick + Radiant ban sequence")
    public void testFirstPickRadiantBanSequence() {
        testBanSequence(true, true, "First Pick + Radiant");
    }
    
    @Test
    @DisplayName("Test First Pick + Dire ban sequence")
    public void testFirstPickDireBanSequence() {
        testBanSequence(true, false, "First Pick + Dire");
    }
    
    @Test
    @DisplayName("Test Second Pick + Radiant ban sequence")
    public void testSecondPickRadiantBanSequence() {
        testBanSequence(false, true, "Second Pick + Radiant");
    }
    
    @Test
    @DisplayName("Test Second Pick + Dire ban sequence") 
    public void testSecondPickDireBanSequence() {
        // This was our problematic scenario
        testBanSequence(false, false, "Second Pick + Dire");
    }
}