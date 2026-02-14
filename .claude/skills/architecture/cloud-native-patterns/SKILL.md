---
name: cloud-native-patterns
description: Apply cloud-native architecture patterns. Use when designing for scalability, resilience, or cloud deployment. Covers microservices, containers, and distributed systems.
allowed-tools: Read, Write, Glob, Grep
---

# Cloud-Native Patterns

## Twelve-Factor App

1. **Codebase**: One codebase, many deploys
2. **Dependencies**: Explicitly declare and isolate
3. **Config**: Store in environment
4. **Backing Services**: Treat as attached resources
5. **Build, Release, Run**: Strictly separate stages
6. **Processes**: Execute as stateless processes
7. **Port Binding**: Export services via port
8. **Concurrency**: Scale out via process model
9. **Disposability**: Fast startup and graceful shutdown
10. **Dev/Prod Parity**: Keep environments similar
11. **Logs**: Treat as event streams
12. **Admin Processes**: Run as one-off processes

## Resilience Patterns

### Circuit Breaker (Resilience4j + Spring Boot)
Prevent cascading failures by failing fast.

```java
// Add dependency: io.github.resilience4j:resilience4j-spring-boot3

// Configuration
@Configuration
public class ResilienceConfig {
    @Bean
    public CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .build();
    }
}

// Service with circuit breaker
@Service
public class ExternalApiService {
    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;

    @CircuitBreaker(name = "externalApi", fallbackMethod = "fallback")
    public String callExternalApi(String request) {
        return restTemplate.getForObject(
            "https://api.example.com/data",
            String.class
        );
    }

    private String fallback(String request, Exception ex) {
        return "Fallback response: Service unavailable";
    }
}
```

### Retry with Backoff (Resilience4j)
```java
// Configuration
@Configuration
public class RetryConfig {
    @Bean
    public io.github.resilience4j.retry.RetryConfig retryConfig() {
        return io.github.resilience4j.retry.RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .retryExceptions(IOException.class, TimeoutException.class)
            .ignoreExceptions(BusinessException.class)
            .build();
    }
}

// Service with retry
@Service
public class DataService {
    @Retry(name = "dataService", fallbackMethod = "fallback")
    public Data fetchData(Long id) {
        // May throw transient exceptions
        return externalDataSource.fetch(id);
    }

    private Data fallback(Long id, Exception ex) {
        // Return cached or default value
        return Data.defaultValue();
    }
}

// Spring Retry alternative (simpler)
@Retryable(
    value = {RemoteAccessException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public String callRemoteService() {
    return restTemplate.getForObject(url, String.class);
}
```

### Bulkhead
Isolate failures to prevent system-wide impact.

## Service Communication

### Synchronous
- REST/HTTP
- gRPC

### Asynchronous
- Message queues (RabbitMQ, SQS)
- Event streaming (Kafka)

## Health Checks (Spring Boot Actuator)

```java
// Add dependency: spring-boot-starter-actuator

// application.yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
  health:
    readiness-state:
      enabled: true
    liveness-state:
      enabled: true

// Built-in endpoints
// GET /actuator/health - Overall health
// GET /actuator/health/liveness - Kubernetes liveness probe
// GET /actuator/health/readiness - Kubernetes readiness probe

// Custom health indicator
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    private final DataSource dataSource;

    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(1000)) {
                return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "reachable")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
        return Health.down().build();
    }
}

// Custom readiness check
@Component
public class CacheReadinessIndicator implements HealthIndicator {
    private final CacheManager cacheManager;

    @Override
    public Health health() {
        try {
            // Test cache connectivity
            boolean cacheHealthy = testCache();
            if (cacheHealthy) {
                return Health.up().build();
            }
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
        return Health.down().build();
    }
}
```

## Container Best Practices

- One process per container
- Use multi-stage builds
- Run as non-root user
- Use health checks
- Keep images small
