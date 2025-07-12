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

## 🔥 Overview

**Funny Movies** is a modern web application built with **Java 24** and **Spring Boot 3.x**, designed to deliver **high-performance video experiences**.

### ✨ Key Highlights

- ⚡ Uses **Java 24 Virtual Threads** and `StructuredTaskScope` for lightweight concurrency
- 😂 Users can share and view funny videos
- 🤖 Uses **ChatGPT AI** to suggest trending YouTube content
- 🔍 Integrates with the **YouTube API** to fetch and analyze video metadata

---

## 🚀 Tech Stack

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

## ⚙️ Why Virtual Threads & StructuredTaskScope?

Thanks to **Project Loom**, Funny Movies uses **Virtual Threads** and `StructuredTaskScope` to handle thousands of concurrent tasks efficiently, especially for I/O-heavy operations like:

- Fetching YouTube metadata
- Calling OpenAI GPT
- Sending emails
- Writing logs & analytics

### ✅ Benefits:

- Minimal memo
