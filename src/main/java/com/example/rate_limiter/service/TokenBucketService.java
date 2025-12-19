package com.example.rate_limiter.service;

import com.example.rate_limiter.config.RateLimitConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBucketService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;
    private final ConcurrentHashMap<String, Long> localRejectCache = new ConcurrentHashMap<>();
    private static final long LOCAL_REJECT_TTL_MS = 3000;
    public TokenBucketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("ratelimiter.lua"));
        this.script.setResultType(Long.class);
    }

    public boolean allow(String key, RateLimitConfig.Limit limit) {
        long nowMs = System.currentTimeMillis();

        Long rejectUntil = localRejectCache.get(key);
        if (rejectUntil != null && rejectUntil > nowMs) {
            return false;
        }

        long nowSeconds = nowMs / 1000;

        Long result = redisTemplate.execute(
                script,
                List.of(key),
                String.valueOf(limit.capacity()),
                String.valueOf(limit.refillRate()),
                String.valueOf(nowSeconds)
        );

        if (result == null || result == 0) {
            localRejectCache.put(key, nowMs + LOCAL_REJECT_TTL_MS);
            return false;
        }

        return true;
    }
}
