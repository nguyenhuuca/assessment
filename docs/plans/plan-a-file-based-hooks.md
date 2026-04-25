# Plan A: Trello Webhook → Claude Code File-Based Hooks

## Overview

**Approach:** File-based trigger system where Trello webhooks write task files that Claude Code monitors and auto-processes.

**Philosophy:** "Don't call Claude, let Claude discover the work"

## Architecture

```
┌─────────────┐      ┌──────────────┐      ┌─────────────────┐
│   Trello    │─────▶│ Spring Boot  │─────▶│ Write Task File │
│  (webhook)  │      │   Endpoint   │      │ ~/.claude/tasks/│
└─────────────┘      └──────────────┘      └─────────────────┘
                                                      │
                                                      ▼
                                            ┌─────────────────┐
                                            │ Claude Code     │
                                            │ File Watcher    │
                                            │ Hook (startup)  │
                                            └─────────────────┘
                                                      │
                                                      ▼
                                            ┌─────────────────┐
                                            │ Auto spawn:     │
                                            │ claude-code CLI │
                                            │ implements task │
                                            └─────────────────┘
                                                      │
                                                      ▼
                                        ┌───────────────────────────┐
                                        │ Git branch → Commit → PR  │
                                        └───────────────────────────┘
```

## Why This Approach?

### Pros ✅

1. **Decoupled:** Spring Boot doesn't need to know about Claude Code internals
2. **Reliable:** File system is persistent, survives crashes
3. **Observable:** Easy to debug - just check task files
4. **Extensible:** Multiple sources can write tasks (Trello, Jira, Slack, etc.)
5. **Queueing:** Natural queue system via file system
6. **Status tracking:** Can mark files as processing/done
7. **Full context:** Claude Code has complete codebase access
8. **Auto git operations:** Claude Code handles branch, commit, PR

### Cons ⚠️

1. **Server setup:** Requires Claude Code CLI installed on server
2. **Daemon process:** Need a background watcher process
3. **File system overhead:** Disk I/O for each task
4. **Polling latency:** Small delay (1-5 seconds) between write and detection

## Implementation Details

### 1. Task File Format

**Location:** `~/.claude/tasks/pending/`

**File naming:** `{timestamp}_{trello_card_id}.json`

**Schema:**
```json
{
  "id": "trello_card_123",
  "source": "trello",
  "title": "Implement user authentication",
  "description": "Add JWT-based authentication to the API endpoints...",
  "priority": "high",
  "labels": ["backend", "security"],
  "createdAt": "2026-02-08T10:30:00Z",
  "metadata": {
    "trelloCardUrl": "https://trello.com/c/abc123",
    "requestedBy": "user@example.com"
  }
}
```

### 2. Spring Boot Service

**File:** `api/src/main/java/com/canhlabs/funnyapp/service/impl/TaskFileServiceImpl.java`

```java
@Service
@Slf4j
public class TaskFileServiceImpl implements TaskFileService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Async
    public void createTaskFile(String cardId, String title, String description, Map<String, Object> metadata) {
        try {
            // 1. Create task DTO
            TaskFileDTO task = TaskFileDTO.builder()
                    .id(cardId)
                    .source("trello")
                    .title(title)
                    .description(description)
                    .priority("normal")
                    .createdAt(Instant.now().toString())
                    .metadata(metadata)
                    .build();

            // 2. Generate filename
            String timestamp = System.currentTimeMillis();
            String filename = String.format("%d_%s.json", timestamp, cardId);

            // 3. Write to pending directory
            Path taskDir = Paths.get(appProperties.getClaudeTasksDir(), "pending");
            Files.createDirectories(taskDir);

            Path taskFile = taskDir.resolve(filename);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(taskFile.toFile(), task);

            log.info("Created task file: {}", taskFile);

        } catch (Exception e) {
            log.error("Failed to create task file for card {}: {}", cardId, e.getMessage(), e);
            throw new CustomException("Failed to create task file");
        }
    }
}
```

