---
name: implementing-code
description: Write clean, efficient, maintainable code. Use when implementing features, writing functions, or creating new modules. Covers SOLID principles, error handling, and code organization.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Implementing Code

## Workflows

- [ ] **Security Check**: Injection flaws, auth issues, sensitive data exposure
- [ ] **Performance Check**: N+1 queries, memory leaks, inefficient algorithms
- [ ] **Readability Check**: SOLID principles, naming conventions, comments
- [ ] **Testing Check**: Edge cases, error paths, happy paths

## Feedback Loops

1. Implement feature or fix
2. Run local tests (unit/integration)
3. Run linter/formatter
4. If failure, fix and repeat

## Reference Implementation

### SOLID Compliant Class (Java + Spring Boot)

```java
// Abstraction (Interface Segregation)
public interface Logger {
    void log(String message);
}

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
}

// Domain Entity
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @Builder.Default
    private Instant createdAt = Instant.now();
}

// Implementation (Single Responsibility)
@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final Logger logger;

    public UserService(UserRepository userRepository, Logger logger) {
        this.userRepository = userRepository;
        this.logger = logger;
    }

    public User registerUser(String email) {
        // Validation
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email already exists");
        }

        // Business logic
        User user = User.builder()
            .email(email)
            .build();

        User saved = userRepository.save(user);
        logger.log("User registered: " + saved.getId());

        return saved;
    }
}
```

## Code Review Checklist

- [ ] No hardcoded secrets or credentials
- [ ] Input validation on all external data
- [ ] Proper error handling with meaningful messages
- [ ] No N+1 query patterns
- [ ] Functions follow single responsibility principle
- [ ] Dependencies injected, not instantiated inline
- [ ] Tests cover happy path and edge cases
