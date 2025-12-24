package com.example.rate_limiter.config;
import java.util.Map;

public class RateLimitConfig {
    
    public enum FailureMode {
        FAIL_OPEN,   
        FAIL_CLOSED  
    }
    
    public static final Map<String, Limit> LIMITS = Map.of(
            "/api/data", new Limit(10, 10.0 / 60, FailureMode.FAIL_OPEN),
            "/api/otp", new Limit(3, 3.0 / 60, FailureMode.FAIL_CLOSED)
    );

    public record Limit(int capacity, double refillRate, FailureMode failureMode) {}
}
