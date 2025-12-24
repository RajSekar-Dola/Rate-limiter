# Distributed Rate Limiter (Spring Boot + Redis)

A production-ready distributed rate limiter using Spring Boot and Redis with Token Bucket algorithm, implementing atomic operations via Lua scripts for thread-safe concurrent request handling across multiple application instances. Features per-API and per-user rate limiting with configurable capacity/refill rates, local rejection caching, intelligent failure modes, and comprehensive monitoring via Spring Boot Actuator and Micrometer metrics.

---

## Architecture Overview

```
Client Request
    ↓
Spring Boot Filter (RateLimiterFilter)
    ↓
Token Bucket Service
    ↓
Redis (Lua Script - Atomic Operations)
    ↓
Controller (if allowed)
```

---

## Rate Limiting Strategy

- **Algorithm:** Token Bucket
- Each API endpoint has:
    - **Capacity** (maximum tokens in bucket)
    - **Refill rate** (tokens added per second)
- Each request consumes **1 token**
- Tokens refill automatically based on elapsed time
- Requests are rejected when bucket is empty (HTTP 429)

---

## Configured Limits

| API Endpoint | Capacity | Refill Rate | Description |
|-------------|----------|------------|-------------|
| `/api/data` | 10 | 10 tokens/min | General data access endpoint |
| `/api/otp` | 3 | 3 tokens/min | OTP generation endpoint (stricter limit) |

---

## Key Features

### 🎯 Core Capabilities
- **Token Bucket Algorithm** - Distributed rate limiting with configurable capacity and refill rates
- **Atomic Operations** - Redis Lua scripts ensure thread-safe concurrent request handling
- **Per-User Limiting** - Tracks requests by `X-USER-ID` header or falls back to IP address
- **Per-API Configuration** - Different rate limits for different endpoints

### 🛡️ Reliability & Performance
- **Local Rejection Cache** - 3-second cache reduces Redis load during traffic bursts
- **Failure Mode Handling** - Configurable fail-open/fail-closed strategies when Redis is unavailable
- **Automatic Key Expiration** - Prevents memory leaks in Redis
- **Filter-Level Enforcement** - Spring's `OncePerRequestFilter` intercepts requests efficiently

### 📊 Monitoring & Observability
- **Comprehensive Metrics** - Request counts, token usage, latency, Redis health via Micrometer
- **Actuator Endpoints** - `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- **Rate Limit Headers** - Standard HTTP headers:
  - `X-RateLimit-Limit` - Maximum requests allowed
  - `X-RateLimit-Remaining` - Tokens remaining in bucket
  - `X-RateLimit-Reset` - Seconds until next token refill
- **Real-time Dashboards** - Prometheus/Grafana integration ready

### 🐳 Infrastructure
- **Dockerized** - Full Docker and Docker Compose support
- **Multi-Instance Ready** - Designed for horizontal scaling with shared Redis state

---

## Redis Lua Script Logic

The rate limiter uses atomic Lua scripts executed on Redis for consistency:

1. **Fetch State** - Get current token count and last refill timestamp
2. **Calculate Refill** - Add tokens based on elapsed time and refill rate
3. **Enforce Capacity** - Cap tokens at maximum bucket capacity
4. **Consume Token** - Atomically decrement token count if available
5. **Return Result** - Reject request when tokens are exhausted

This atomic execution ensures no race conditions in concurrent environments.

---

## Running the Project

### Prerequisites
- **Java 17** or higher
- **Maven 3.6+**
- **Docker** (for Redis)
- **Redis 6.0+** (if running standalone)

### Option 1: Docker Compose (Recommended)
```bash
# Start both Spring Boot app and Redis
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

### Option 2: Manual Setup

#### Start Redis
```bash
docker run -d -p 6379:6379 --name redis redis:latest
```

#### Build and Run Application
```bash
# Build the project
mvn clean package

# Run the application
java -jar target/rate-limiter-0.0.1-SNAPSHOT.jar

# Or use Maven
mvn spring-boot:run
```

---

## Testing the Rate Limiter

### Test `/api/data` endpoint (10 req/min limit)
```bash
# Send multiple requests
for i in {1..15}; do 
  curl -H "X-USER-ID: user123" http://localhost:8080/api/data
  echo ""
done
```

**Expected:** First 10 requests succeed, next 5 return `429 Too Many Requests`

