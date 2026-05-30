# Funny Movies

---

### Build Status

![Java 24](https://img.shields.io/badge/Java-24-blue)
![Spring Boot](https://img.shields.io/badge/Spring--Boot-3.5.0-brightgreen)
![React](https://img.shields.io/badge/React-19-61DAFB)
![Virtual Threads](https://img.shields.io/badge/Threads-Virtual--Threads-orange)
![StructuredTaskScope](https://img.shields.io/badge/Concurrency-StructuredTaskScope-informational)

![Build Status](https://github.com/nguyenhuuca/assessment/actions/workflows/funnyapp-ci.yml/badge.svg)
[![codecov](https://codecov.io/gh/nguyenhuuca/assessment/branch/main/graph/badge.svg?token=NC1XVNHCJW)](https://codecov.io/gh/nguyenhuuca/assessment)

---

## 📚 Documentation

Full architecture docs, ADRs, plans, and reports:

**[https://nguyenhuuca.github.io/assessment/](https://nguyenhuuca.github.io/assessment/)**

---

## Overview

**Funny Movies** is a video streaming web application built with **Java 24** and **Spring Boot 3.5.0**, backed by a **React 19** SPA frontend. It lets users upload, share, watch, and comment on funny videos.

### Key Features

- **Video streaming** — chunked range-request streaming with LRU cache
- **Passwordless auth** — magic link login (no passwords)
- **MFA** — optional TOTP-based two-factor authentication
- **Video sharing** — share private video links with other users
- **Comments** — per-video comment threads
- **Google Drive sync** — auto-sync videos from Google Drive to server every 15 minutes
- **Admin dashboard** — manage videos, accounts, and view stats

---

## Tech Stack

### Backend

| Component | Choice |
|-----------|--------|
| Language | Java 24 (preview features enabled) |
| Framework | Spring Boot 3.5.0 |
| Concurrency | Virtual Threads + `StructuredTaskScope` |
| ORM | JPA / Hibernate |
| Migrations | Liquibase |
| Security | Spring Security + JWT (JJWT 0.12.6) |
| Caching | Guava LRU 33.3.1 |
| API Docs | Springdoc OpenAPI 2.7.0 (Swagger) |
| Testing | JUnit 5 + Jacoco |
| Database | PostgreSQL + HikariCP |

### Frontend

| Component | Choice |
|-----------|--------|
| Framework | React 19 |
| Build Tool | Vite 6.2 |
| Routing | React Router DOM 7 |
| Server State | TanStack React Query 5 |
| UI | Bootstrap 5.3 + React-Bootstrap 2 |
| Icons | FontAwesome |
| HTTP Client | Axios |
| Testing | Vitest 3 + Testing Library |

### Infrastructure

| Component | Choice |
|-----------|--------|
| Containerization | Docker |
| Orchestration | Kubernetes + Helm |
| CI/CD | GitHub Actions |
| Registry | Docker Hub (`nguyenhuuca/funny-app`) |
| Deployment | VM (SSH/SCP) + Kubernetes (Helm) |
| Monitoring | Spring Actuator + Prometheus + OpenTelemetry |

---

## Architecture

Layered Spring Boot architecture: **Controller → Service → Repository**

```
api/src/main/java/com/canhlabs/funnyapp/
├── web/          # REST controllers (8 controllers)
├── service/      # Business logic (interfaces + impls)
├── repo/         # JPA repositories
├── entity/       # JPA entities
├── dto/          # Data Transfer Objects
├── client/       # External API clients
├── config/       # Spring configuration
├── cache/        # Guava LRU caches
├── aop/          # @AuditLog, @RateLimited aspects
├── jobs/         # Scheduled background tasks
└── utils/        # Validation, helpers
```

**Virtual Threads** enabled globally via `spring.threads.virtual.enabled=true`, with `StructuredTaskScope` used for coordinated concurrent calls (external APIs + email).

**Caching** — four Guava LRU caches:
- `VideoCacheImpl` — video metadata
- `ChunkIndexCacheImpl` — chunk position index
- `StatsCacheImpl` — view/hit statistics
- `MFASessionStoreImpl` — temporary MFA session storage

**Cross-cutting concerns** via AOP:
- `@AuditLog` — audit trail with automatic sensitive-field masking
- `@RateLimited` — per-endpoint rate limiting

---

## Setup & Installation

### Requirements

- JDK 24 (with preview features)
- Maven 3.6+
- PostgreSQL
- Node.js 18+

### Database

```bash
createdb funnyapp
psql -d funnyapp -f db/dump.sql
```

### Environment

```bash
cp api/.env.example api/.env
```

Required variables:

```env
DB_USER=postgres
DB_NAME=funnyapp
DB_PASS=your_password
DB_HOST=localhost

JWT_SECRET=your_jwt_secret
GOOGLE_KEY=your_google_api_key

EMAIL_SENDER=you@example.com
EMAIL_PASS=your_email_password
```

### Run Locally

```bash
# Backend
cd api && ./startLocal.sh

# Frontend
cd webapp && npm install && npm run dev
```

### Access

| URL | Purpose |
|-----|---------|
| `http://localhost:8081/swagger-ui/` | API docs (dev profile) |
| `http://localhost:8081/actuator/health` | Health check |
| `http://localhost:5173` | Frontend dev server |

---

## Testing

```bash
cd api

# Run unit tests
./unittest.sh

# Tests + coverage report
mvn verify
```

Coverage is enforced by Jacoco — currently at **55%**, with a **+1% gate per commit** (build fails if coverage drops).

---

## Docker

```bash
cd api
mvn clean package
docker build -t funny-app .
docker run -p 8081:8081 --env-file env.local funny-app
```

---

## CI/CD Pipeline

GitHub Actions workflow (`.github/workflows/funnyapp-ci.yml`):

```
Build → Test + Coverage → Docker Build → Push to Hub → Deploy to VM
```

1. **Build** — Maven clean package (JDK 24, skip tests)
2. **Test** — JUnit 5, Jacoco coverage gate (+1%), SonarCloud, Codecov
3. **Docker** — build image, auto-increment version, push `prod-{version}` + `latest`
4. **Deploy** — SCP JAR to VM, trigger deployment script via SSH

Current version: **32** (tracked in `api/.funny-app.version`)

---

## Deployment

### VM (current)

SSH + SCP deploy to `/opt/CICD/`, triggered automatically by CI on merge to `main`.

### Kubernetes (Helm)

```bash
helm upgrade --install funny-app helm/funny-app/ \
  --set image.tag=prod-32
```

Helm config: NodePort 30080, 1 replica, HPA at 80% CPU, 500m CPU / 600Mi memory limits.

---

## Online Demo

[https://funnyapp.canh-labs.com/](https://funnyapp.canh-labs.com/)

---

## Using Claude Code

This project is configured with the **Claude Code Framework** — 9 specialist personas, 90+ auto-triggered skills, and multi-agent swarm execution.

See the guide: [English](https://nguyenhuuca.github.io/assessment/claude-guide-en/) · [Tiếng Việt](https://nguyenhuuca.github.io/assessment/claude-guide-vi/)