**Webhook Integration:**
```java
@Service
public class TrelloWebhookServiceImpl implements TrelloWebhookService {

    private final TaskFileService taskFileService;

    @Override
    @Async
    public void processWebhook(TrelloWebhookDTO payload) {
        // ... validate and parse webhook ...

        if ("doing".equalsIgnoreCase(labelName)) {
            Map<String, Object> metadata = Map.of(
                "trelloCardUrl", payload.getAction().getData().getCard().getUrl(),
                "boardId", payload.getModel().getId()
            );

            taskFileService.createTaskFile(
                cardId,
                cardName,
                cardDesc,
                metadata
            );
        }
    }
}
```

### 3. Claude Code File Watcher Hook

**Location:** `~/.claude/hooks/on-startup.sh`

**Implementation:**
```bash
#!/bin/bash
# Claude Code startup hook - watches for new task files

TASKS_DIR="$HOME/.claude/tasks/pending"
PROCESSING_DIR="$HOME/.claude/tasks/processing"
COMPLETED_DIR="$HOME/.claude/tasks/completed"

mkdir -p "$TASKS_DIR" "$PROCESSING_DIR" "$COMPLETED_DIR"

# Start file watcher in background
while true; do
    # Find new task files
    for task_file in "$TASKS_DIR"/*.json; do
        [ -e "$task_file" ] || continue  # No files found

        echo "Found new task: $task_file"

        # Move to processing
        filename=$(basename "$task_file")
        mv "$task_file" "$PROCESSING_DIR/$filename"

        # Extract task info
        task_id=$(jq -r '.id' "$PROCESSING_DIR/$filename")
        task_title=$(jq -r '.title' "$PROCESSING_DIR/$filename")
        task_desc=$(jq -r '.description' "$PROCESSING_DIR/$filename")

        # Spawn Claude Code CLI
        echo "Spawning claude-code for task: $task_title"

        cd "$HOME/projects/assessment" || exit

        claude-code --non-interactive <<EOF
Implement the following task:

Title: $task_title

Description:
$task_desc

Follow existing code patterns in the codebase.
Create a feature branch, implement the code, write tests, commit, and create a PR.

Task ID: $task_id
EOF

        # Mark as completed
        mv "$PROCESSING_DIR/$filename" "$COMPLETED_DIR/$filename"
        echo "Completed task: $task_title"
    done

    # Wait 5 seconds before next check
    sleep 5
done
```

**Alternative: Using inotify (Linux) for instant detection:**
```bash
#!/bin/bash
# More efficient - triggers immediately on file creation

TASKS_DIR="$HOME/.claude/tasks/pending"

inotifywait -m -e create "$TASKS_DIR" --format '%f' | while read filename; do
    if [[ $filename == *.json ]]; then
        echo "New task detected: $filename"
        # ... process task ...
    fi
done
```

### 4. Systemd Service (Linux Server)

**File:** `/etc/systemd/system/claude-task-watcher.service`

```ini
[Unit]
Description=Claude Code Task Watcher
After=network.target

[Service]
Type=simple
User=appuser
WorkingDirectory=/home/appuser
ExecStart=/home/appuser/.claude/hooks/on-startup.sh
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

**Enable and start:**
```bash
sudo systemctl enable claude-task-watcher
sudo systemctl start claude-task-watcher
sudo systemctl status claude-task-watcher
```

### 5. Configuration

**AppProperties.java:**
```java
@Configuration
@ConfigurationProperties("app")
public class AppProperties {
    // ... existing properties ...

    private String claudeTasksDir = System.getProperty("user.home") + "/.claude/tasks";

    // Getters and setters
}
```

**application.yaml:**
```yaml
app:
  claude-tasks-dir: ${CLAUDE_TASKS_DIR:~/.claude/tasks}
