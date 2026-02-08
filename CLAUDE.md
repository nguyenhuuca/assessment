# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

**Funny Movies** is a high-performance video streaming application built with Java 24, Spring Boot 3.x, and PostgreSQL. The application leverages cutting-edge Java features including Virtual Threads (Project Loom) and `StructuredTaskScope` for efficient concurrency. It integrates with YouTube API and ChatGPT for AI-powered video suggestions, and implements passwordless authentication via magic links.

## Model Usage Guidelines (Cost Optimization)

To optimize API costs when using Claude Code, always select the appropriate model for each task:

### Model Selection Rules

**Use Haiku (Cheapest - ~$0.001/task):**
- File searches and pattern matching (glob, grep operations)
- Reading and summarizing code structure
- Finding files by name or content
- Quick codebase exploration
- Listing files, directories, or dependencies
- Simple "what/where" questions

**Use Sonnet (Default - ~$0.05-0.20/task):**
- Writing new code or features
- Refactoring existing code
- Code reviews and analysis
- Debugging issues
- Test implementation
- Complex "how" questions

**Use Opus (Most Expensive - ~$0.50+/task):**
- Major architecture decisions only
- System design problems
- Complex trade-off analysis
- Reserve for critical design work

### How to Specify Model

**Option 1 - Explicit instruction:**
```
"Use Haiku to find all files containing 'authentication'"
"Use Sonnet to refactor the UserService class"
```

**Option 2 - Use appropriate agent types:**
```
worker-explorer    → Auto uses Haiku (searches, exploration)
worker-builder     → Auto uses Sonnet (implementation)
worker-architect   → Auto uses Opus (design)
```

**Option 3 - In Task tool:**
```
subagent_type: "general-purpose"
model: "haiku"  // Override to cheaper model for simple tasks
```

### Cost-Efficient Workflows

**Bad (expensive):**
```
"Find and analyze all services, then refactor them"  → All Sonnet
Cost: ~$0.50
```

**Good (optimized):**
```
Step 1: "Use Haiku to find all service files"        → Haiku
Step 2: "Read UserService.java specifically"         → Haiku
Step 3: "Refactor UserService with these changes"    → Sonnet
Cost: ~$0.12 (60% savings)
```

### Guidelines Summary

- **Search first with Haiku**, then implement with Sonnet
- **Batch similar operations** in one request when possible
- **Be specific** about which files to analyze (avoid "analyze everything")
- **Use Opus sparingly** - only for critical architectural decisions
- **Keep sessions focused** - cache expires after 5 minutes of inactivity

Target: **~$0.10-0.15 per productive hour** with proper model selection.

## Project Structure

```
.
├── api/                    # Spring Boot backend application
│   ├── src/main/java/com/canhlabs/funnyapp/
│   │   ├── aop/           # AOP aspects (audit logging, rate limiting, masking)
│   │   ├── cache/         # Caching layer (Guava LRU cache implementations)
│   │   ├── client/        # External API clients (YouTube)
│   │   ├── config/        # Spring configuration classes
│   │   ├── dto/           # Data Transfer Objects
│   │   ├── entity/        # JPA entities
│   │   ├── enums/         # Enumerations
│   │   ├── exception/     # Custom exceptions and error handling
│   │   ├── filter/        # HTTP filters
│   │   ├── jobs/          # Scheduled background jobs
│   │   ├── repo/          # JPA repositories
│   │   ├── service/       # Business logic services
│   │   ├── utils/         # Utility classes
│   │   └── web/           # REST controllers
│   ├── src/main/resources/
│   │   ├── application.yaml    # Spring Boot configuration
│   │   └── db/changelog/       # Liquibase database migrations
│   └── pom.xml            # Maven dependencies and build configuration
├── webapp/                # Frontend (vanilla JS application)
├── db/                    # Database schema and migrations
├── helm/                  # Kubernetes Helm charts
├── k8s-manifest/          # Raw Kubernetes manifests
└── doc/                   # Architecture documentation and ADRs
```

## Essential Development Commands

