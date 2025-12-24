package com.example.rate_limiter.service;

import com.example.rate_limiter.config.RateLimitConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBucketService {

    public static class RateLimitResult {
        private final boolean allowed;
        private final long remaining;
        private final long limit;
        private final long resetSeconds;
        private final boolean redisError;

        public RateLimitResult(boolean allowed, long remaining, long limit, long resetSeconds) {
            this(allowed, remaining, limit, resetSeconds, false);
        }

        public RateLimitResult(boolean allowed, long remaining, long limit, long resetSeconds, boolean redisError) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.limit = limit;
            this.resetSeconds = resetSeconds;
            this.redisError = redisError;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public long getRemaining() {
            return remaining;
        }

        public long getLimit() {
            return limit;
        }

        public long getResetSeconds() {
            return resetSeconds;
        }

        public boolean isRedisError() {
            return redisError;
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;
    private final RateLimiterMetrics metrics;
    private final ConcurrentHashMap<String, Long> localRejectCache = new ConcurrentHashMap<>();
    private static final long LOCAL_REJECT_TTL_MS = 3000;
    
    public TokenBucketService(StringRedisTemplate redisTemplate, RateLimiterMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("ratelimiter.lua"));
        this.script.setResultType(Long.class);
    }

    public RateLimitResult allow(String key, RateLimitConfig.Limit limit) {
        long nowMs = System.currentTimeMillis();
        long capacity = limit.capacity();
        double refillRate = limit.refillRate();
        
        long resetSeconds = (long) Math.ceil(1.0 / refillRate);

        Long rejectUntil = localRejectCache.get(key);
        if (rejectUntil != null && rejectUntil > nowMs) {
            return new RateLimitResult(false, 0, capacity, resetSeconds);
        }

        long nowSeconds = nowMs / 1000;

        Long result;
        try {
            result = redisTemplate.execute(
                    script,
                    List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(nowSeconds)
            );
        } catch (Exception e) {
            System.err.println("Redis error for key " + key + ": " + e.getMessage());
            
            metrics.recordRedisError(key);
            
            boolean allowOnFailure = limit.failureMode() == RateLimitConfig.FailureMode.FAIL_OPEN;
            
            if (allowOnFailure) {
                metrics.recordFailOpen(key);
            } else {
                metrics.recordFailClosed(key);
            }
            
            return new RateLimitResult(allowOnFailure, 0, capacity, resetSeconds, true);
        }

        if (result == null || result == 0) {
            localRejectCache.put(key, nowMs + LOCAL_REJECT_TTL_MS);
            return new RateLimitResult(false, 0, capacity, resetSeconds);
        }

        return new RateLimitResult(true, result, capacity, resetSeconds);
    }
}
