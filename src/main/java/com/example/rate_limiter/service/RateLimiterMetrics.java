package com.example.rate_limiter.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterMetrics {

    private final MeterRegistry registry;

    public RateLimiterMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Record a request that was allowed through rate limiter
     */
    public void recordAllowed(String endpoint) {
        Counter.builder("rate_limiter.requests.allowed")
                .tag("endpoint", endpoint)
                .tag("result", "allowed")
                .description("Number of requests allowed by rate limiter")
                .register(registry)
                .increment();
    }

    /**
     * Record a request that was rate limited (429)
     */
    public void recordRateLimited(String endpoint) {
        Counter.builder("rate_limiter.requests.blocked")
                .tag("endpoint", endpoint)
                .tag("result", "rate_limited")
                .description("Number of requests blocked by rate limiter")
                .register(registry)
                .increment();
    }

    /**
     * Record a Redis failure
     */
    public void recordRedisError(String endpoint) {
        Counter.builder("rate_limiter.redis.errors")
                .tag("endpoint", endpoint)
                .description("Number of Redis errors encountered")
                .register(registry)
                .increment();
    }

    /**
     * Record a fail-open event (Redis down, request allowed)
     */
    public void recordFailOpen(String endpoint) {
        Counter.builder("rate_limiter.fail_open.invoked")
                .tag("endpoint", endpoint)
                .tag("failure_mode", "fail_open")
                .description("Number of times fail-open was invoked")
                .register(registry)
                .increment();
    }

    /**
     * Record a fail-closed event (Redis down, request blocked)
     */
    public void recordFailClosed(String endpoint) {
        Counter.builder("rate_limiter.fail_closed.invoked")
                .tag("endpoint", endpoint)
                .tag("failure_mode", "fail_closed")
                .description("Number of times fail-closed was invoked")
                .register(registry)
                .increment();
    }

    /**
     * Record rate limiter check duration
     */
    public void recordCheckDuration(String endpoint, long durationMs) {
        Timer.builder("rate_limiter.check.duration")
                .tag("endpoint", endpoint)
                .description("Duration of rate limiter check in milliseconds")
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Record remaining tokens for monitoring
     */
    public void recordRemainingTokens(String endpoint, long remaining, long limit) {
        registry.gauge("rate_limiter.tokens.remaining", 
                java.util.List.of(
                    io.micrometer.core.instrument.Tag.of("endpoint", endpoint)
                ), 
                remaining);
        
        registry.gauge("rate_limiter.tokens.limit", 
                java.util.List.of(
                    io.micrometer.core.instrument.Tag.of("endpoint", endpoint)
                ), 
                limit);
        
        double usagePercent = limit > 0 ? ((limit - remaining) * 100.0 / limit) : 0;
        registry.gauge("rate_limiter.usage.percent", 
                java.util.List.of(
                    io.micrometer.core.instrument.Tag.of("endpoint", endpoint)
                ), 
                usagePercent);
    }
}