### Local Development

**Prerequisites:**
- JDK 24 with preview features enabled
- Maven 3.6+
- PostgreSQL database
- Google API key for YouTube Data API

**Setup database:**
```bash
# Create PostgreSQL database
createdb funnyapp

# Run schema from db/dump.sql
psql -d funnyapp -f db/dump.sql
```

**Configure environment:**
```bash
# Copy and edit environment variables
cp api/.env.example api/.env

# Required variables in .env:
# DB_PASS, DB_HOST, GOOGLE_KEY, GPT_KEY, JWT_SECRET, EMAIL_SENDER, EMAIL_PASS
```

**Build and run locally:**
```bash
cd api
./startLocal.sh    # Runs: mvn clean install && java -jar target/funny-app-1.0.0.jar
```

**Run unit tests:**
```bash
cd api
./unittest.sh      # Runs: mvn -DskipTests=false test
```

**Run tests with coverage:**
```bash
cd api
mvn verify         # Generates Jacoco report in target/jacoco-report/
```

**Build only (skip tests):**
```bash
cd api
mvn clean package -DskipTests=true
```

**Run single test class:**
```bash
cd api
mvn test -Dtest=ClassName
```

**Run single test method:**
```bash
cd api
mvn test -Dtest=ClassName#methodName
```

### Docker

**Build Docker image:**
```bash
cd api
mvn clean package
docker build -t funny-app .
```

**Run with Docker:**
```bash
docker run -p 8081:8081 --env-file env.local funny-app
```

### API Access

- **Swagger UI (dev profile):** http://localhost:8081/swagger-ui/
- **Health Check:** http://localhost:8081/actuator/health
- **Prometheus Metrics:** http://localhost:8081/actuator/prometheus

## Architecture Highlights

### Virtual Threads & Concurrency

The application uses Java 24 Virtual Threads throughout for efficient handling of I/O-bound operations. Key configuration:
- `spring.threads.virtual.enabled=true` in application.yaml
- `StructuredTaskScope` is used for coordinating concurrent tasks (e.g., fetching YouTube metadata + generating thumbnails)
- All blocking I/O operations (DB, external APIs, file operations) leverage virtual threads automatically

### Layered Architecture

Classic Spring Boot layered design:
- **Controller Layer** (`web/`): REST API endpoints, request/response handling
- **Service Layer** (`service/`): Business logic, orchestrates repositories and external clients
- **Repository Layer** (`repo/`): JPA data access
- **Client Layer** (`client/`): External API integrations (YouTube, ChatGPT)

### Caching Strategy

**Current Implementation:** Guava-based LRU in-memory cache
- Video metadata caching (titles, duration, availability)
- Video chunk caching for streaming performance
- Hit stats caching (view count, like count)
- MFA session storage
- Configured via `cache.type=guava` in application.yaml

**Cache Implementations:**
- `GuavaAppCache`: Base Guava cache wrapper
- `VideoCacheImpl`: Video chunk caching with LRU eviction
- `ChunkIndexCacheImpl`: Chunk metadata caching
- `StatsCacheImpl`: View/hit statistics caching
- `MFASessionStoreImpl`: Temporary MFA session storage

**Future Migration:** Redis is planned for horizontal scaling (see doc/adr/0003-use-cache.md)

### Authentication

Passwordless magic link authentication:
1. User enters email
2. System sends magic link via email
3. Clicking link signs user in with JWT token
4. Optional MFA (TOTP) support

JWT configuration in `app.jwt-secret-key` and `app.token-expired` properties.

### Database

PostgreSQL with:
- JPA/Hibernate for ORM (`ddl-auto: none` - schema managed externally)
- Liquibase for migrations (`db/changelog/`)
- Main entities: `User`, `ShareLink`, `YouTubeVideo`, `VideoAccessStats`, `UserEmailRequest`

### Background Jobs

Scheduled tasks in `jobs/AppScheduler.java`:
- Download videos from Google Drive
- Generate thumbnails via FFmpeg
- Cleanup old data
- Stats aggregation

