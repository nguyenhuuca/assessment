# STREAM-VIDEO Board - Card Status Report

**Generated**: 2026-02-13 10:47:21
**Board**: STREAM-VIDEO
**Total Cards**: 25

## Summary by Status

| Status | Count |
|--------|-------|
| Deployed | 14 |
| Backlog/To Do | 7 |
| In Progress | 3 |
| Backlog/To Do | 1 |

---

## In Progress

**List ID**: 6865e49fe5b307299e1eb1dc
**Card Count**: 3

### [2] Create Cron Job to Clean Up Old Unviewed Files and Log Deleted Files

**Link**: https://trello.com/c/IqFTuacQ

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-04 |
| Labels | Doing |

<details>
<summary>View Description</summary>

### **Description:**

As a system maintainer, I want a scheduled job that automatically deletes video files from disk that haven’t been viewed for a long time, so that we can free up disk space.
Additionally, I want to log the details of each deleted file into a database table for audit and tracking purposes.

---

### **Acceptance Criteria:**

- ✅ A new cron job runs periodically (e.g., daily) to scan and delete video files that haven't been accessed/viewed in the last **N** days (configurable).
- ✅ A new table (e.g., `deleted_files_log`) is created with the following fields:
  - `id` (UUID or auto-increment)
  - `file_path` (string)
  - `deleted_at` (timestamp)
  - `reason` (string or enum, e.g., `UNUSED_TOO_LONG`)
- ✅ When a file is deleted by the job, an entry is inserted into `deleted_files_log` table.
- ✅ The cron job handles I/O exceptions gracefully and logs errors.
- ✅ Configuration allows tuning the time threshold (e.g., 30 days).
- ✅ Unit/integration tests are written for the core logic.

</details>

### [25] [BE] Add video_type column to video_source table

**Link**: https://trello.com/c/s4ghHM16

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-22 |
| Labels | Doing |
| Comments | 1 |

<details>
<summary>View Description</summary>

**Description:**

As a backend developer, I want to add a new column `video_type` to the `video_source` table, so that each video can be categorized by type (e.g., education, entertainment, funny, etc.) and used for filtering on the frontend.

**Current State:**

- The `video_source` table currently stores general information such as `id`, `title`, `url`, `duration`, etc.
- There is **no field** to categorize videos by type.
- Filtering by video type is currently impossible at the database level.

**Desired State:**

- Add a new column `video_type` to the `video_source` table.
- `video_type` should be a **string or enum** (depending on DB engine).
- Existing records can have a **default value** of `'general'` or `NULL`.
- Future inserts must include a valid `video_type` value.
- The column will be used by the `/api/videos?type={type}` endpoint to filter results.
- Migration should be **idempotent** and **backward-compatible**.

**Acceptance Criteria:**

✅ Database schema updated to include `video_type` column
✅ Column type: VARCHAR(50) (or ENUM if DB supports)
✅ Default value: `'general'` (for backward compatibility)
✅ All existing services using `video_source` remain functional
✅ ORM or entity model updated to include `video_type` field
✅ Migration script (e.g., `V20251019__add_video_type_column.sql`) created and tested
✅ The new column is reflected in the API response payload
✅ Unit tests updated to verify read/write of `video_type`

</details>

### [23] [BE] Video Type Filtering API

**Link**: https://trello.com/c/jIP0Urmy

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-22 |
| Labels | Doing |
| Assigned | 1 member(s) |

<details>
<summary>View Description</summary>

**Description:**

As a backend developer, I want to provide an API endpoint that filters and returns videos by type, so that the frontend can display categorized video lists dynamically.

**Current State:**

- The current `/api/videos` endpoint returns **all videos**.
- There is **no query parameter or filter** to retrieve by type.
- The video model already includes a `type` field (e.g., `"education"`, `"entertainment"`, `"funny"`).

**Desired State:**

- Extend the existing `/api/videos` endpoint to accept an optional query parameter `type`.
- When `type` is provided, the endpoint returns **only videos matching that type**.
- When `type` is missing or “all”, it returns all videos.
- The API should support pagination and caching (if available).
- Validation should ensure that invalid `type` values (e.g., `type=unknown`) return HTTP 400 or an empty list.
- Add proper logging for API access and performance.

**Acceptance Criteria:**

