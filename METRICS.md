# Rate Limiter Metrics & Monitoring Guide

## 📊 Available Metrics

### 1. **Request Metrics**
```
rate_limiter.requests.allowed{endpoint="/api/data"}
rate_limiter.requests.allowed{endpoint="/api/otp"}
rate_limiter.requests.blocked{endpoint="/api/data"}
rate_limiter.requests.blocked{endpoint="/api/otp"}
```
Tracks allowed vs blocked requests per endpoint.

### 2. **Redis Health Metrics**
```
rate_limiter.redis.errors{endpoint="/api/data"}
rate_limiter.redis.errors{endpoint="/api/otp"}
```
Tracks Redis connection failures and errors.

### 3. **Failure Mode Metrics**
```
rate_limiter.fail_open.invoked{endpoint="/api/data"}
rate_limiter.fail_closed.invoked{endpoint="/api/otp"}
```
Tracks when fail-open or fail-closed strategies are triggered.

### 4. **Performance Metrics**
```
rate_limiter.check.duration{endpoint="/api/data"}
rate_limiter.check.duration{endpoint="/api/otp"}
```
Tracks latency of rate limiter checks in milliseconds.

### 5. **Token Usage Metrics**
```
rate_limiter.tokens.remaining{endpoint="/api/data"}
rate_limiter.tokens.limit{endpoint="/api/data"}
rate_limiter.usage.percent{endpoint="/api/data"}
```
Real-time view of token bucket state and usage percentage.

---

## 🔍 Monitoring Endpoints

### Actuator Endpoints
```bash
# Health check
GET http://localhost:8080/actuator/health

# All metrics
GET http://localhost:8080/actuator/metrics

# Specific metric
GET http://localhost:8080/actuator/metrics/rate_limiter.requests.allowed

# Prometheus scrape endpoint
GET http://localhost:8080/actuator/prometheus
```

---

## 📈 Prometheus Configuration

Add this to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'rate-limiter'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    static_configs:
      - targets: ['localhost:8080']
```

---

## 🎯 Sample Queries

### Grafana/PromQL Queries

**1. Request Rate (per second)**
```promql
rate(rate_limiter_requests_allowed_total[1m])
```

**2. Block Rate**
```promql
rate(rate_limiter_requests_blocked_total[1m])
```

**3. Redis Error Rate**
```promql
rate(rate_limiter_redis_errors_total[5m])
```

**4. Fail-Open Invocations**
```promql
increase(rate_limiter_fail_open_invoked_total[1h])
```

**5. Average Check Latency**
```promql
rate(rate_limiter_check_duration_sum[1m]) / 
rate(rate_limiter_check_duration_count[1m])
```

**6. Token Usage by Endpoint**
```promql
rate_limiter_usage_percent{endpoint="/api/otp"}
```

---

## 🚨 Alerting Rules

```yaml
groups:
  - name: rate_limiter_alerts
    rules:
      # High Redis error rate
      - alert: RedisHighErrorRate
        expr: rate(rate_limiter_redis_errors_total[5m]) > 0.1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Redis errors detected in rate limiter"
          
      # Fail-closed invoked (security critical)
      - alert: FailClosedInvoked
        expr: increase(rate_limiter_fail_closed_invoked_total[5m]) > 0
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Fail-closed triggered on {{ $labels.endpoint }}"
          
      # High block rate
      - alert: HighBlockRate
        expr: rate(rate_limiter_requests_blocked_total[5m]) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High rate limit blocks on {{ $labels.endpoint }}"
```

---

## 📊 Sample Dashboard Layout

```
┌─────────────────────────────────────────────────┐
│  Rate Limiter Overview                          │
├─────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐     │
│  │ Allowed  │  │ Blocked  │  │  Redis   │     │
│  │  Reqs/s  │  │  Reqs/s  │  │  Errors  │     │
│  └──────────┘  └──────────┘  └──────────┘     │
├─────────────────────────────────────────────────┤
│  Request Rate by Endpoint                       │
│  [Line Graph: /api/data vs /api/otp]           │
├─────────────────────────────────────────────────┤
│  Token Usage (%)                                │
│  [Gauge: Current usage percentage]              │
├─────────────────────────────────────────────────┤
│  Check Latency (p50, p95, p99)                 │
│  [Histogram]                                    │
└─────────────────────────────────────────────────┘
```

---

## 🧪 Testing Metrics

```bash
# Generate some traffic
for i in {1..100}; do 
  curl http://localhost:8080/api/data
done

# Check metrics
curl http://localhost:8080/actuator/metrics/rate_limiter.requests.allowed | jq

# View Prometheus format
curl http://localhost:8080/actuator/prometheus | grep rate_limiter
```

---

## 📝 Logging

All Redis errors are logged to stderr:
```
Redis error for key bucket:/api/otp:192.168.1.1: Connection refused
```

Consider adding structured logging with SLF4J for production:
```java
log.error("Redis error for key={}, endpoint={}, error={}", 
    key, endpoint, e.getMessage());
```
