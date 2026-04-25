# Plan B: Trello Webhook → Direct Claude Code CLI Spawn

## Overview

**Approach:** Direct process spawning where Spring Boot immediately executes Claude Code CLI when webhook is received.

**Philosophy:** "Immediate execution, simple pipeline"

## Architecture

```
┌─────────────┐      ┌──────────────┐      ┌─────────────────┐
│   Trello    │─────▶│ Spring Boot  │─────▶│  ProcessBuilder │
│  (webhook)  │      │   Endpoint   │      │ spawn claude-code│
└─────────────┘      └──────────────┘      └─────────────────┘
                                                      │
                                                      ▼
                                            ┌─────────────────┐
                                            │ Claude Code CLI │
                                            │ runs in process │
                                            └─────────────────┘
                                                      │
                                                      ▼
                                        ┌───────────────────────────┐
                                        │ Git branch → Commit → PR  │
                                        └───────────────────────────┘
```

## Why This Approach?

### Pros ✅

1. **Simple:** Minimal moving parts, direct execution
2. **Fast:** No file I/O, no polling delay
3. **Synchronous option:** Can wait for result if needed
4. **Less infrastructure:** No daemon/watcher needed
5. **Easy debugging:** Direct logs from Claude Code
6. **Full context:** Claude Code has complete codebase access
7. **Built-in features:** Auto git operations, testing, PR creation

### Cons ⚠️

1. **Process management:** Need to handle long-running processes
2. **Server setup:** Requires Claude Code CLI installed
3. **Resource intensive:** Each request spawns new process
4. **Error handling:** Process failures harder to recover
5. **No queue:** Tasks execute immediately (no backpressure)
6. **Blocking:** Webhook response delayed if synchronous

## Implementation Details

### 1. Claude Code Service

**File:** `api/src/main/java/com/canhlabs/funnyapp/service/impl/ClaudeCodeServiceImpl.java`