### AOP Features

Cross-cutting concerns handled via aspects:
- **Audit Logging** (`@AuditLog`): Logs method calls with sensitive field masking
- **Rate Limiting** (`@RateLimited`): Sliding window rate limiter
- **Sensitive Data Masking**: Automatic masking of password, token, secret fields in logs

### External Integrations

- **YouTube API**: Video metadata fetching (`YouTubeApiClient`)
- **ChatGPT API**: AI-powered video suggestions (`ChatGptService`)
- **Email**: Magic link delivery via Spring Mail (Gmail SMTP)
- **FFmpeg**: Thumbnail generation (`FfmpegService`)

## Testing & CI/CD

### Code Coverage

Jacoco enforces coverage with a progressive threshold:
- Coverage threshold stored in `api/.coverage-threshold`
- CI enforces +1% coverage increase per commit
- Excluded from coverage: DTOs, entities, exceptions, config classes
- Reports uploaded to Codecov

### CI Pipeline

GitHub Actions workflow (`.github/workflows/funnyapp-ci.yml`):
1. **Build**: Maven package with JDK 24
2. **Test**: Run tests, generate coverage, upload to Codecov & SonarCloud
3. **Docker**: Build and push image to Docker Hub with auto-incremented version
4. **Deploy**: SCP JAR to remote server and execute deployment script

**Version Management:**
- Version tracked in `api/.funny-app.version`
- Auto-incremented on successful builds
- Git tagged with version number
- Helm chart updated with new image tag

### Deployment

- **VM Deployment**: Via SSH + SCP to remote server
- **Kubernetes**: Helm charts in `helm/funny-app/`
  - NodePort service on port 30080
  - HPA configured (1-1 replicas, 80% CPU threshold)
  - Resource limits: 500m CPU, 600Mi memory

## Important Configuration Notes

### Java 24 Preview Features

The project uses preview features and requires:
```xml
<compilerArgs>--enable-preview</compilerArgs>
```
And runtime flag in test configuration:
```xml
<argLine>--enable-preview</argLine>
```

### Environment Variables

Critical environment variables (see `api/.env.example`):
- `DB_PASS`, `DB_HOST`: PostgreSQL connection
- `GOOGLE_KEY`: YouTube Data API key (get from Google Cloud Console)
- `GPT_KEY`: OpenAI API key
- `JWT_SECRET`: 32+ character secret for JWT signing
- `EMAIL_SENDER`, `EMAIL_PASS`: Gmail SMTP credentials
- `OTEL_*`: OpenTelemetry configuration for observability

### Spring Profiles

- `dev`: Swagger UI enabled
- `prod`: Swagger UI disabled (default)

Switch via `SPRING_PROFILES_ACTIVE` environment variable.

## Architecture Decision Records

Key architectural decisions documented in `doc/adr/`:
- **0001**: Use PostgreSQL relational database
- **0002**: Layered Spring Boot architecture with Virtual Threads
- **0003**: Guava Cache (future Redis migration)
- **0004**: Frontend structure (vanilla JS)
- **0005**: LRU cache for video streaming

Full documentation in `doc/` including sequence diagrams and architecture quality reports.

## Common Development Patterns

### Error Handling

Centralized exception handling via `@ControllerAdvice`. Custom exceptions extend base `Error` class.

### DTOs and API Responses

Standard response wrappers:
- `ResultObjectInfo<T>`: Single object response
- `ResultListInfo<T>`: List response
- `ResultErrorInfo`: Error response

### Service Implementation

Service interfaces in `service/`, implementations in `service/impl/`.
Follow pattern: `Service` interface → `ServiceImpl` class.

### Video Streaming

Video streaming uses chunked serving with:
- Range header support
- LRU cache for chunks
- `RandomAccessFile` for efficient file reading
- Virtual threads for concurrent chunk serving

### Contract Utilities

Use `Contract` and `ContractDSL` for validation and preconditions:
```java
Contract.require(condition, "Error message");
ContractDSL.of(value).notNull().inRange(min, max);
```
