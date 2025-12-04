package com.dota2assistant.infrastructure.api;

import com.dota2assistant.util.AppError;
import java.util.Map;

/**
 * Exception thrown when an external API call fails.
 */
public class ApiException extends AppError {
    
    private final int statusCode;
    
    public ApiException(String service, int statusCode, String message) {
        super(
            "API_ERROR",
            service + " API error: " + message,
            statusCode == 429,  // Rate limits are operational (expected)
            Map.of("service", service, "statusCode", statusCode),
            null
        );
        this.statusCode = statusCode;
    }
    
    public ApiException(String service, String message, Throwable cause) {
        super(
            "API_ERROR",
            service + " API error: " + message,
            false,
            Map.of("service", service),
            cause
        );
        this.statusCode = -1;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public boolean isRateLimited() {
        return statusCode == 429;
    }
}