```java
@Service
@Slf4j
public class ClaudeCodeServiceImpl implements ClaudeCodeService {

    private final AppProperties appProperties;
    private final ExecutorService executorService;

    public ClaudeCodeServiceImpl(AppProperties appProperties) {
        this.appProperties = appProperties;
        // Thread pool for async process execution
        this.executorService = Executors.newFixedThreadPool(5);
    }

    @Override
    @Async
    public void implementTask(String taskId, String title, String description) {
        executorService.submit(() -> {
            try {
                log.info("Starting implementation for task: {}", title);

                // Build task prompt
                String prompt = buildPrompt(taskId, title, description);

                // Spawn Claude Code CLI
                ProcessResult result = spawnClaudeCode(prompt);

                if (result.exitCode == 0) {
                    log.info("Task {} completed successfully", taskId);
                    notifySuccess(taskId, result);
                } else {
                    log.error("Task {} failed with exit code {}", taskId, result.exitCode);
                    notifyFailure(taskId, result);
                }

            } catch (Exception e) {
                log.error("Failed to execute task {}: {}", taskId, e.getMessage(), e);
                notifyFailure(taskId, e.getMessage());
            }
        });
    }

    private ProcessResult spawnClaudeCode(String prompt) throws Exception {
        // 1. Create temporary prompt file
        Path promptFile = Files.createTempFile("claude-prompt-", ".txt");
        Files.writeString(promptFile, prompt);

        // 2. Build command
        List<String> command = List.of(
            "claude-code",
            "--non-interactive",
            "--input", promptFile.toString(),
            "--project", appProperties.getProjectPath()
        );

        // 3. Configure process
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(appProperties.getProjectPath()));
        pb.redirectErrorStream(true);

        // Set environment variables
        Map<String, String> env = pb.environment();
        env.put("ANTHROPIC_API_KEY", appProperties.getAnthropicApiKey());

        // 4. Start process
        Process process = pb.start();

        // 5. Capture output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("Claude Code: {}", line);
            }
        }

        // 6. Wait for completion (with timeout)
        boolean finished = process.waitFor(30, TimeUnit.MINUTES);

        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException("Claude Code execution timeout");
        }

        // 7. Cleanup
        Files.deleteIfExists(promptFile);

        return new ProcessResult(process.exitValue(), output.toString());
    }

    private String buildPrompt(String taskId, String title, String description) {
        return String.format("""
            You are a Java Spring Boot expert working on the Funny Movies project.

            Implement the following task:

            Task ID: %s
            Title: %s

            Description:
            %s

            Requirements:
            1. Follow existing code patterns in the codebase (see CLAUDE.md)
            2. Create a feature branch: feature/%s
            3. Implement the code following SOLID principles
            4. Write unit tests with proper coverage
            5. Commit changes with descriptive message
            6. Create a pull request

            Use the project structure:
            - Controllers: api/src/main/java/com/canhlabs/funnyapp/web/
            - Services: api/src/main/java/com/canhlabs/funnyapp/service/
            - Repositories: api/src/main/java/com/canhlabs/funnyapp/repo/
            - DTOs: api/src/main/java/com/canhlabs/funnyapp/dto/

            Follow the tech stack: Java 24, Spring Boot 3.x, PostgreSQL, Virtual Threads
            """,
            taskId,
            title,
            description,
            taskId.replaceAll("[^a-zA-Z0-9-]", "-").toLowerCase()
        );
    }

    private void notifySuccess(String taskId, ProcessResult result) {
        // Extract PR URL from output
        String prUrl = extractPrUrl(result.output);
        log.info("Task {} completed. PR: {}", taskId, prUrl);

        // TODO: Post comment back to Trello card with PR link
        // TODO: Send Slack notification
    }

    private void notifyFailure(String taskId, ProcessResult result) {
        log.error("Task {} failed. Output:\n{}", taskId, result.output);

        // TODO: Post error comment to Trello card
        // TODO: Send alert
    }

    private String extractPrUrl(String output) {
        // Parse Claude Code output for PR URL
        Pattern pattern = Pattern.compile("https://github\\.com/[^/]+/[^/]+/pull/\\d+");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    @Data
    @AllArgsConstructor
    private static class ProcessResult {
        private int exitCode;
        private String output;
    }
}
```

### 2. Trello Webhook Service

**File:** `api/src/main/java/com/canhlabs/funnyapp/service/impl/TrelloWebhookServiceImpl.java`

```java
@Service
@Slf4j
public class TrelloWebhookServiceImpl implements TrelloWebhookService {

    private final ClaudeCodeService claudeCodeService;
    private final AppProperties appProperties;

    @Override
    @Async
    public void processWebhook(TrelloWebhookDTO payload, String signature) {
        // 1. Verify signature
        if (!verifySignature(payload, signature)) {
            log.warn("Invalid Trello webhook signature");
            return;
        }

        // 2. Parse action type
        String actionType = payload.getAction().getType();
        if (!"addLabelToCard".equals(actionType)) {
            log.debug("Ignoring action type: {}", actionType);
            return;
        }

        // 3. Check label name
        String labelName = payload.getAction().getData().getLabel().getName();
        if (!"doing".equalsIgnoreCase(labelName)) {
            log.debug("Ignoring label: {}", labelName);
            return;
        }

        // 4. Extract card details
        TrelloWebhookDTO.Card card = payload.getAction().getData().getCard();
        String cardId = card.getId();
        String cardName = card.getName();
        String cardDesc = card.getDesc();

        log.info("Triggering implementation for Trello card: {} ({})", cardName, cardId);

        // 5. Trigger Claude Code
        claudeCodeService.implementTask(cardId, cardName, cardDesc);
    }

    private boolean verifySignature(TrelloWebhookDTO payload, String signature) {
        try {
            String callbackUrl = appProperties.getTrelloCallbackUrl();
            String secret = appProperties.getTrelloWebhookSecret();

            ObjectMapper mapper = new ObjectMapper();
            String body = mapper.writeValueAsString(payload);
            String content = body + callbackUrl;

            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA1");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(content.getBytes());
            String computed = Base64.getEncoder().encodeToString(hash);

            return computed.equals(signature);

        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }
}
```