✅ `GET /api/videos?type=education` returns only videos with type `education`
✅ `GET /api/videos` or `GET /api/videos?type=all` returns all videos
✅ Invalid types (e.g., `GET /api/videos?type=abc`) return 400 or an empty list
✅ API responds within acceptable latency (e.g., < 300ms for cached results)
✅ Proper validation and error handling implemented
✅ Unit/integration tests cover all scenarios
✅ OpenAPI/Swagger doc updated with the new query parameter

</details>

---

## Backlog/To Do

**List ID**: 686775822d1e1fdb24e5a152
**Card Count**: 1

### [5] [ONBOARDING] Setup & Explore funny-app – Assigned to: @new_joiner

**Link**: https://trello.com/c/ycx3Jlj4

| Property | Value |
|----------|-------|
| Last Activity | 2025-07-21 |
| Checklist | 0/22 |

<details>
<summary>View Description</summary>

### **Description:**

Welcome to the team! 🎉
This onboarding task will help you get up and running with the `funny-app` source code. You’ll clone the repo, set up your environment, run the project locally, and understand how the system is structured.

Use this task as a checklist and feel free to ask your mentor/buddy if you get stuck. Happy onboarding! 🚀

---

### 🧩 **Goals:**

- Run the `funny-app` locally in your environment
- Understand project structure and key services
- Run and pass at least one test
- Get ready to contribute your first PR

---

### 🛠 **Acceptance Criteria:**

- ✅ Can build and run `funny-app` locally
- ✅ Knows how to navigate the codebase
- ✅ Can explain 1 core feature flow (e.g., video stream or thumbnail gen)
- ✅ Environment is properly configured (IDE, DB, etc.)
- ✅ Made a small test PR (e.g., README fix or logging improvement)

</details>

---

## Backlog/To Do

**List ID**: 6865e44bdaadfa536119c5c8
**Card Count**: 7

### [9] Backend – Implement Like/Unlike and View Count APIs

**Link**: https://trello.com/c/32PhAcaS

| Property | Value |
|----------|-------|
| Last Activity | 2025-07-03 |

<details>
<summary>View Description</summary>

As a backend developer, I want to implement REST APIs to support liking/unliking videos and tracking total views, so that the frontend can display video popularity and allow user interaction.

---

### **Acceptance Criteria:**

#### 🔹 **Like/Unlike**

- ✅ POST `POST /videos/{id}/like`: increment like count (idempotent per session/IP if needed)
- ✅ POST `POST /videos/{id}/unlike`: decrement like count (min = 0)
- ✅ Total like count is stored in DB (e.g., `video_likes` table or field in `video_source`)

#### 🔹 **View Count**

- ✅ Each time video is accessed (or started), `POST /videos/{id}/view` is called
- ✅ View count is incremented and persisted (e.g., `video_views` table or field)
- ✅ View should be throttled per IP/session (e.g., 1 view per hour/session)

</details>

### [10] Frontend – Implement Like/Unlike Button and View Tracking

**Link**: https://trello.com/c/aj2ud85R

| Property | Value |
|----------|-------|
| Last Activity | 2025-07-03 |

<details>
<summary>View Description</summary>

### **Description:**

As a user, I want to like/unlike a video and see how many views and likes it has, so that I can engage with content and know its popularity.

---

### **Acceptance Criteria:**

- ✅ Show total **views** and **likes** on video detail page
- ✅ Add **like/unlike toggle button** (heart or thumbs up/down)
- ✅ Call backend APIs to track likes and views:
  - `POST /videos/{id}/like`
  - `POST /videos/{id}/unlike`
  - `POST /videos/{id}/view` (on page load or video start)
- ✅ Prevent duplicate likes per session (store locally)
- ✅ Optimistic UI update (increment like count immediately)

</details>

### [17] Automate Nginx Deployment & Reload via GitHub Actions (CI/CD)

**Link**: https://trello.com/c/tT9HPz6p

| Property | Value |
|----------|-------|
| Last Activity | 2025-07-05 |

<details>
<summary>View Description</summary>

### **Description:**

As a DevOps engineer, I want to automate the deployment and reloading of Nginx configuration using GitHub Actions, so that changes to nginx-related files in the repository are safely and consistently applied to the remote server.

---

### **Current State:**

- Nginx is already installed and manually configured on the server
- Nginx configs are not yet under version control or automated deployment
- Reloading Nginx currently requires SSH and manual commands

---

### **Desired State:**

- Any updates to Nginx config files (e.g., `nginx.conf`, `site.conf`) committed to Git are automatically:
  - Deployed to remote server via GitHub Actions
  - Verified for syntax correctness
  - Trigger a `nginx -s reload` or `systemctl reload nginx` on server
