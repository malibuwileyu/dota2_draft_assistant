package com.dota2assistant.infrastructure.persistence;

import com.dota2assistant.util.AppError;
import java.util.Map;

/**
 * Exception thrown when a database operation fails.
 */
public class RepositoryException extends AppError {
    private static final long serialVersionUID = 1L;
    
    public RepositoryException(String message, Throwable cause) {
        super("REPOSITORY_ERROR", message, false, Map.of(), cause);
    }
    
    public RepositoryException(String message, Map<String, Object> context, Throwable cause) {
        super("REPOSITORY_ERROR", message, false, context, cause);
    }
}

