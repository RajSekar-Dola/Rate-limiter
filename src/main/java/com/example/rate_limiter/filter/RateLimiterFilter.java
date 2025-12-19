package com.example.rate_limiter.filter;

import com.example.rate_limiter.config.RateLimitConfig;
import com.example.rate_limiter.service.TokenBucketService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private final TokenBucketService service;

    public RateLimiterFilter(TokenBucketService service) {
        this.service = service;
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

        if (!service.allow(redisKey, limit)) {
            response.setStatus(429);
            response.getWriter().write("Too Many Requests");
            return;
        }

        chain.doFilter(request, response);
    }
}