- Deployment should be **secure, idempotent**, and possibly support multiple environments (staging/production)

---

### **Acceptance Criteria:**

- ✅ GitHub Action workflow is triggered on `push` to `main` or specific folder (`/nginx-config/`)
- ✅ Config files are deployed via SSH/SCP to correct server location
- ✅ Workflow runs `nginx -t` to validate syntax before applying
- ✅ Nginx is reloaded only if validation passes
- ✅ Secrets (SSH key, host IP) are securely stored in GitHub Secrets
- ✅ Logs are viewable in GitHub Actions UI

</details>

### [19] Evaluate using Temporal to replace scheduled jobs in Spring Boot

**Link**: https://trello.com/c/VqITzd6y

| Property | Value |
|----------|-------|
| Last Activity | 2025-07-07 |

<details>
<summary>View Description</summary>

**📌 Description:**

Assess the feasibility of using [http://Temporal.io](http://Temporal.io "smartCard-inline")  to replace current `@Scheduled` jobs in our Spring Boot application. The goal is to evaluate how well Temporal fits into our system as a workflow engine compared to traditional scheduling.

---

**🔍 Evaluation Objectives:**

- Compare Temporal with `@Scheduled`, Quartz, and other scheduling mechanisms
- Review Temporal's support for:
  - Retry policies
  - Timeout and error handling
  - State persistence and observability
  - Human-in-the-loop workflows (if needed)
- Assess integration with Spring Boot and Java SDK
- Consider operational complexity: UI/CLI tools, deployment model (Temporal Cloud vs. self-hosted)
- Evaluate debugging, testing, and maintainability benefits
- Identify required changes to migrate from current scheduled jobs

</details>

### [20] [TECH] Move Java Package Classification Lists to Spring Config with Enable Flags

**Link**: https://trello.com/c/vUgyGXoB

| Property | Value |
|----------|-------|
| Last Activity | 2025-07-09 |

<details>
<summary>View Description</summary>

## **User Story**

**As a developer**, I want to move hardcoded lists of Java native packages, external packages, and basic types into the Spring Boot YAML configuration, so that these lists are configurable and can be turned on/off individually via flags.

---

## **Current State**

The `abstractness-instability-calculator` currently uses three hardcoded collections in code:

- `JAVA_NATIVE_PACKAGES` (List of core/native Java packages)
- `JAVA_EXTERNAL_PACKAGES` (List of commonly used external libraries)
- `BASIC_TYPES` (Set of Java primitive and boxed/common types)

These are defined as static constants in Java code, which makes them harder to maintain, extend, or override without rebuilding the application.

There’s no way to disable the use of one or more of these lists at runtime.

---

## **Desired State**

- All three collections (`JAVA_NATIVE_PACKAGES`, `JAVA_EXTERNAL_PACKAGES`, `BASIC_TYPES`) are moved to the Spring Boot configuration file (`application.yml`).
- Each list has its own `enabled` flag to allow toggling that list's usage at runtime.
- A configuration properties class is created and injected via `@ConfigurationProperties`.
- The calculator logic uses the config bean and respects the `enabled` flags.
- Static constants are removed or refactored out.

‌

---

## **Acceptance Criteria**

✅ The following keys are added to `application.yml`:

```
instability-calculator:
  native-packages:
    enabled: true
    values:
      - java.
      - javax.
      - jakarta.
      - sun.
      - com.sun.
      - org.w3c.
      - org.xml.
      - org.omg.
      - org.ietf.
      - jdk.
      - org.apache.xerces.
      - org.relaxng.

  external-packages:
    enabled: true
    values:
      - org.springframework.
      - org.apache.
      - com.google.common.
      - org.junit.
      - org.mockito.
      - org.slf4j.
      - org.logback.
      - org.hibernate.
      - com.fasterxml.jackson.
      - com.google.api
      - org.assertj.
      - org.aspectj.
      - io.micrometer.
      - io.swagger.
      - io.jsonwebtoken.
      - org.json.
      - com.google.zxing.
      - com.google.auth.

  basic-types:
    enabled: true
    values:
      - boolean
      - byte
      - char
      - short
      - int
      - long
      - float
      - double
      - void
      - java.lang.Boolean
      - java.lang.Byte
      - java.lang.Character
      - java.lang.Short
      - java.lang.Integer
      - java.lang.Long
      - java.lang.Float
      - java.lang.Double
      - java.lang.Void
      - java.lang.String
      - java.lang.Object
      - java.lang.Class
```

✅ A Spring `@ConfigurationProperties(prefix = "instability-calculator")` class is created and mapped properly.

✅ The original static constants are removed, and the logic now accesses the lists via injected config bean.

✅ Unit tests or integration tests verify that the values are loaded correctly and used in the analyzer logic.

✅ It’s possible to override or extend these lists via `application.yaml` or `application-dev.yaml` without touching the codebase.

</details>

### [24] [FE] Video Type Filtering UI

**Link**: https://trello.com/c/Ty56seMW

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-19 |

<details>
<summary>View Description</summary>

**Description:**

As a frontend developer, I want to allow users to filter and view videos by type (e.g., Education, Entertainment, Funny, etc.), so that users can easily find videos matching their interests.

**Current State:**

- The video list page currently shows **all videos** in one view.
- There is **no filter or category tab** for video types.
- The video API already provides `type` metadata for each video.

**Desired State:**

- The UI should display **tabs or buttons** representing available video types (e.g., “All”, “Education”, “Entertainment”, “Funny”).
- When a user clicks a tab, the frontend should **load and display only videos of that type**.
- The selected tab should be **highlighted** to indicate active state.
- The video list should **re-render smoothly** without full page reload (e.g., using React state or fetch update).
- The frontend should call a backend API endpoint like `/api/videos?type={type}` to get filtered results.
- The UI must work in both **light mode** and **dark mode**.

**Acceptance Criteria:**

✅ Tabs for each video type are displayed on top of the video list
✅ Clicking a tab triggers an API call to fetch videos of that type
✅ Video list updates dynamically without full page reload
✅ The active tab is visually highlighted
✅ Default tab “All” shows all videos
✅ API errors (e.g., network fail) show a user-friendly message
✅ Responsive on mobile and desktop views

</details>

### [26] Create Trello MCP Server in Python

**Link**: https://trello.com/c/LLZek6TM

| Property | Value |
|----------|-------|
| Last Activity | 2026-02-13 |

<details>
<summary>View Description</summary>

Title: Create Trello MCP Server in Python

Description:
Implement a Trello MCP server using Python. The project should use pip, virtual environment (venv), and a requirements.txt file to manage dependencies. The source code will be placed under the directory: mcp/mcp-trello.

Requirements:

- Use Python (latest stable version available in the project).
- Use venv for environment isolation.
- Provide a requirements.txt file for dependency installation.
- Implement basic MCP server structure and configuration.
- Ensure the project can be installed and started with clear setup instructions.

Setup Steps (expected):

1. Create virtual environment.
2. Install dependencies via requirements.txt.
3. Run the MCP server locally.

Acceptance Criteria:

- Project runs locally without errors after following README steps.
- Dependencies can be installed using pip install -r requirements.txt.
- Source code is located in mcp/mcp-trello.
- Basic Trello MCP server starts and responds (health check or minimal functionality).

Tasks:

- Initialize project structure.
- Create virtual environment and requirements.txt.
- Implement basic MCP server skeleton.
- Add README with installation and run instructions.
- Verify server runs locally.

Definition of Done:

- Code committed to repository.
- README clearly explains setup and run steps.
- Server can be started successfully in a clean environment.

</details>

---

## Deployed

**List ID**: 6865e4a267ea372ce23cbecc
**Card Count**: 14

### [3] Persist Video Hit Stats to Database for Cleanup Support

**Link**: https://trello.com/c/7FSHzLox

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Merged, Deployed, Done |

<details>
<summary>View Description</summary>

### **User Story:**

As a system owner, I want to persist video hit statistics (both from cache and disk) into the database, so that we can analyze usage and support the cleanup job in deleting old or rarely accessed files.

---

### **Current State:**

- A background job already tracks the number of times a video is accessed from **cache** and **disk**.
- However, this data is **kept only in memory** and is **lost when the application restarts**.
- The cleanup cronjob currently **does not have access** to hit count data, making deletion decisions based only on last modified time or heuristics.

---

### **Desired State:**

- A dedicated table (`video_access_stats`) is created to **persist video hit statistics**.
- Whenever a video is accessed, its **hit count is incremented** and **last accessed timestamp is updated** in the database.
- The cronjob responsible for cleaning up old files will **query this table** to determine which files are least viewed.
- The data remains available across restarts and can support long-term analysis and auditing.

---

### **Acceptance Criteria:**

- ✅ A new table `video_access_stats` is created with:
  - `video_id` (string or UUID)
  - `hit_count` (integer)
  - `last_accessed_at` (timestamp)
- ✅ When a video is accessed:
  - If the `video_id` exists: increment `hit_count`, update `last_accessed_at`.
  - If it doesn’t exist: insert a new row with initial hit count and timestamp.
- ✅ The implementation is **concurrent-safe** and performs well under load.
- ✅ The cleanup cronjob can now query `video_access_stats` and make deletion decisions based on low usage.
- ✅ Unit and/or integration tests cover hit tracking and DB updates.

</details>

### [21] Email handle

**Link**: https://trello.com/c/s9qpgCHP

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Done, Merged, Deployed |

<details>
<summary>View Description</summary>

## **User Story**

**As a system owner**, I want to enable preview mode to limit the number of emails sent per day, while allowing a configurable whitelist of email addresses (provided via environment variable, with a default fallback), so that we can safely test in production-like environments without impacting real users.

---

## **Current State**

- The system currently sends emails without any global daily cap or sampling..
- No preview mode or limit is enforced.
- There’s no way to bypass email limits for critical testers or internal teams.
- Whitelist is not configurable via environment, making it hard to override without changing code or YAML files.

---

## **Desired State**

- A previewMode is introduced with:
  - A flag to enable/disable it.
  - A percentage-based sampling (e.g., 10% of total).
  - A hard cap on the number of emails sent per day.
- A whitelist of email addresses is injected via environment variable, using comma-separated format (,) to support dynamic updates without modifying application.yml.).