### 3. Webhook Controller

**File:** `api/src/main/java/com/canhlabs/funnyapp/web/TrelloWebhookController.java`

```java
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/webhook")
@Slf4j
public class TrelloWebhookController extends BaseController {

    private final TrelloWebhookService webhookService;

    public TrelloWebhookController(TrelloWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Trello webhook verification endpoint
     */
    @RequestMapping(value = "/trello", method = RequestMethod.HEAD)
    public ResponseEntity<Void> verifyWebhook() {
        log.info("Trello webhook verification request");
        return ResponseEntity.ok().build();
    }

    /**
     * Trello webhook callback endpoint
     */
    @PostMapping("/trello")
    public ResponseEntity<ResultObjectInfo<String>> handleWebhook(
            @RequestBody TrelloWebhookDTO payload,
            @RequestHeader(value = "X-Trello-Webhook", required = false) String signature) {

        log.info("Received Trello webhook: action={}, cardId={}",
                payload.getAction().getType(),
                payload.getAction().getData().getCard().getId());

        // Process asynchronously - don't block webhook response
        webhookService.processWebhook(payload, signature);

        return ResponseEntity.ok(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message("Webhook received")
                .data("OK")
                .build());
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/trello/health")
    public ResponseEntity<ResultObjectInfo<Map<String, Object>>> health() {
        Map<String, Object> status = Map.of(
            "status", "up",
            "timestamp", System.currentTimeMillis(),
            "claudeCode", checkClaudeCodeInstalled()
        );

        return ResponseEntity.ok(ResultObjectInfo.<Map<String, Object>>builder()
                .status(ResultStatus.SUCCESS)
                .data(status)
                .build());
    }

    private boolean checkClaudeCodeInstalled() {
        try {
            Process process = new ProcessBuilder("claude-code", "--version").start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 4. DTOs

**TrelloWebhookDTO.java:**
```java
@Data
public class TrelloWebhookDTO {

    private Action action;
    private Model model;

    @Data
    public static class Action {
        private String id;
        private String idMemberCreator;
        private String type;
        private String date;
        private ActionData data;
        private MemberCreator memberCreator;
    }

    @Data
    public static class ActionData {
        private Card card;
        private Board board;
        private Label label;
    }

    @Data
    public static class Card {
        private String id;
        private String name;
        private String desc;
        private String shortLink;
        private String idShort;

        public String getUrl() {
            return "https://trello.com/c/" + shortLink;
        }
    }

    @Data
    public static class Board {
        private String id;
        private String name;
        private String shortLink;
    }

    @Data
    public static class Label {
        private String id;
        private String name;
        private String color;
    }

    @Data
    public static class Model {
        private String id;
        private String name;
    }

    @Data
    public static class MemberCreator {
        private String id;
        private String username;
        private String fullName;
    }
}
```

### 5. Configuration

**AppProperties.java:**
```java
@Configuration
@ConfigurationProperties("app")
@Data
public class AppProperties {
    // ... existing properties ...

    // Trello configuration
    private String trelloWebhookSecret;
    private String trelloCallbackUrl;

