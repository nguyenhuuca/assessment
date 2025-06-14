# Funny Movives
---

### Build status

![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring--Boot-3.x-brightgreen)
![ChatGPT Powered](https://img.shields.io/badge/AI-ChatGPT--Powered-purple)
![Virtual Threads](https://img.shields.io/badge/Threads-Virtual--Threads-orang)

![Build Status](https://github.com/nguyenhuuca/assessment/actions/workflows/funnyapp-ci.yml/badge.svg)
[![Tests](https://github.com/nguyenhuuca/assessment/actions/workflows/funnyapp-ci.yml/badge.svg?branch=main&event=push&label=tests)](https://github.com/nguyenhuuca/assessment/actions/workflows/funnyapp-ci.yml)
[![Deploy](https://github.com/nguyenhuuca/assessment/actions/workflows/funnyapp-ci.yml/badge.svg?branch=main&event=push&label=deploy)](https://github.com/nguyenhuuca/assessment/actions/workflows/funnyapp-ci.yml)
[![Coverage](https://img.shields.io/codecov/c/github/nguyenhuuca/assessment/main)](https://codecov.io/gh/nguyenhuuca/assessment)

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

### 🚀 Setup & Installation

#### 🧰 Requirements:
- **JDK 21+** – make sure to set `JAVA_HOME`
- **Maven 3.6+** – make sure to set `MAVEN_HOME`
- **PostgreSQL** installed and running
- **Google API Key** – required for YouTube Data API  
  👉 Get it from [Google Cloud Console](https://console.cloud.google.com/apis/api/youtube.googleapis.com/credentials)

#### 🗃️ Database setup:
1. Create a PostgreSQL database named `funnyapp`
2. Run the SQL script in `db/dump.sql` to create the necessary tables

---

### ▶️ Running App Locally

#### 🔧 Configure environment variables:
Copy the example `.env` file and edit:
```shell
cp api/.env.example api/.env
```
Edit api/.env and update values:
```shell
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

▶ Start the application:
```shell
cd api
./startLocal
```

#### 🌐 Access Endpoints

✅ API Endpoints
- wagger UI: http://localhost:8081/swagger-ui/

- Health Check: http://localhost:8081/actutor/health

🖥️ Web UI
1. Open webapp/js/assesement.js

2. Update baseURL to:
```js
const baseURL = "http://localhost:8081/v1/funny-app";
```
3. Open webapp/index.html in your browser.

#### 🌍 Online Demo
Visit: https://funnyapp.canh-labs.com/

#### ✅ Running Unit Tests

```shell
cd api
./unittest
```

