---
name: security-review
description: Conduct security code reviews. Use when reviewing code for vulnerabilities, assessing security posture, or auditing applications. Covers security review checklist.
allowed-tools: Read, Glob, Grep
---

# Security Review

## Review Checklist

### Authentication
- [ ] Strong password requirements enforced
- [ ] MFA implemented for sensitive operations
- [ ] Session tokens are cryptographically secure
- [ ] Session timeout is appropriate
- [ ] Logout properly invalidates session

### Authorization
- [ ] Access controls checked server-side
- [ ] Least privilege principle applied
- [ ] Role-based access properly implemented
- [ ] Direct object references validated

### Input Validation
- [ ] All input validated server-side
- [ ] Input type and length checked
- [ ] Special characters properly handled
- [ ] File uploads validated and restricted

### Output Encoding
- [ ] HTML output properly encoded
- [ ] JSON responses use proper content type
- [ ] Error messages don't leak information

### Cryptography
- [ ] Strong algorithms used (AES-256, RSA-2048+)
- [ ] No custom crypto implementations
- [ ] Keys properly managed
- [ ] TLS 1.2+ enforced

### Error Handling
- [ ] Exceptions handled gracefully
- [ ] Error messages don't expose internals
- [ ] Failed operations logged

### Logging
- [ ] Security events logged
- [ ] Sensitive data not logged
- [ ] Logs protected from tampering

## Code Patterns to Flag (Java)

### SQL Injection
```java
// ❌ DANGER - SQL Injection
String query = "SELECT * FROM users WHERE id = " + userId;
jdbcTemplate.queryForObject(query, User.class);

// ✅ SAFE - Use JPA or parameterized queries
@Query("SELECT u FROM User u WHERE u.id = :id")
Optional<User> findById(@Param("id") Long id);
```

### XSS (Cross-Site Scripting)
```java
// ❌ DANGER - Unescaped output in templates
@GetMapping("/profile")
public String profile(Model model, @RequestParam String name) {
    model.addAttribute("name", name); // If rendered without escaping
    return "profile";
}

// ✅ SAFE - Use Thymeleaf with proper escaping
<!-- Thymeleaf auto-escapes by default -->
<p th:text="${name}">Name here</p>

// ✅ SAFE - Manual escaping if needed
import org.springframework.web.util.HtmlUtils;
String safe = HtmlUtils.htmlEscape(userInput);
```

### Hardcoded Secrets
```java
// ❌ DANGER - Hardcoded credentials
public class ApiClient {
    private static final String API_KEY = "sk-abc123...";
    private static final String DB_PASSWORD = "password123";
}

// ✅ SAFE - Use environment variables
@Value("${api.key}")
private String apiKey;

@Value("${spring.datasource.password}")
private String dbPassword;
```

### Insecure Random
```java
// ❌ DANGER - Predictable random for security
Random random = new Random();
String token = String.valueOf(random.nextInt());

// ✅ SAFE - Use SecureRandom for security purposes
SecureRandom secureRandom = new SecureRandom();
byte[] token = new byte[32];
secureRandom.nextBytes(token);
String tokenStr = Base64.getUrlEncoder().encodeToString(token);
```

### Path Traversal
```java
// ❌ DANGER - Path traversal vulnerability
@GetMapping("/files/{filename}")
public ResponseEntity<Resource> getFile(@PathVariable String filename) {
    File file = new File("/uploads/" + filename);
    return ResponseEntity.ok(new FileSystemResource(file));
}

// ✅ SAFE - Validate and sanitize path
@GetMapping("/files/{filename}")
public ResponseEntity<Resource> getFile(@PathVariable String filename) {
    // Reject path traversal attempts
    if (filename.contains("..") || filename.contains("/")) {
        throw new IllegalArgumentException("Invalid filename");
    }

    Path basePath = Paths.get("/uploads").toAbsolutePath().normalize();
    Path filePath = basePath.resolve(filename).normalize();

    // Ensure resolved path is within base directory
    if (!filePath.startsWith(basePath)) {
        throw new SecurityException("Path traversal detected");
    }

    return ResponseEntity.ok(new FileSystemResource(filePath));
}
```

## Security Review Report

```markdown
## Security Review: [Component]

### Summary
- Critical: [X]
- High: [X]
- Medium: [X]
- Low: [X]

### Findings

#### [CRITICAL] SQL Injection in UserService
**Location**: api/src/main/java/com/example/service/UserService.java:47
**Description**: User input concatenated into SQL query
**Remediation**: Use JPA with named parameters
**Code**:
```java
// Current (vulnerable)
String query = "SELECT * FROM users WHERE email = '" + email + "'";

// ✅ Recommended fix
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);
```
```
