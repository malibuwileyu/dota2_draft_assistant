package com.dota2assistant.domain.draft;

/**
 * Phases of a Captain's Mode draft.
 */
public enum DraftPhase {
    BAN_1,      // First ban phase (7 bans: ABBABBA)
    PICK_1,     // First pick phase (2 picks: AB)
    BAN_2,      // Second ban phase (3 bans: AAB)
    PICK_2,     // Second pick phase (6 picks: BAABBA)
    BAN_3,      // Third ban phase (4 bans: ABBA)
    PICK_3,     // Third pick phase (2 picks: AB)
    COMPLETED;  // Draft finished
    
    /**
     * Check if this phase is a ban phase.
     */
    public boolean isBanPhase() {
        return this == BAN_1 || this == BAN_2 || this == BAN_3;
    }
    
    /**
     * Check if this phase is a pick phase.
     */
    public boolean isPickPhase() {
        return this == PICK_1 || this == PICK_2 || this == PICK_3;
    }
}

