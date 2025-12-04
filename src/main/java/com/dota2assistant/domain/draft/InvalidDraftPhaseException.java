package com.dota2assistant.domain.draft;

import com.dota2assistant.util.AppError;
import java.util.Map;

/**
 * Exception thrown when an action is attempted in the wrong draft phase.
 */
public class InvalidDraftPhaseException extends AppError {
    
    public InvalidDraftPhaseException(DraftPhase current, String attemptedAction) {
        super(
            "INVALID_DRAFT_PHASE",
            "Cannot " + attemptedAction + " during " + current,
            true,
            Map.of("currentPhase", current, "attemptedAction", attemptedAction),
            null
        );
    }
}