```

### 6. Task Status Tracking

**Enhanced schema with status:**
```json
{
  "id": "trello_card_123",
  "status": "pending",  // pending | processing | completed | failed
  "title": "...",
  "description": "...",
  "attempts": 0,
  "lastError": null,
  "startedAt": null,
  "completedAt": null,
  "result": {
    "branch": "feature/123-task-name",
    "prUrl": "https://github.com/user/repo/pull/45",
    "commitSha": "abc123"
  }
}
```

**Update after completion:**
```bash
# In hook script after claude-code completes
jq '.status = "completed" | .completedAt = now | .result.branch = "'"$branch"'"' \
    "$PROCESSING_DIR/$filename" > "$COMPLETED_DIR/$filename"
```

## Deployment Steps

### Development Setup

1. **Install Claude Code CLI:**
   ```bash
   npm install -g @anthropic/claude-code
   ```

2. **Create directories:**
   ```bash
   mkdir -p ~/.claude/tasks/{pending,processing,completed}
   mkdir -p ~/.claude/hooks
   ```

3. **Install hook script:**
   ```bash
   cp hooks/on-startup.sh ~/.claude/hooks/
   chmod +x ~/.claude/hooks/on-startup.sh
   ```

4. **Start watcher:**
   ```bash
   ~/.claude/hooks/on-startup.sh &
   ```

5. **Configure Spring Boot:**
   ```bash
   # In api/.env
   CLAUDE_TASKS_DIR=$HOME/.claude/tasks
   ```

### Production Setup (Server)

1. **Install dependencies:**
   ```bash
   sudo apt-get install -y inotify-tools jq
   npm install -g @anthropic/claude-code
   ```

2. **Setup systemd service:**
   ```bash
   sudo cp claude-task-watcher.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable claude-task-watcher
   sudo systemctl start claude-task-watcher
   ```

3. **Configure Trello webhook:**
   ```bash
   curl -X POST "https://api.trello.com/1/webhooks/" \
     -H "Content-Type: application/json" \
     -d '{
       "key": "YOUR_TRELLO_API_KEY",
       "callbackURL": "https://your-domain.com/api/webhook/trello",
       "idModel": "BOARD_ID"
     }'
   ```

## Testing

### Unit Test

```java
@SpringBootTest
class TaskFileServiceImplTest {

    @Autowired
    private TaskFileService taskFileService;

    @TempDir
    Path tempDir;

