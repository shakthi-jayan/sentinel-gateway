# Sentinel Gateway

A distributed API gateway built from scratch in Spring Boot, handling authentication, per-client rate limiting, and fault tolerance across multiple backend services — the same category of problem solved by Kong, Envoy, or AWS API Gateway.

## Why this exists

Microservice architectures need a single, trusted entry point that can validate requests, protect downstream services from abuse, and fail gracefully when something goes down — without every individual service reimplementing that logic itself. Sentinel Gateway is that entry point. It sits in front of an authentication microservice and downstream APIs, and handles:

- **Routing** requests to the correct backend service by path
- **Authenticating** every request via JWT before it reaches any downstream service
- **Rate limiting** each client independently, backed by Redis so the limit holds even across multiple gateway instances
- **Failing fast** when a downstream service is unhealthy, instead of letting requests hang

## Architecture

```
                    ┌─────────────────────┐
                    │   gateway-service    │
                    │      (:8080)         │
                    │                       │
   client ────────▶ │  1. JWT validation    │
                    │  2. Rate limiting     │──────▶ Redis (:6379)
                    │  3. Circuit breaker   │        (rate-limit buckets,
                    │  4. Route by path     │         shared across instances)
                    └──────────┬────────────┘
                               │
                ┌──────────────┴──────────────┐
                ▼                              ▼
      ┌──────────────────┐          ┌──────────────────┐
      │ backend-service   │          │  downstream-b     │
      │     (:8081)       │          │     (:8082)       │
      └──────────────────┘          └──────────────────┘
```

All four services run in Docker containers on a custom bridge network, brought up with a single `docker compose up`.

## What each piece actually does

### Request routing
Built on **Spring Cloud Gateway Server WebMvc**, using a Java `RouterFunction` API rather than hardcoded proxy logic. Adding a new backend is a route definition, not new forwarding code.

### JWT authentication (trust boundary)
Every request to a protected route must carry a valid `Authorization: Bearer <token>` header, signed with the same secret used by the associated auth microservice. Requests with a missing, malformed, or invalid signature are rejected with `401` **before** they ever reach a downstream service — the gateway is the trust boundary, not each individual service.

### Redis-backed, per-client rate limiting
Implemented with **Bucket4j**, using a token-bucket algorithm with state stored in Redis via a `LettuceBasedProxyManager`, rather than an in-memory counter. This matters for one specific reason: if this gateway were ever scaled to multiple replicas, an in-memory bucket would let a client get multiples of the intended rate limit — one bucket per instance. Because the bucket lives in Redis, every instance shares the same counter.

Each client is keyed independently (by `X-Forwarded-For` or remote address), so one client exhausting their limit doesn't affect anyone else.

### Circuit breaker
Implemented with **Resilience4j**, wrapping calls to `downstream-b`. If the failure rate within a sliding window of the last 10 calls exceeds 50%, the breaker trips to **open** and immediately returns a fallback response instead of waiting on a service that's clearly struggling. After a 10-second cooldown, it moves to **half-open**, permits a few test calls, and either closes again (service recovered) or re-opens (still failing).

This was tested end-to-end: stopping `downstream-b` produced consistent `503` fallback responses instead of hung requests, and restarting it showed the breaker automatically detect recovery and resume normal routing — without any manual intervention.

## Load test: rate limiter under concurrent load

To verify the rate limiter holds up under real concurrent traffic (not just sequential manual testing), a [k6](https://k6.io) load test fired 10 concurrent virtual users at a protected endpoint for 15 seconds.

**Result: 1,206 total requests. 12 succeeded (200 OK). 1,194 were correctly throttled (429 Too Many Requests).**

![Load test results](loadtest-results.png)

This confirms the bucket's capacity and refill rate hold under sustained concurrent pressure, not just when requests are spaced out — the failure mode a naive or untested rate limiter would miss.

*Note: since all virtual users in this test originated from one machine, this demonstrates single-client throttling under concurrency, not fairness across many distinct clients — per-client isolation was verified separately via Redis key inspection.*

## Tech stack

- **Java 25**, Spring Boot 4.1
- **Spring Cloud Gateway Server WebMvc** — routing
- **Bucket4j + Redis (Lettuce)** — distributed rate limiting
- **JJWT** — JWT signing/validation
- **Resilience4j** — circuit breaking
- **Docker Compose** — multi-service orchestration, custom bridge network
- **k6** — load testing

## Running it locally

```bash
git clone <repo-url>
cd sentinel-gateway
docker compose up --build
```

This starts Redis, `backend-service`, `downstream-b`, and `gateway-service` together. Once running:

```bash
# Rejected — no token
curl http://localhost:8080/api/v1/hello

# Accepted — with a valid JWT
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/hello
```

## Notable challenges solved along the way

- **Spring Cloud Gateway's newer WebMvc variant** uses a different configuration prefix (`spring.cloud.gateway.server.webmvc.routes`) than most published tutorials, which cover the older reactive/WebFlux gateway — required tracing through actual source and current docs rather than common examples.
- **Bucket4j's rate-limit filter requires a distributed `AsyncProxyManager` bean**, not an in-memory default — meaning the rate limiter is Redis-backed by necessity, not as an afterthought.
- **Dependency scope mismatches** (`runtime` vs `compile`) caused classes to be available at runtime but not compile-time — required explicitly declaring `bucket4j-core`/`bucket4j-redis` at matching versions.
- **JVM version alignment between local dev and Docker** — a Java 25 local environment against a Java 21 Docker base image failed to compile; resolved by matching Docker's JDK version to the local one.

## What's next

- Externalize hardcoded service URIs and the JWT secret into environment variables for cleaner Docker Compose configuration
- Add a Redis-backed JWT blacklist check at the gateway, reusing the same blacklist logic from the auth microservice, so revoked tokens are rejected even before expiry
- Extend the circuit breaker pattern to `backend-service`