‌

## **Acceptance Criteria**

✅ In `application.yml`:

```
email:
  preview-mode:
    enabled: true
    max-daily-emails: 1000
    percentage: 10
    whitelist: ${EMAIL_PREVIEW_WHITELIST:abc.com}
```

✅ Behavior:

- When `preview-mode.enabled = false`: send all emails.
- When `preview-mode.enabled = true`:
  - Emails **to addresses matching the whitelist** (by exact match or domain match, e.g., `endsWith("@abc.com")`) are **always sent**, not counted toward the daily cap.
  - All others follow `percentage` sampling and `max-daily-emails` enforcement.

✅ Whitelist parsing:

- Read `whitelist` as a comma-separated string from env or fallback value.
- Trim and normalize list.

</details>

### [8] [FE] Build Frontend UI for Anonymous Commenting

**Link**: https://trello.com/c/UD1ezAJn

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Done, Merged, Deployed |

<details>
<summary>View Description</summary>

### **Description:**

As a user (guest or logged-in), I want to post and reply to comments on a video page without being required to log in, so that the system is more open and engaging.

### **Acceptance Criteria:**

- ✅ Comments section is visible to all visitors
- ✅ Users can:
  - View all comments (sorted)
  - Post comment as guest (enter guest name)
  - Reply to existing comments
  - (Optional) Delete their own comment (if tracking guest session/token)
