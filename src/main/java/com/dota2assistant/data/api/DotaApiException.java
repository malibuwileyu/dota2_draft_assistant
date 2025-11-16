package com.dota2assistant.data.api;

import java.io.IOException;

/**
 * Custom exception class for API errors with specific status codes
 */
public class DotaApiException extends IOException {
    private final int statusCode;
    private final String errorType;
    private final long matchId;
    
    public DotaApiException(String message, int statusCode) {
        this(message, statusCode, -1);
    }
    
    public DotaApiException(String message, int statusCode, long matchId) {
        super(message);
        this.statusCode = statusCode;
        this.matchId = matchId;
        
        // Categorize error types for better handling
        if (statusCode == 404) {
            this.errorType = "NOT_FOUND";
        } else if (statusCode == 400) {
            this.errorType = "INVALID_REQUEST";
        } else if (statusCode >= 500) {
            this.errorType = "SERVER_ERROR";
        } else if (statusCode == 429) {
            this.errorType = "RATE_LIMITED";
        } else if (statusCode == 422) {
            this.errorType = "UNPROCESSABLE_ENTITY";
        } else {
            this.errorType = "OTHER_ERROR";
        }
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getErrorType() {
        return errorType;
    }
    
    public long getMatchId() {
        return matchId;
    }
    
    @Override
    public String toString() {
        if (matchId != -1) {
            return String.format("[%s] Error %d: %s (Match ID: %d)", 
                    errorType, statusCode, getMessage(), matchId);
        } else {
            return String.format("[%s] Error %d: %s", 
                    errorType, statusCode, getMessage());
        }
    }
}