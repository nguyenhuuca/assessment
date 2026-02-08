# Tech Strategy - Golden Paths (Funny Movies Project)

This is the **SINGLE SOURCE OF TRUTH** for technology choices.

## Compliance

1. **Follow This File**: Use the technologies listed in the Golden Paths below
2. **No Deviations**: Do not suggest alternatives unless explicitly instructed
3. **Latest Stable**: Always use the latest stable version unless pinned

## Language Golden Paths

### Java (Backend Standard)

| Component | Choice |
|-----------|--------|
| Runtime | JDK 24 with Preview Features |
| Framework | Spring Boot 3.x |
| Concurrency | Virtual Threads (Project Loom) |
| Build Tool | Maven 3.6+ |
| ORM | JPA / Hibernate |
| Database Migrations | Liquibase |
| Testing | JUnit 5 |
| Coverage | Jacoco |
| API Documentation | Swagger / OpenAPI |
| Architecture | Layered (Controller → Service → Repository) |
| Validation | Spring Validation + Custom Contract Utils |
| Security | Spring Security + JWT |

**Required Maven Configuration:**
```xml
<compilerArgs>--enable-preview</compilerArgs>
<argLine>--enable-preview</argLine>
```

### JavaScript (Frontend Standard)

| Component | Choice |
|-----------|--------|
| Framework | Vanilla JavaScript (ES6+) |
| UI Library | Bootstrap 5 |
| HTTP Client | Fetch API |
| Location | `webapp/` directory |
| Architecture | Simple client-side rendering |

## Infrastructure

| Component | Choice |
|-----------|--------|
| Containerization | Docker |
| Orchestration | Kubernetes with Helm |
| Service Type | NodePort (port 30080) |
| Autoscaling | HPA (1-1 replicas, 80% CPU) |
| Resource Limits | 500m CPU, 600Mi memory |
| Deployment | SSH/SCP to VM + Kubernetes |
| CI/CD | GitHub Actions |
| Registry | Docker Hub |

## Data

| Component | Choice |
|-----------|--------|
| Database | PostgreSQL |
| Connection Pool | HikariCP (via Spring Boot) |
| Caching | Guava LRU Cache (in-memory) |
| Future Cache | Redis (for horizontal scaling) |

**Cache Types:**
- `VideoCacheImpl`: Video chunk caching
- `ChunkIndexCacheImpl`: Chunk metadata caching
- `StatsCacheImpl`: View/hit statistics caching
- `MFASessionStoreImpl`: Temporary MFA session storage

## External Integrations

| Component | Choice |
|-----------|--------|
| Video Platform | YouTube Data API v3 |
| AI Services | OpenAI ChatGPT API |
| Email | Spring Mail (Gmail SMTP) |
| Video Processing | FFmpeg (thumbnails) |
| Authentication | JWT + Magic Links |
| MFA | TOTP (optional) |

## Observability

| Component | Choice |
|-----------|--------|
| Standard | OpenTelemetry (OTel) |
| Metrics | Spring Boot Actuator + Prometheus |
| Health Checks | `/actuator/health` |
| Metrics Endpoint | `/actuator/prometheus` |
| Environment Config | `OTEL_*` variables |

## CI/CD Pipeline

| Component | Choice |
|-----------|--------|
| Platform | GitHub Actions |
| Build | Maven package with JDK 24 |
| Testing | Maven test with coverage |
| Coverage Reporting | Codecov + SonarCloud |
| Quality Gate | +1% coverage per commit |
| Version Management | Auto-increment in `.funny-app.version` |
| Docker Build | Automatic on successful tests |
| Registry | Docker Hub with version tags |
| Deployment | Helm chart update + VM deployment script |
| Security Scanning | Trivy (recommended, not yet implemented) |

**Version Strategy:**
- Version tracked in `api/.funny-app.version`
- Auto-incremented on successful builds
- Git tagged with version number
- Helm chart `values.yaml` updated automatically

## Code Quality

| Component | Choice |
|-----------|--------|
| Coverage Tool | Jacoco |
| Coverage Threshold | Progressive (+1% per commit) |
| Threshold File | `api/.coverage-threshold` |
| Excluded from Coverage | DTOs, entities, exceptions, config classes |
| Code Analysis | SonarCloud |

## Cross-Cutting Concerns

| Component | Choice |
|-----------|--------|
| Audit Logging | Custom AOP (`@AuditLog`) |
| Rate Limiting | Custom AOP (`@RateLimited`) |
| Data Masking | Automatic for sensitive fields |
| Error Handling | `@ControllerAdvice` with standard responses |
| Background Jobs | Spring `@Scheduled` in `AppScheduler` |

## Deployment Strategy

### Development
```bash
./startLocal.sh  # Build + Run locally with .env
```

### Testing
```bash
./unittest.sh    # Run tests
mvn verify       # Tests + Coverage report
```

### Production
1. **VM Deployment**: SSH + SCP JAR + deployment script
2. **Kubernetes**: Helm upgrade with new image version

## Configuration Management

| Component | Choice |
|-----------|--------|
| Spring Config | `application.yaml` |
| Environment Variables | `.env` file (local) |
| Required Variables | `DB_PASS`, `DB_HOST`, `GOOGLE_KEY`, `GPT_KEY`, `JWT_SECRET`, `EMAIL_SENDER`, `EMAIL_PASS` |
| Profiles | `dev` (Swagger enabled), `prod` (Swagger disabled) |

## API Standards

| Component | Choice |
|-----------|--------|
| Response Wrappers | `ResultObjectInfo<T>`, `ResultListInfo<T>`, `ResultErrorInfo` |
| HTTP Methods | RESTful conventions |
| Authentication | JWT Bearer token in Authorization header |
| Video Streaming | Range header support with chunked serving |
| File Handling | `RandomAccessFile` for efficient streaming |

## Prohibited Patterns

- ❌ No Redis until horizontal scaling is needed (ADR 0003)
- ❌ No Spring Data REST (use explicit controllers)
- ❌ No blocking I/O operations (leverage virtual threads)
- ❌ No synchronized keyword (use virtual thread-safe patterns)
- ❌ No raw SQL in service layer (use repositories)
- ❌ No hardcoded secrets (use environment variables)
- ❌ No `ddl-auto: create` or `update` (use Liquibase migrations)