- ✅ Input fields:
  - Text area for comment
  - Guest name (if not logged in)

</details>

### [7] [BE]Implement Comment API in Spring Boot (No Login Required

**Link**: https://trello.com/c/szyzNFJq

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Deployed, Merged, Done |

<details>
<summary>View Description</summary>

### **Description:**

As a backend developer, I want to build public comment APIs that allow both logged-in and anonymous users to post and view comments, so that we can enable community interaction without requiring login.

### **Acceptance Criteria:**

- ✅ Public endpoints:
  - `GET /videos/{id}/comments`
  - `POST /videos/{id}/comments` — accepts either `user_id` (if logged in) or `guest_name`
  - `DELETE /comments/{id}` — only for authenticated users or guest with proper token (if applied)
- ✅ Validations:
  - At least one of `user_id` or `guest_name` must be present
  - `content` must not be empty
- ✅ Replies are returned in nested structure (if needed)
- ✅ API is protected against spam (basic rate limit / captcha if needed)

</details>

### [6] Design Database Schema for Comment Feature

**Link**: https://trello.com/c/HVx6ahoE

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Merged, Done, Deployed |

<details>
<summary>View Description</summary>

### **Description:**

As a developer, I want to design and create a database table to store user comments on videos so that we can support interaction and discussions.

### **Acceptance Criteria:**

