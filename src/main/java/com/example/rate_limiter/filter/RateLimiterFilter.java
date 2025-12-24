package com.example.rate_limiter.filter;

import com.example.rate_limiter.config.RateLimitConfig;
import com.example.rate_limiter.service.RateLimiterMetrics;
import com.example.rate_limiter.service.TokenBucketService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private final TokenBucketService service;
    private final RateLimiterMetrics metrics;

    public RateLimiterFilter(TokenBucketService service, RateLimiterMetrics metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {

        String path = request.getRequestURI();
        RateLimitConfig.Limit limit = RateLimitConfig.LIMITS.get(path);

        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        String user = request.getHeader("X-USER-ID");
        if (user == null) user = request.getRemoteAddr();

        String redisKey = "bucket:" + path + ":" + user;

        long startTime = System.currentTimeMillis();
        TokenBucketService.RateLimitResult result = service.allow(redisKey, limit);
        long duration = System.currentTimeMillis() - startTime;
        
        metrics.recordCheckDuration(path, duration);
        
        metrics.recordRemainingTokens(path, result.getRemaining(), result.getLimit());
        
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetSeconds()));

        if (!result.isAllowed()) {
            if (result.isRedisError()) {
                response.setStatus(503);
                response.getWriter().write("Service Unavailable - Rate limiter unavailable");
            } else {
                metrics.recordRateLimited(path);
                response.setStatus(429);
                response.getWriter().write("Too Many Requests");
            }
            return;
        }

        
        metrics.recordAllowed(path);
        chain.doFilter(request, response);
    }
}