    // Claude Code configuration
    private String anthropicApiKey;
    private String projectPath = System.getProperty("user.dir");
}
```

**application.yaml:**
```yaml
app:
  trello-webhook-secret: ${TRELLO_WEBHOOK_SECRET}
  trello-callback-url: ${TRELLO_CALLBACK_URL:https://your-domain.com/api/webhook/trello}
  anthropic-api-key: ${ANTHROPIC_API_KEY}
  project-path: ${PROJECT_PATH:/app}
```

**.env.example:**
```bash
# Trello Configuration
TRELLO_WEBHOOK_SECRET=your_random_secret_here
TRELLO_CALLBACK_URL=https://your-domain.com/api/webhook/trello

# Anthropic API
ANTHROPIC_API_KEY=sk-ant-api03-...

# Project
PROJECT_PATH=/home/user/projects/assessment
```

### 6. Enhanced Version: Background Process Management

For better control over Claude Code processes:

```java
@Service
@Slf4j
public class ClaudeCodeProcessManager {

    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Map<String, ProcessStatus> processStatuses = new ConcurrentHashMap<>();

    public CompletableFuture<ProcessResult> executeTask(String taskId, String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Mark as running
                processStatuses.put(taskId, ProcessStatus.RUNNING);

                // Spawn process
                Process process = startClaudeCode(prompt);
                runningProcesses.put(taskId, process);

                // Wait for completion
                ProcessResult result = waitForCompletion(process, taskId);

                // Update status
                processStatuses.put(taskId,
                    result.exitCode == 0 ? ProcessStatus.SUCCESS : ProcessStatus.FAILED);

                return result;

            } catch (Exception e) {
                processStatuses.put(taskId, ProcessStatus.FAILED);
                throw new RuntimeException(e);
            } finally {
                runningProcesses.remove(taskId);
            }
        });
    }

    public void cancelTask(String taskId) {
        Process process = runningProcesses.get(taskId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            processStatuses.put(taskId, ProcessStatus.CANCELLED);
            log.info("Cancelled task: {}", taskId);
        }
    }

    public ProcessStatus getStatus(String taskId) {
        return processStatuses.getOrDefault(taskId, ProcessStatus.UNKNOWN);
    }

    public List<String> getRunningTasks() {
        return new ArrayList<>(runningProcesses.keySet());
    }

    public enum ProcessStatus {
        PENDING, RUNNING, SUCCESS, FAILED, CANCELLED, UNKNOWN
    }
}
```

## Deployment Steps

### 1. Install Claude Code CLI

**On server:**
```bash
# Install Node.js if not present
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs

# Install Claude Code globally
sudo npm install -g @anthropic/claude-code

# Verify installation
claude-code --version
```

**Or use Docker:**
```dockerfile
FROM maven:3.9-eclipse-temurin-24-alpine

# Install Node.js and Claude Code
RUN apk add --no-cache nodejs npm git
RUN npm install -g @anthropic/claude-code

# Copy application
COPY . /app
WORKDIR /app

CMD ["java", "-jar", "target/funny-app.jar"]
```

### 2. Configure Environment

```bash
# Set environment variables
export ANTHROPIC_API_KEY=sk-ant-api03-...
export TRELLO_WEBHOOK_SECRET=$(openssl rand -hex 32)
export TRELLO_CALLBACK_URL=https://your-domain.com/api/webhook/trello
export PROJECT_PATH=/home/user/projects/assessment

# Add to ~/.bashrc or /etc/environment for persistence
```

### 3. Setup Trello Webhook

```bash
# Get your Trello API key and token
# Visit: https://trello.com/app-key

# Create webhook
curl -X POST "https://api.trello.com/1/webhooks/?key=YOUR_KEY&token=YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Claude Code Automation",
    "callbackURL": "https://your-domain.com/api/webhook/trello",
    "idModel": "YOUR_BOARD_ID"
  }'

# List webhooks
curl "https://api.trello.com/1/tokens/YOUR_TOKEN/webhooks?key=YOUR_KEY"

# Delete webhook
curl -X DELETE "https://api.trello.com/1/webhooks/WEBHOOK_ID?key=YOUR_KEY&token=YOUR_TOKEN"
```

### 4. Expose Endpoint (Development)

**Using ngrok:**
```bash
# Install ngrok
brew install ngrok  # macOS
# or download from https://ngrok.com/

# Start tunnel
ngrok http 8081

# Use the HTTPS URL for Trello webhook
# Example: https://abc123.ngrok.io/api/webhook/trello
```

**Using localtunnel:**
```bash
npm install -g localtunnel
lt --port 8081 --subdomain myapp
```

## Testing

### Unit Test

```java
@SpringBootTest
class ClaudeCodeServiceImplTest {

    @Autowired
    private ClaudeCodeService claudeCodeService;

