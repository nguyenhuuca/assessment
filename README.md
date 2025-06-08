# Funny Movives
---

### Build status
![Build Status](https://github.com/nguyenhuuca/assessment/actions/workflows/funnyapp-ci.yml/badge.svg)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring--Boot-3.x-brightgreen)
![ChatGPT Powered](https://img.shields.io/badge/AI-ChatGPT--Powered-purple)
![Virtual Threads](https://img.shields.io/badge/Threads-Virtual--Threads-orang)

[![codecov](https://codecov.io/gh/nguyenhuuca/assessment/branch/main/graph/badge.svg)](https://codecov.io/gh/nguyenhuuca/assessment)

---
## 🔥 Overview
**Funny Movies** is a modern web application built with **Java 21** and **Spring Boot 3.x**, designed to deliver **high-performance video experiences**.

Key Highlights:
- ⚡ **Leverages Java 21 Virtual Threads** (Project Loom) for high concurrency
- 😂 Allows users to share and view funny videos
- 🤖 Uses **ChatGPT AI** to suggest trending YouTube content
- 🔍 Integrates with the **YouTube API** to fetch and analyze video metadata

---

## 🚀 Tech Stack

| Technology        | Description                                                |
|-------------------|------------------------------------------------------------|
| **Java 21**       | Full use of modern language features, including Virtual Threads |
| **Spring Boot 3** | Lightweight and efficient backend framework                |
| **Virtual Threads** | Massive concurrency with low resource usage (Loom)       |
| **ChatGPT API**   | AI-based suggestions for trending or funny videos          |
| **YouTube API**   | Video search and metadata analysis                         |
| **PostgreSQL**    | Persistent storage for users and videos                    |
| **JUnit + Jacoco**| Unit testing and test coverage                             |
| **GitHub Actions**| CI/CD automation                                           |

---
## ⚙️ Why Virtual Threads?

Thanks to **Project Loom**, Funny Movies uses **Java 21 Virtual Threads** to handle thousands of concurrent HTTP requests efficiently without the overhead of traditional threads.

✅ Key benefits:
- Minimal memory usage
- Fast context switching
- Ideal for I/O-heavy operations (like calling YouTube API or OpenAI)

```java
// Example: Creating a lightweight virtual thread
Thread.startVirtualThread(() -> {
        // Handle video recommendation call here
        });
```
---
## 🧠 How ChatGPT Works

We integrate OpenAI's ChatGPT API to:

- Prompt trending video suggestions:
"Suggest 10 funny trending videos on YouTube this week"

- Filter and analyze titles/descriptions

- Display high-quality recommendations to users

---

### 🔐 Passwordless Login via Magic Link

**Funny Movies** uses a **modern, secure, and frictionless login system**:

> ✅ **No passwords. No reset flows. Just a click.**

#### How it works:
1. User enters their email
2. A **magic link** is sent to the email
3. Clicking the link signs them in instantly

#### Benefits:
- 🚫 No password reuse or phishing risks
- 📬 Email-based identity = simple for users
- 🔒 Secure tokens with short expiry and device binding

> Powered by JWT & secure one-time use tokens.


---


### Set up & Installation
#### Installation:
- OpenJDK/JDK 21 and export JAVA_HOME
- Maven >=3.6 and export MAVEN_HOME
- PostgresSQL.
- Generate the GOOGE API KEY to use their API [link](https://console.cloud.google.com/apis/api/youtube.googleapis.com/credentials)
- Creata database with name funnyapp.
- Run sql script int db/dump.sql to create tables


### Running Appp on LocalHost:
- Change file api/.env.example to api/.env and configure database info for postgres sql
```shell
DB_USER=postgres
DB_NAME=assessment
DB_PASS=[pass]
DB_HOST=[host]
GOOGLE_KEY=[key]
JWT_SECRET=[jwt_secret]
GOOGLE_KEY=[]
GPT_KEY=[]

EMAIL_SENDER=[]
EMAIL_PASS=[]
```
- Start app:
```shell
cd api
./startLocal 
```

#### To visit the endpoints running on the LocalHost:
`http://localhost:8081/swagger-ui/`

`http://localhost:8081/actutor/health`

#### To visit the  web LocalHost:
`Change baseURL in webapp/js/assesement.js to http://localhost:8081/v1/funny-app`

`Open webapp/index.html`

#### To visit demo:
Access https://funnyapp.canh-labs.com/

#### To run tests:
```shell
cd api
./unittest
```