- ✅ A new `video_comments` table is created with the following fields:
  - `id` (UUID or auto-increment)
  - `video_id` (foreign key)
  - `user_id` (foreign key or string)
  - `content` (text)
  - `created_at` (timestamp)
  - `updated_at` (timestamp)
  - `parent_id` (nullable, for nested replies)
- ✅ Supports nesting replies (via `parent_id`).
- ✅ DB migration scripts are written and tested.
- ✅ Indexes are added for performance (e.g., `video_id`, `created_at`).

</details>

### [18] Integrate SonarCloud Code Analysis into GitHub Actions CI

**Link**: https://trello.com/c/e6mxAork

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Done, Merged, Deployed |
| Assigned | 1 member(s) |

<details>
<summary>View Description</summary>

### **Description**:

As a developer, I want to integrate SonarCloud static code analysis into the GitHub Actions CI pipeline so that I can monitor code quality, detect code smells, bugs, and maintainability issues automatically on every commit.

---

### 📋 **Acceptance Criteria**:

- ✅ The `pom.xml` is configured with SonarCloud properties:
  - `sonar.organization=canh-labs`
  - `sonar.host.url=https://sonarcloud.io`
- ✅ `sonar-maven-plugin` is added to `pom.xml` (or implicitly used)
- ✅ GitHub Actions CI includes:
  - Java setup (JDK 21)
  - Cache for Maven and Sonar
  - Step to run `mvn verify sonar:sonar`