    @Test
    @Disabled("Integration test - requires Claude Code CLI")
    void testImplementTask() throws Exception {
        String taskId = "test-123";
        String title = "Test Task";
        String description = "Create a simple hello world endpoint";

        // This will actually spawn Claude Code
        claudeCodeService.implementTask(taskId, title, description);

        // Wait a bit for async execution
        Thread.sleep(60000);

        // Verify branch was created
        Process process = new ProcessBuilder("git", "branch", "--list", "feature/test-123*")
                .start();
        String output = new String(process.getInputStream().readAllBytes());

        assertTrue(output.contains("feature/test-123"));
    }
}
```

### Mock Test

```java
@SpringBootTest
@MockBean(ClaudeCodeService.class)
class TrelloWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClaudeCodeService claudeCodeService;

    @Test
    void testWebhookEndpoint() throws Exception {
        String payload = """
        {
          "action": {
            "type": "addLabelToCard",
            "data": {
              "card": {
                "id": "test123",
                "name": "Test Task",
                "desc": "Test description",
                "shortLink": "abc"
              },
              "label": {
                "name": "doing"
              }
            }
          },
          "model": {
            "id": "board123"
          }
        }
        """;

        mockMvc.perform(post("/api/webhook/trello")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // Verify service was called
        verify(claudeCodeService, times(1))
                .implementTask(eq("test123"), eq("Test Task"), eq("Test description"));
    }
}
```

### Manual Test

```bash
# Test webhook endpoint locally
curl -X POST http://localhost:8081/api/webhook/trello \
  -H "Content-Type: application/json" \
  -d '{
    "action": {
      "type": "addLabelToCard",
      "data": {
        "card": {
          "id": "manual-test",
          "name": "Manual Test Task",
          "desc": "Create a simple REST endpoint that returns hello world",
          "shortLink": "test"
        },
        "label": {
          "name": "doing"
        }
      }
    },
    "model": {
      "id": "board123"
    }
  }'

# Check logs
tail -f api/logs/app.log

# Check if Claude Code is running
ps aux | grep claude-code
```

## Monitoring & Observability

### Application Logs

```java
// Add structured logging
log.info("Claude Code started for task: {} at {}", taskId, Instant.now());
log.info("Claude Code completed for task: {} in {} seconds", taskId, duration);
```

### Metrics

```java
@Component
public class ClaudeCodeMetrics {

    private final Counter tasksStarted;
    private final Counter tasksCompleted;
    private final Timer taskDuration;

    public ClaudeCodeMetrics(MeterRegistry registry) {
        this.tasksStarted = registry.counter("claude.tasks.started");
        this.tasksCompleted = registry.counter("claude.tasks.completed", "status", "success");
        this.taskDuration = registry.timer("claude.tasks.duration");
    }

    public void recordTaskStart() {
        tasksStarted.increment();
    }

