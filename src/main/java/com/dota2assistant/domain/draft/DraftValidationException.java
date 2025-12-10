package com.dota2assistant.domain.draft;

import com.dota2assistant.util.AppError;
import java.util.Map;

/**
 * Exception thrown when a draft action fails validation.
 */
public class DraftValidationException extends AppError {
    private static final long serialVersionUID = 1L;
    
    public DraftValidationException(String message) {
        super("DRAFT_VALIDATION_ERROR", message, true, Map.of(), null);
    }
    
    public DraftValidationException(String message, Map<String, Object> context) {
        super("DRAFT_VALIDATION_ERROR", message, true, context, null);
    }
}