### Test `/api/otp` endpoint (3 req/min limit)
```bash
# Send multiple requests
for i in {1..5}; do 
  curl -X POST -H "X-USER-ID: user456" http://localhost:8080/api/otp
  echo ""
done
```

**Expected:** First 3 requests succeed, next 2 return `429 Too Many Requests`

### Check Rate Limit Headers
```bash
curl -v -H "X-USER-ID: user789" http://localhost:8080/api/data
```

**Response Headers:**
```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 9
X-RateLimit-Reset: 6
```

---

## Monitoring & Metrics

### View All Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### View Specific Metrics
```bash
# Allowed requests
curl http://localhost:8080/actuator/metrics/rate_limiter.requests.allowed

# Blocked requests
curl http://localhost:8080/actuator/metrics/rate_limiter.requests.blocked

# Check duration
curl http://localhost:8080/actuator/metrics/rate_limiter.check.duration

# Remaining tokens
curl http://localhost:8080/actuator/metrics/rate_limiter.tokens.remaining
```

### Prometheus Format
```bash
curl http://localhost:8080/actuator/prometheus
```

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

See [METRICS.md](METRICS.md) for detailed monitoring documentation.

---

## Configuration

### Application Properties (`application.yml`)
```yaml
server:
  port: 8080

spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: 6379

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### Rate Limit Configuration
Edit [RateLimitConfig.java](src/main/java/com/example/rate_limiter/config/RateLimitConfig.java):

```java
public static final Map<String, Limit> LIMITS = Map.of(
    "/api/data", new Limit(10, 10.0 / 60, FailureMode.FAIL_OPEN),
    "/api/otp", new Limit(3, 3.0 / 60, FailureMode.FAIL_CLOSED)
);
```

---

## Project Structure

```
src/main/java/com/example/rate_limiter/
├── RateLimiterApplication.java          # Main application entry point
├── config/
│   ├── RateLimitConfig.java            # Rate limit configuration
│   └── RedisConfig.java                # Redis connection setup
├── controller/
│   ├── DataController.java             # /api/data endpoint
│   └── OtpController.java              # /api/otp endpoint
├── filter/
│   └── RateLimiterFilter.java          # Request interceptor
├── service/
│   ├── TokenBucketService.java         # Core rate limiting logic
│   └── RateLimiterMetrics.java         # Metrics collection
└── resources/
    ├── application.yml                  # Application configuration
    └── ratelimiter.lua                  # Redis Lua script
```

---

## How It Works

1. **Request Arrives** → Client sends HTTP request
2. **Filter Intercepts** → `RateLimiterFilter` captures request before controller
3. **User Identification** → Extracts `X-USER-ID` header or uses IP address
4. **Redis Key** → Creates key: `bucket:{endpoint}:{user}`
5. **Token Check** → Executes Lua script on Redis to check/consume token
6. **Decision**:
   - ✅ **Token available** → Allow request, proceed to controller
   - ❌ **No tokens** → Return HTTP 429 with retry headers
   - ⚠️ **Redis down** → Apply failure mode (fail-open or fail-closed)
7. **Metrics Recorded** → Track allowed/blocked/latency metrics
8. **Response** → Return to client with rate limit headers

---

## Deployment to Git

### Initialize Repository
```bash
cd "c:\Users\HP\Downloads\rate-limiter\rate-limiter"
git init
```

### Create .gitignore
```bash
# Add to .gitignore
echo "target/" >> .gitignore
echo "*.class" >> .gitignore
echo "*.log" >> .gitignore
echo ".idea/" >> .gitignore
echo "*.iml" >> .gitignore
```

### Commit Changes
```bash
git add .
git commit -m "Initial commit: Distributed rate limiter with Spring Boot and Redis"
```

### Push to GitHub
```bash
# Create repository on GitHub first, then:
git remote add origin https://github.com/YOUR_USERNAME/rate-limiter.git
git branch -M main
git push -u origin main
```

---

## Technology Stack

- **Spring Boot 3.x** - Application framework
- **Spring Data Redis** - Redis integration
- **Redis 6.0+** - Distributed cache and state store
- **Micrometer** - Metrics collection
- **Spring Boot Actuator** - Monitoring endpoints
- **Maven** - Build tool
- **Docker** - Containerization

---

## License

This project is licensed under the MIT License.

---

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

---

## Support

For questions or issues, please open a GitHub issue or contact the maintainer.
