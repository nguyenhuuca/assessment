# Funny Movies

---

### Build status

![Java 24](https://img.shields.io/badge/Java-24-blue)
![Spring Boot](https://img.shields.io/badge/Spring--Boot-3.x-brightgreen)
![ChatGPT Powered](https://img.shields.io/badge/AI-ChatGPT--Powered-purple)
![Virtual Threads](https://img.shields.io/badge/Threads-Virtual--Threads-orange)
![StructuredTaskScope](https://img.shields.io/badge/Concurrency-StructuredTaskScope-informational)

![Build Status](https://github.com/nguyenhuuca/assessment/actions/workflows/funnyapp-ci.yml/badge.svg)

[![codecov](https://codecov.io/gh/nguyenhuuca/assessment/branch/main/graph/badge.svg?token=NC1XVNHCJW)](https://codecov.io/gh/nguyenhuuca/assessment)

---

##  Overview

**Funny Movies** is a modern web application built with **Java 24** and **Spring Boot 3.x**, designed to deliver **high-performance video experiences**.

###  Key Highlights

-  Uses **Java 24 Virtual Threads** and `StructuredTaskScope` for lightweight concurrency
-  Users can share and view funny videos
-  Uses **ChatGPT AI** to suggest trending YouTube content
-  Integrates with the **YouTube API** to fetch and analyze video metadata

---

## Tech Stack

| Technology            | Description                                                 |
|-----------------------|-------------------------------------------------------------|
| **Java 24**           | Latest features, including Virtual Threads (Project Loom)   |
| **Spring Boot 3**     | Lightweight and efficient backend framework                 |
| **StructuredTaskScope** | High-concurrency coordination with clean error handling   |
| **ChatGPT API**       | AI-based suggestions for trending or funny videos           |
| **YouTube API**       | Video search and metadata analysis                          |
| **PostgreSQL**        | Persistent storage for users and videos                     |
| **JUnit + Jacoco**    | Unit testing and code coverage reporting                    |
| **GitHub Actions**    | CI/CD automation                                            |

---

##  Why Virtual Threads & StructuredTaskScope?

Thanks to **Project Loom**, Funny Movies uses **Virtual Threads** and `StructuredTaskScope` to handle thousands of concurrent tasks efficiently, especially for I/O-heavy operations like:

- Fetching YouTube metadata  
- Calling OpenAI GPT  
- Sending emails  
- Writing logs & analytics  

###  Benefits:

- Minimal memory usage  
- Fast context switching  
- Safe concurrent task coordination  
- Easier error handling (`shutdownOnFailure`, etc.)

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var metadataTask = scope.fork(() -> youtubeService.fetchVideoMetadata(id));
    var thumbnailTask = scope.fork(() -> ffmpegService.generateThumbnail(id));

    scope.join();
    scope.throwIfFailed();

    VideoMeta meta = metadataTask.get();
    String thumbnail = thumbnailTask.get();
}
```

---

## How ChatGPT Works

We integrate OpenAI's ChatGPT API to:

- Prompt trending video suggestions  
  > _e.g._ "Suggest 10 funny trending videos on YouTube this week"  
- Filter and analyze titles/descriptions  
- Display high-quality recommendations to users

---

##  Passwordless Login via Magic Link

**Funny Movies** uses a modern, secure, and frictionless login system:

> **No passwords. No reset flows. Just a click.**

### How it works:

1. User enters their email  
2. A **magic link** is sent  
3. Clicking it signs them in instantly

### Benefits:

-  No password reuse or phishing risks  
-  Email-based identity = simple  
-  Secure, expirable tokens with device binding

> Powered by JWT & one-time secure tokens.

---

##  Setup & Installation

###  Requirements

- **JDK 24** (with preview features enabled)  
- **Maven 3.6+**  
- **PostgreSQL**  
- **Google API Key** (YouTube Data API)  
  👉 https://console.cloud.google.com/apis/api/youtube.googleapis.com/credentials

---

###  Database Setup

1. Create a PostgreSQL database named `funnyapp`
2. Run `db/dump.sql` to create the schema & tables

---

###  Running App Locally

#### 1. Configure environment variables

Copy and edit:

```bash
cp api/.env.example api/.env
```

```env
DB_USER=postgres
DB_NAME=funnyapp
DB_PASS=your_password
DB_HOST=localhost

GOOGLE_KEY=your_google_api_key
JWT_SECRET=your_jwt_secret
GPT_KEY=your_openai_key

EMAIL_SENDER=you@example.com
EMAIL_PASS=your_email_password
```

#### 2. Start the app

```bash
cd api
./startLocal
```

#### 3. Access endpoints

-  Swagger UI: [http://localhost:8081/swagger-ui/](http://localhost:8081/swagger-ui/)  
-  Health Check: [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)

#### 4. Web UI

- Open `webapp/js/assessment.js`
- Update:

```js
const baseURL = "http://localhost:8081/v1/funny-app";
```

- Open `webapp/index.html` in your browser

---

###  Run Unit Tests

```bash
cd api
./unittest
```

> Jacoco report will be generated under `/target/jacoco-report`

---

## Docker options

Build your JAR:
```
mvn clean package
```
Build the Docker image:
```
docker build -t funny-app .
```
Run the container:
```
docker run -p 8080:8080 --env-file env.local funny-app
```
##  Online Demo

🔗 [https://funnyapp.canh-labs.com/](https://funnyapp.canh-labs.com/)
