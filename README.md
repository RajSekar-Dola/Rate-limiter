# Distributed Rate Limiter (Spring Boot + Redis)

This project implements a **distributed rate limiter** using the **Token Bucket algorithm** with Redis as the central state store.  
It supports **per-API and per-user rate limiting** and is designed to work reliably in concurrent and multi-instance environments.

---

## Architecture Overview

Client  
→ Spring Boot Filter  
→ Token Bucket Service  
→ Redis (Lua Script)  
→ Controller

---

## Rate Limiting Strategy

- **Algorithm:** Token Bucket
- Each API has:
    - **Capacity** (max tokens)
    - **Refill rate** (tokens per second)
- Each request consumes **1 token**
- Tokens refill automatically based on elapsed time

---

## Configured Limits

| API Endpoint | Capacity | Refill Rate |
|-------------|----------|------------|
| `/api/data` | 10 | 10 tokens/min |
| `/api/otp` | 3 | 3 tokens/min |

---

## Key Features

- **Atomic operations using Redis Lua**
- **Per-user rate limiting** using User ID or IP address
- **Local rejection cache** to reduce Redis load during burst traffic
- **Automatic key expiration** to prevent memory leaks
- **Filter-level enforcement** using Spring’s `OncePerRequestFilter`

---

## Redis Lua Script Logic

- Fetch current token count and timestamp
- Refill tokens based on elapsed time
- Enforce capacity limit
- Atomically decrement tokens on successful request
- Reject request when tokens are exhausted

---

## Running the Project

### Start Redis
```bash
docker run -d -p 6379:6379 redis
