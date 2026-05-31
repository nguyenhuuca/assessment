---
name: java
description: Write Java code following best practices. Use when developing Java applications with Spring Boot, Maven/Gradle. Covers modern Java features, Virtual Threads, and testing.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Java Development

## Project Setup

```bash
# Maven project
mvn archetype:generate -DgroupId=com.example -DartifactId=my-app \
  -DarchetypeArtifactId=maven-archetype-quickstart

# Spring Boot project
curl https://start.spring.io/starter.zip \
  -d dependencies=web,data-jpa,postgresql \
  -d type=maven-project \
  -d javaVersion=24 \
  -o project.zip
```

### pom.xml (Java 24 with Preview Features)
```xml
<properties>
    <java.version>24</java.version>
    <maven.compiler.source>24</maven.compiler.source>
    <maven.compiler.target>24</maven.compiler.target>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <release>24</release>
                <compilerArgs>--enable-preview</compilerArgs>
            </configuration>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <argLine>--enable-preview</argLine>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Modern Java Features

### Records (Java 14+)
```java
// Immutable data class
public record User(Long id, String email, String name) {
    // Compact constructor for validation
    public User {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
    }
}

// Usage
User user = new User(1L, "test@example.com", "John");
String email = user.email(); // Getter auto-generated
```

### Pattern Matching (Java 21+)
```java
// Pattern matching in switch
String format(Object obj) {
    return switch (obj) {
        case Integer i -> "int %d".formatted(i);
        case String s when s.length() > 10 -> "long string: %s".formatted(s);
        case String s -> "short string: %s".formatted(s);
        case null -> "null";
        default -> obj.toString();
    };
}

// Pattern matching in instanceof
if (obj instanceof String s && s.length() > 5) {
    System.out.println(s.toUpperCase());
}
```

### Virtual Threads (Java 21+)
```java
// Enable in Spring Boot
@Configuration
public class ThreadConfig {
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
}

// Manual usage
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<String>> futures = urls.stream()
        .map(url -> executor.submit(() -> fetchData(url)))
        .toList();

    for (Future<String> future : futures) {
        String result = future.get();
    }
}
```

### StructuredTaskScope (Java 21+ Preview)
```java
// Structured concurrency
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Future<String> user = scope.fork(() -> fetchUser(userId));
    Future<List<Order>> orders = scope.fork(() -> fetchOrders(userId));

    scope.join();           // Wait for all tasks
    scope.throwIfFailed();  // Propagate errors

    return new UserProfile(user.resultNow(), orders.resultNow());
}
```

### Text Blocks (Java 15+)
```java
String json = """
    {
        "name": "%s",
        "age": %d,
        "email": "%s"
    }
    """.formatted(name, age, email);

String sql = """
    SELECT u.id, u.name, o.total
    FROM users u
    JOIN orders o ON u.id = o.user_id
    WHERE u.status = 'ACTIVE'
    """;
```

## Spring Boot Patterns

### Controller with Validation
```java
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        UserDto user = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getById(@PathVariable @Positive Long id) {
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
```

### Service Layer
```java
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDto create(CreateUserRequest request) {
        // Validation
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already exists");
        }

        // Business logic
        User user = User.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .createdAt(Instant.now())
            .build();

        User saved = userRepository.save(user);
        return mapToDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findById(Long id) {
        return userRepository.findById(id)
            .map(this::mapToDto);
    }
}
```

### Repository with Custom Query
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    @Query("""
        SELECT u FROM User u
        WHERE u.status = :status
        AND u.createdAt > :since
        ORDER BY u.createdAt DESC
        """)
    Page<User> findActiveUsers(
        @Param("status") UserStatus status,
        @Param("since") Instant since,
        Pageable pageable
    );

    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :now WHERE u.id = :id")
    void updateLastLogin(@Param("id") Long id, @Param("now") Instant now);
}
```

## Exception Handling

### Custom Exception
```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

// Global exception handler
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            Instant.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                FieldError::getDefaultMessage
            ));

        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation failed",
            errors,
            Instant.now()
        );
        return ResponseEntity.badRequest().body(error);
    }
}
```

## Testing

### Unit Test with JUnit 5 & Mockito
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void create_ValidRequest_ReturnsUser() {
        // Given
        CreateUserRequest request = new CreateUserRequest("test@example.com", "password");
        User savedUser = User.builder()
            .id(1L)
            .email(request.email())
            .build();

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When
        UserDto result = userService.create(request);

        // Then
        assertThat(result.email()).isEqualTo("test@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void create_DuplicateEmail_ThrowsException() {
        // Given
        CreateUserRequest request = new CreateUserRequest("test@example.com", "password");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // When & Then
        assertThrows(DuplicateEmailException.class, () -> userService.create(request));
        verify(userRepository, never()).save(any());
    }
}
```

### Integration Test with TestContainers
```java
@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_ExistingUser_ReturnsUser() {
        // Given
        User user = User.builder()
            .email("test@example.com")
            .password("password")
            .build();
        userRepository.save(user);

        // When
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }
}
```

### REST API Test with MockMvc
```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void getById_ExistingUser_ReturnsOk() throws Exception {
        // Given
        UserDto user = new UserDto(1L, "test@example.com", "Test User");
        when(userService.findById(1L)).thenReturn(Optional.of(user));

        // When & Then
        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.name").value("Test User"));
    }

    @Test
    void create_InvalidEmail_ReturnsBadRequest() throws Exception {
        // Given
        String requestBody = """
            {
                "email": "invalid-email",
                "password": "password"
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }
}
```

## Best Practices

### Use Optional for Nullable Returns
```java
// Good
public Optional<User> findById(Long id) {
    return userRepository.findById(id);
}

// Bad
public User findById(Long id) {
    return userRepository.findById(id).orElse(null);
}
```

### Use Builder Pattern with Lombok
```java
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String password;

    @Builder.Default
    private Instant createdAt = Instant.now();
}

// Usage
User user = User.builder()
    .email("test@example.com")
    .password("encoded")
    .build();
```

### Validation with Jakarta Bean Validation
```java
public record CreateUserRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password
) {}
```

## Tooling

```bash
# Maven commands
mvn clean install          # Build project
mvn test                   # Run tests
mvn verify                 # Run tests + integration tests
mvn jacoco:report          # Generate coverage report

# Run with preview features
mvn clean package
java --enable-preview -jar target/app.jar

# Spring Boot
mvn spring-boot:run        # Run application
mvn spring-boot:build-image # Build Docker image
```

## Common Dependencies

```xml
<!-- Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- TestContainers -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```