    public void recordTaskComplete(boolean success, long durationMs) {
        tasksCompleted.increment();
        taskDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
```

### Health Check

```bash
# Check Claude Code is installed
curl http://localhost:8081/api/webhook/trello/health

# Response:
# {
#   "status": "SUCCESS",
#   "data": {
#     "status": "up",
#     "claudeCode": true,
#     "timestamp": 1707389123456
#   }
# }
```

### Process Monitoring

```bash
# List running Claude Code processes
ps aux | grep claude-code

# Monitor resource usage
top -p $(pgrep -f claude-code)

# Check open files
lsof -p $(pgrep -f claude-code)
```

## Error Handling & Recovery

### Timeout Handling

```java
// Set timeout for Claude Code execution
boolean finished = process.waitFor(30, TimeUnit.MINUTES);

if (!finished) {
    log.error("Task {} timed out after 30 minutes", taskId);
    process.destroyForcibly();
    throw new TimeoutException("Claude Code execution timeout");
}
```

### Retry Logic

```java
public void implementTaskWithRetry(String taskId, String title, String description) {
    int maxRetries = 3;
    int attempt = 0;

    while (attempt < maxRetries) {
        try {
            implementTask(taskId, title, description);
            return; // Success
        } catch (Exception e) {
            attempt++;
            log.warn("Task {} failed on attempt {}/{}: {}",
                    taskId, attempt, maxRetries, e.getMessage());

            if (attempt >= maxRetries) {
                throw new RuntimeException("Task failed after " + maxRetries + " attempts", e);
            }

            // Exponential backoff
            Thread.sleep(1000L * (1 << attempt));
        }
    }
}
```

### Resource Cleanup

```java
@Scheduled(fixedRate = 300000) // Every 5 minutes
public void cleanupZombieProcesses() {
    runningProcesses.forEach((taskId, process) -> {
        if (!process.isAlive()) {
            log.warn("Found zombie process for task: {}", taskId);
            runningProcesses.remove(taskId);
        }
    });
}
```

## Security Considerations

1. **Signature Verification:** Always verify Trello webhook signatures
2. **Input Sanitization:** Sanitize task descriptions to prevent injection
3. **Rate Limiting:** Limit concurrent Claude Code processes (max 5)
4. **Resource Limits:** Set CPU/memory limits for spawned processes
5. **API Key Protection:** Store ANTHROPIC_API_KEY securely (env vars, not in code)
6. **Process Isolation:** Consider running Claude Code in Docker container

## Performance Optimization

### Thread Pool Tuning

```java
// Configure thread pool size based on server resources
@Bean
public ExecutorService claudeCodeExecutor() {
    return new ThreadPoolExecutor(
        2,                          // corePoolSize
        5,                          // maximumPoolSize
        60L, TimeUnit.SECONDS,      // keepAliveTime
        new LinkedBlockingQueue<>(10) // workQueue
    );
}
```

### Process Pooling (Advanced)

For high-throughput scenarios, consider keeping Claude Code instances warm:

```java
// Keep 2 idle Claude Code processes ready
private final Queue<Process> processPool = new ConcurrentLinkedQueue<>();

private Process getOrCreateProcess() {
    Process process = processPool.poll();
    if (process == null || !process.isAlive()) {
        return startNewClaudeCode();
    }
    return process;
}
```

## Cost Estimation

**Assumptions:**
- 10 tasks/day
- Average 2000 tokens input + 4000 tokens output per task
- Claude Sonnet 4.5 pricing: $3/M input, $15/M output

**Monthly cost:**
- Input: 10 tasks × 2000 tokens × 30 days = 600k tokens = $1.80
- Output: 10 tasks × 4000 tokens × 30 days = 1.2M tokens = $18.00
- **Total: ~$20/month**

## Comparison with Plan A (File-Based)

| Feature | Plan A (File-Based) | Plan B (Direct Spawn) |
|---------|---------------------|----------------------|
| **Complexity** | Medium (daemon + files) | Low (direct execution) |
| **Latency** | 1-5 seconds (polling) | <1 second |
| **Reliability** | High (persistent queue) | Medium (in-memory) |
| **Scalability** | Easy (add workers) | Limited (thread pool) |
| **Debugging** | Easy (file audit trail) | Medium (process logs) |
| **Infrastructure** | More (daemon + watcher) | Less (just Spring Boot) |
| **Recovery** | Easy (reprocess files) | Hard (lost if crash) |

## When to Use Plan B

**Best for:**
- ✅ Simple use cases with low volume (<20 tasks/day)
- ✅ Fast prototyping and MVP
- ✅ Teams comfortable with process management
- ✅ Single server deployments

**Not ideal for:**
- ❌ High-volume production systems
- ❌ Distributed/multi-server setups
- ❌ Need for audit trail and recovery
- ❌ Serverless or containerized environments

## Migration Path

Start with **Plan B** for simplicity, then migrate to **Plan A** when:
1. Task volume exceeds 50/day
2. Need better reliability and recovery
3. Adding multiple task sources
4. Require detailed audit trails

## Summary

Plan B offers the simplest implementation path with direct Claude Code CLI spawning. It's perfect for small-to-medium deployments where immediate execution and low infrastructure overhead are priorities. The trade-off is reduced reliability and scalability compared to the queue-based Plan A approach.
