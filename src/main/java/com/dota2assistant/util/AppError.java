package com.dota2assistant.util;

import java.util.Map;

/**
 * Base exception class for all application errors.
 * Provides structured error information for logging and user feedback.
 */
public abstract class AppError extends RuntimeException {
    
    private final String code;
    private final boolean operational;
    private final Map<String, Object> context;
    
    /**
     * Creates an application error.
     *
     * @param code Machine-readable error code (e.g., "HERO_NOT_FOUND")
     * @param message Human-readable error message
     * @param operational True if expected/recoverable, false if unexpected bug
     * @param context Additional context for debugging
     * @param cause The underlying cause, if any
     */
    protected AppError(String code, String message, boolean operational,
                       Map<String, Object> context, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.operational = operational;
        this.context = context != null ? Map.copyOf(context) : Map.of();
    }
    
    public String getCode() {
        return code;
    }
    
    /**
     * Returns true if this is an expected, recoverable error.
     * Operational errors are user-facing and don't indicate bugs.
     */
    public boolean isOperational() {
        return operational;
    }
    
    public Map<String, Object> getContext() {
        return context;
    }
}