    @Test
    void testCreateTaskFile() throws Exception {
        // Given
        String cardId = "test123";
        String title = "Test Task";
        String description = "Test description";

        // Configure temp directory
        ReflectionTestUtils.setField(taskFileService, "tasksDir", tempDir.toString());

        // When
        taskFileService.createTaskFile(cardId, title, description, Map.of());

        // Then
        Path pendingDir = tempDir.resolve("pending");
        assertTrue(Files.exists(pendingDir));

        List<Path> files = Files.list(pendingDir).collect(Collectors.toList());
        assertEquals(1, files.size());

        String content = Files.readString(files.get(0));
        assertTrue(content.contains(cardId));
        assertTrue(content.contains(title));
    }
}
```

### Integration Test

```bash
# Test manual task creation
cat > ~/.claude/tasks/pending/test_manual.json <<EOF
{
  "id": "manual_test",
  "source": "manual",
  "title": "Test task",
  "description": "This is a test",
  "createdAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

# Check logs
tail -f ~/.claude/tasks/watcher.log
```

### End-to-End Test

1. Create Trello card
2. Add "doing" label
3. Check Spring Boot logs: `tail -f api/logs/app.log`
4. Check task file created: `ls ~/.claude/tasks/pending/`
5. Check watcher processing: `tail -f ~/.claude/tasks/watcher.log`
6. Verify branch created: `git branch | grep feature/`
7. Verify PR created: `gh pr list`

## Monitoring & Observability

### Log Files

```bash
# Spring Boot logs
tail -f api/logs/app.log | grep TaskFile

# Watcher logs
tail -f ~/.claude/tasks/watcher.log

# Systemd logs
journalctl -u claude-task-watcher -f
```

### Metrics

```java
@Component
public class TaskFileMetrics {

    private final MeterRegistry registry;

    public TaskFileMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordTaskCreated() {
        registry.counter("tasks.created").increment();
    }

    public void recordTaskCompleted(String status) {
        registry.counter("tasks.completed", "status", status).increment();
    }
}
```

### Health Check

```bash
# Check watcher is running
ps aux | grep on-startup.sh

# Check pending tasks
ls -la ~/.claude/tasks/pending/

# Check processing tasks (should be empty or short-lived)
ls -la ~/.claude/tasks/processing/

# Check completed tasks
ls -la ~/.claude/tasks/completed/ | tail -10
```

## Error Handling

### Failed Tasks

**Retry logic in hook:**
```bash
max_attempts=3
current_attempt=$(jq -r '.attempts // 0' "$task_file")

if [ "$current_attempt" -lt "$max_attempts" ]; then
    # Increment attempt counter
    jq '.attempts += 1' "$task_file" > "$task_file.tmp"
    mv "$task_file.tmp" "$task_file"

    # Move back to pending for retry
    mv "$task_file" "$TASKS_DIR/"
else
    # Max attempts reached, mark as failed
    jq '.status = "failed" | .lastError = "Max attempts reached"' "$task_file" > "$FAILED_DIR/$filename"
fi
```

### Stuck Tasks

**Cleanup script:**
```bash
#!/bin/bash
# Remove tasks stuck in processing > 1 hour

find ~/.claude/tasks/processing -name "*.json" -mmin +60 | while read task; do
    echo "Found stuck task: $task"
    filename=$(basename "$task")
    mv "$task" ~/.claude/tasks/failed/
done
```

**Cron job:**
```cron
# Run cleanup every hour
0 * * * * /home/appuser/scripts/cleanup-stuck-tasks.sh
```

## Security Considerations

1. **File permissions:**
   ```bash
   chmod 700 ~/.claude/tasks/
   chmod 600 ~/.claude/tasks/*/*.json
   ```

2. **Input validation:**
   - Sanitize task descriptions (prevent code injection)
   - Validate JSON schema before processing
   - Limit file size (max 100KB per task)

3. **Rate limiting:**
   - Max 10 tasks per minute
   - Max 100 tasks per hour

4. **Isolation:**
   - Run watcher as dedicated user
   - Use Docker container for Claude Code execution

## Scalability

### Single Server

- **Throughput:** ~10-20 tasks/hour (depends on task complexity)
- **Queue size:** File system limited (~1000s of files)
- **Bottleneck:** Claude Code processing time

### Multi-Server (Future)

Use Redis + queue system:
```
Trello → Spring Boot → Redis Queue → Multiple Workers → Claude Code
```

## Cost Estimation

**Assumptions:**
- 10 tasks/day
- Average 2000 tokens input + 4000 tokens output per task
- Claude Sonnet 4.5 pricing

**Monthly cost:** ~$5-10

## Alternative: Polling Instead of File Watching

If file watching is complex, use polling from Spring Boot:

```java
@Scheduled(fixedRate = 30000) // Every 30 seconds
public void processTaskQueue() {
    Path pendingDir = Paths.get(appProperties.getClaudeTasksDir(), "pending");

    try (Stream<Path> files = Files.list(pendingDir)) {
        files.filter(path -> path.toString().endsWith(".json"))
             .forEach(this::spawnClaudeCode);
    }
}
```

## Summary

**Best for:**
- ✅ Production systems with high reliability needs
- ✅ Multiple task sources (Trello, Jira, Slack, etc.)
- ✅ Audit trail and status tracking required
- ✅ Decoupled architecture

**Not ideal for:**
- ❌ Simple use cases (overkill)
- ❌ Instant response needed (<1 second)
- ❌ Serverless environments
