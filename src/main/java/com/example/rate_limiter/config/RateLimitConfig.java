package com.example.rate_limiter.config;
import java.util.Map;

public class RateLimitConfig {
    public static final Map<String, Limit> LIMITS = Map.of(
            "/api/data", new Limit(10, 10.0 / 60),
            "/api/otp", new Limit(3, 3.0 / 60)
    );

    public record Limit(int capacity, double refillRate) {}
}
