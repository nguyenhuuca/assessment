---
name: optimizing-code
description: Improve code performance without changing behavior. Use when code fails latency/throughput requirements. Covers profiling, caching, and algorithmic optimization.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Optimizing Code

## The Optimization Hat

When optimizing, you improve **performance** without changing **behavior**. Always measure before and after.

## Golden Rules

1. **Measure First**: Never optimize without a benchmark
2. **Profile Before Guessing**: Find the actual bottleneck
3. **Optimize the Right Thing**: Focus on the critical path
4. **Measure After**: Verify the optimization worked

## Workflows

- [ ] **Benchmark**: Establish baseline performance metrics
- [ ] **Profile**: Identify the actual bottleneck
- [ ] **Hypothesize**: What optimization will help?
- [ ] **Implement**: Make the change
- [ ] **Measure**: Verify improvement
- [ ] **Document**: Record the optimization and results

## Common Optimizations

### Algorithm Complexity
- Replace O(n²) with O(n log n) or O(n)
- Use appropriate data structures (Set for lookups, Map for key-value)

### Caching (Java + Guava)
```java
// In-memory caching with Guava
@Service
public class DataService {
    private final LoadingCache<String, Result> cache;

    public DataService() {
        this.cache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Result>() {
                @Override
                public Result load(String key) {
                    return expensiveCalculation(key);
                }
            });
    }

    public Result getData(String input) {
        return cache.getUnchecked(input);
    }

    private Result expensiveCalculation(String input) {
        // Expensive work here
        return new Result();
    }
}
```

### Virtual Threads (Java 24)
```java
// Leverage Virtual Threads for I/O-heavy operations
@Configuration
public class VirtualThreadConfig {
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutor() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
}

// Parallel processing with StructuredTaskScope
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Future<UserDto> user = scope.fork(() -> fetchUser(userId));
    Future<List<Order>> orders = scope.fork(() -> fetchOrders(userId));

    scope.join();
    scope.throwIfFailed();

    return buildProfile(user.resultNow(), orders.resultNow());
}
```

### Database Queries (JPA/Hibernate)
```java
// ❌ BAD: N+1 query problem
@GetMapping("/users")
public List<UserDto> getUsers() {
    List<User> users = userRepository.findAll();
    // This triggers N additional queries!
    return users.stream()
        .map(user -> new UserDto(user, user.getOrders()))
        .toList();
}

// ✅ GOOD: Use JOIN FETCH to load in one query
@Query("SELECT u FROM User u LEFT JOIN FETCH u.orders WHERE u.status = :status")
Page<User> findActiveUsersWithOrders(@Param("status") UserStatus status, Pageable pageable);

// ✅ GOOD: Use @EntityGraph for eager loading
@EntityGraph(attributePaths = {"orders", "profile"})
List<User> findByStatus(UserStatus status);

// ✅ GOOD: Pagination for large result sets
@GetMapping("/users")
public Page<UserDto> getUsers(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    Pageable pageable = PageRequest.of(page, size);
    return userService.findAll(pageable);
}
```

- Add indexes for frequently queried columns
- Avoid N+1 queries (use JOIN FETCH or @EntityGraph)
- Use pagination for large result sets
- Use read-only transactions for queries: `@Transactional(readOnly = true)`

### Memory
- Avoid creating unnecessary objects in loops
- Use streaming for large files
- Release references when done

## Profiling Tools

```bash
# Java/JVM Profiling
# 1. JProfiler (commercial)
# 2. VisualVM (free, included with JDK)
jvisualvm

# 3. Async Profiler (open-source, production-ready)
java -agentpath:/path/to/libasyncProfiler.so=start,event=cpu,file=profile.html -jar app.jar

# 4. Spring Boot Actuator + Micrometer
# Add to application.yaml:
# management.endpoints.web.exposure.include=metrics,health
# management.metrics.export.prometheus.enabled=true

# View metrics
curl http://localhost:8081/actuator/metrics

# 5. JMH (Java Microbenchmark Harness) for method-level benchmarking
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main

# 6. Heap dump analysis
jmap -dump:format=b,file=heap.bin <pid>
jhat heap.bin

# 7. Thread dump
jstack <pid> > threads.txt

# 8. GC logging
java -Xlog:gc*:file=gc.log -jar app.jar
```

## Anti-Patterns to Avoid

- Premature optimization (no benchmark)
- Micro-optimizations (negligible impact)
- Optimizing cold paths
- Sacrificing readability for minor gains