- ✅ `SONAR_TOKEN` is stored in GitHub Secrets
- ✅ The SonarCloud analysis is successfully triggered and visible on [https://sonarcloud.io](https://sonarcloud.io "smartCard-inline")
- ✅ CI still builds the JAR and uploads artifact as before

</details>

### [4] Generate Video Thumbnail Using FFmpeg at 1 Second Mark

**Link**: https://trello.com/c/pztvy830

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Done, Deployed, Merged |
| Assigned | 1 member(s) |
| Comments | 1 |

<details>
<summary>View Description</summary>

### **User Story:**

As a system, I want to automatically generate a video thumbnail image at the 1-second mark using FFmpeg after downloading the video from Google Drive, so that we can display preview images for users and store the thumbnail path for future use.

---

### **Current State:**

- There is already a working job that downloads video files from Google Drive and stores their metadata in the database.
- However, no thumbnail image is currently generated during or after this process.
- The `thumbnail_path` column has already been added to the `video_source` table (see previous task).

---

### **Desired State:**

- After a video is downloaded, a thumbnail image is generated automatically using `ffmpeg` at the 1-second mark of the video.
- The generated image is saved to a specified path (e.g., `/thumbnails/{videoId}.jpg` or from config).
- The `thumbnail_path` in the DB is updated with the actual file path or URL of the generated image.
- Any failures in thumbnail generation are logged and do not block the video import process.

---

### **Acceptance Criteria:**

- ✅ FFmpeg is used to extract a single frame at 1 second (`-ss 1 -vframes 1`).
- ✅ The thumbnail is saved in a configured directory with a consistent naming convention (e.g., `{videoId}.jpg`).
- ✅ The thumbnail file is generated successfully for all standard video formats (mp4, mov, etc.).
- ✅ `thumbnail_path` in the `video_source` table is updated with the correct path after generation.
- ✅ If FFmpeg fails, the error is logged but does not interrupt the download job.
- ✅ Unit/integration tests are added (if applicable, e.g., mocking FFmpeg call).

</details>

### [11] Insert thumbnail_path During Google Drive Download Cron Job

**Link**: https://trello.com/c/kv5mKgz7

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Done, Merged, Deployed |
| Assigned | 1 member(s) |

<details>
<summary>View Description</summary>

### **Description:**

As a system, I want the cron job that downloads videos from Google Drive to also generate a thumbnail image and update the `thumbnail_path` column in the database, so that the thumbnail is available immediately after download.

---

### **Current State:**

- A cron job already exists that downloads video files from Google Drive and saves metadata (e.g., file path, video_id) into the database.
- The column `thumbnail_path` has already been added to the `video_source` table.
- The system already supports generating a thumbnail using `ffmpeg`.

---

### **Desired State:**

- After each video is downloaded:
  - A thumbnail is generated using `ffmpeg` (at 1 second mark).
  - The generated image is saved to a configured directory (e.g., `/thumbnails/{videoId}.jpg`).
  - The `thumbnail_path` in the `video_source` table is updated accordingly.

---

### **Acceptance Criteria:**

- ✅ After each video download, a thumbnail image is generated using FFmpeg.
- ✅ The image is saved to a consistent and accessible path.
- ✅ The `thumbnail_path` column in the `video_source` table is updated with the image path or URL.
- ✅ If thumbnail generation fails, the error is logged, and the cron job continues.
- ✅ This is done automatically inside the cron job workflow — no manual trigger required.

</details>

### [22] Add TaskUtils helper for structured parallel task execution using Virtual Threads

**Link**: https://trello.com/c/cBBLdayX

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Done, Merged, Deployed |

<details>
<summary>View Description</summary>

### 🧾 **User Story**

As a backend developer, I want a utility class `TaskUtils` that uses `StructuredTaskScope` and virtual threads, so that I can run multiple parallel tasks safely and efficiently, with clean error handling and support for both fail-fast and error-tolerant execution patterns.

---

### 🧩 **Current State**

Developers currently rely on `CompletableFuture` or custom threading logic for parallel task execution.

This results in repetitive code for managing errors, cancellations, and joining task results.

There is no common utility supporting structured concurrency introduced in Java 21+.

---

### 🎯 **Desired State**

A reusable utility class `TaskUtils` is implemented using Java's `StructuredTaskScope` to manage structured parallel execution.

The utility provides two main methods:

- `runAll(...)`: runs all tasks in parallel and fails fast on the first error
- `runAllIgnoreError(...)`: runs all tasks in parallel and collects both successes and failures

Tasks are executed using virtual threads for maximum scalability.

A result wrapper class `Result<T>` is introduced to encapsulate either a success value or an exception.

---

### ✅ **Acceptance Criteria**

✅ A new utility class `TaskUtils` is implemented with:

- `<T> List<T> runAll(List<Callable<T>> tasks) throws Exception;`
  → Uses `StructuredTaskScope.ShutdownOnFailure`
  → Cancels remaining tasks on the first failure
  → Returns ordered list of results
- `<T> List<Result<T>> runAllIgnoreError(List<Callable<T>> tasks);`
  → Uses basic `StructuredTaskScope`
  → Collects success and failure without throwing
  → Returns list of `Result<T>` objects

✅ `Result<T>` class is implemented with:

- `T value`
- `Throwable error`
- `boolean isSuccess()`
- Factory methods: `success(...)`, `failure(...) `

✅ Tasks are joined using `.join()`, and canceled automatically in fail-fast mode.

✅ Exception propagation is handled via `.throwIfFailed()`

✅ Unit tests cover:

- All tasks succeed
- One or more tasks fail
- Result ordering is preserved
- Exception handling and propagation
- Success/failure mix in `runAllIgnoreError`

✅ No third-party libraries are used (pure Java 21+)

</details>

### [1] Add is_hide and thumbnail_path Columns to video_source Table

**Link**: https://trello.com/c/vUkSZhsw

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Done, Merged, Deployed |
| Assigned | 1 member(s) |

<details>
<summary>View Description</summary>

### **Description:**

As a developer, I want to add `is_hide` and `thumbnail_path` columns to the `video_source` table so that we can support hiding videos and storing thumbnail images for better content management.

---

### **Acceptance Criteria:**

- ✅ A new column `is_hide` of type `BOOLEAN` is added to the `video_source` table.
  - Default value is `FALSE`.
  - Indicates whether the video should be hidden in the UI.
- ✅ A new column `thumbnail_path` of type `VARCHAR` (or equivalent) is added to the `video_source` table.
  - Stores the path or URL to the video’s thumbnail image.(path value = ./icon/{filedId}.jpg
- ✅ Database migration is created and tested successfully.
- ✅ ORM/entity (if used) is updated accordingly.
- ✅ Code is reviewed and merged into the main branch.
- ✅ Unit or integration tests (if applicable) are updated or added.

</details>

### [14] Add Virtual Thread Metrics to Monitor Performance

**Link**: https://trello.com/c/gaudma5I

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Done, Merged, Deployed |
| Assigned | 1 member(s) |

<details>
<summary>View Description</summary>

### **Description:**

**As a developer, I want to add monitoring for Virtual Threads in our Spring Boot service, so that we can observe concurrency behavior, detect bottlenecks, and optimize resource usage when using Project Loom.**

---

### **Acceptance Criteria:**

- **✅ System exposes metrics about virtual threads, such as:**
  - **Number of active virtual threads**
  - **Completed thread count**
  - **Average task duration (if executor supports)**
- **✅ Metrics are visible via actuator endpoint (e.g., /actuator/metrics)**
- **✅ Metrics are exportable to Prometheus/Grafana if enabled**
- **✅ Logging includes virtual thread names (e.g., \@VirtualThread) for easier debugging**
- **✅ No performance degradation after metrics added**

</details>

### [16] Configure Nginx to Serve Images on images.canh-labs.com

**Link**: https://trello.com/c/NgV0xAOL

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Done, Deployed |
| Assigned | 1 member(s) |

<details>
<summary>View Description</summary>

### **Description:**

As a DevOps/developer, I want to enhance the current Nginx configuration to serve static image files under the subdomain `images.canh-labs.com`, so that image assets (e.g., video thumbnails) can be accessed securely and directly via a CDN-like endpoint.

---

### **Current State:**

- Nginx is already installed and running on the target server.
- Other services (e.g., backend on `api.canh-labs.com`) are already proxied via Nginx.
- Static files like images are currently stored locally on disk (e.g., `/var/www/images/thumbnails/`) but not served via public domain.

---

### **Desired State:**

- Domain `images.canh-labs.com` serves public image files from a configured folder (e.g., `/var/www/images/` or `/opt/data/thumbnails/`)
- HTTPS is enabled via Let’s Encrypt or other certs
- Proper content types are returned (`image/jpeg`, `image/png`)
- Directory listing is disabled
- System can access thumbnails like:
  ```
  https://images.canh-labs.com/thumbnails/abc123.jpg
  ```
  ‌
- CORS and caching headers are optionally configured to support frontend usage

---

### **Acceptance Criteria:**

- ✅ Domain `images.canh-labs.com` resolves to the server (DNS configured)
- ✅ Nginx serves static files correctly via `https://images.canh-labs.com/{filename}`
- ✅ SSL/TLS is enabled and auto-renewed (e.g., via Certbot)
- ✅ Static directory is mapped and isolated
- ✅ No directory browsing allowed
- ✅ Nginx is reloaded successfully with no downtime

</details>

### [15] Create C4 Architecture Diagrams – Levels 1, 2, and 3

**Link**: https://trello.com/c/lA0rpJbP

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Done, Merged |
| Assigned | 1 member(s) |

<details>
<summary>View Description</summary>

### **Description:**

As a team, we want to create a full set of C4 Model diagrams (L1–L3) to document and communicate the architecture of our system. This will help current and future developers, stakeholders, and reviewers understand the system’s scope, structure, and responsibilities.

---

### **Acceptance Criteria:**

- ✅ Level 1 (System Context Diagram):
  - Shows the main system, users, and external systems (e.g., Google Drive, Redis, etc.)
  - Defines system boundaries
- ✅ Level 2 (Container Diagram):
  - Shows main containers: Web frontend, Backend service, Database, Redis, etc.
  - Explains responsibilities and technologies used
  - Includes communication lines (e.g., HTTP, JDBC, Redis, etc.)
- ✅ Level 3 (Component Diagram):
  - Zoom into Backend container
  - Show key components/modules: API controllers, services, repositories, cache, cron jobs, etc.
- ✅ Diagrams are version-controlled (e.g., in `docs/architecture/`)
- ✅ Use tools like Structurizr DSL, [http://draw.io](http://draw.io "smartCard-inline") , or PlantUML for diagrams

</details>

### [12] Create ADR Documentation for System Architecture

**Link**: https://trello.com/c/LozrV954

| Property | Value |
|----------|-------|
| Last Activity | 2025-10-15 |
| Labels | Done, Merged |
| Assigned | 1 member(s) |
| Comments | 1 |

<details>
<summary>View Description</summary>

### **Description:**

As a team, we want to document key architecture decisions in ADR (Architecture Decision Records) format for the core components of the system — including Database, Backend Service, Frontend, and Caching — so that we can ensure consistent communication and easier onboarding for future developers.

---

### **Acceptance Criteria:**

- ✅ An ADR document is created and stored (in `/docs/adr/` or shared Notion/Confluence/Repo)
- ✅ Each ADR should follow a standard format, e.g., MADR or a simplified template:
  - Title
  - Context
  - Decision
  - Consequences
- ✅ ADRs must be created for the following components:
  - Database schema and structure (e.g., normalization, naming, relationships)
  - Backend service architecture (e.g., REST design, authentication, modular structure)
  - Frontend structure (e.g., framework choice, state management, component strategy)
  - Caching strategy (e.g., Redis usage, TTL, keys pattern)

</details>

---
