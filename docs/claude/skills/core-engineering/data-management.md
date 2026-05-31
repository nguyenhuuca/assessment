---
name: data-management
description: Design and manage data storage effectively. Use when working with databases, schemas, or data migrations. Covers schema design, migrations, and data integrity.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Data Management

## Workflows

- [ ] **Schema Design**: Define tables, relationships, constraints
- [ ] **Migrations**: Version control schema changes
- [ ] **Indexing**: Add indexes for query performance
- [ ] **Backup**: Ensure data recovery capability

## Schema Design Principles

### Normalization
- **1NF**: Atomic values, no repeating groups
- **2NF**: No partial dependencies
- **3NF**: No transitive dependencies

### When to Denormalize
- Read-heavy workloads
- Reporting/analytics
- Caching layers

## Migration Best Practices (Liquibase + PostgreSQL)

### Forward-Only Migrations
Each migration should be a single forward step.

```xml
<!-- src/main/resources/db/changelog/changes/001-create-users.xml -->
<databaseChangeLog>
  <changeSet id="001-create-users" author="dev">
    <createTable tableName="users">
      <column name="id" type="BIGSERIAL">
        <constraints primaryKey="true" nullable="false"/>
      </column>
      <column name="email" type="VARCHAR(255)">
        <constraints nullable="false" unique="true"/>
      </column>
      <column name="created_at" type="TIMESTAMP WITH TIME ZONE" defaultValueComputed="CURRENT_TIMESTAMP">
        <constraints nullable="false"/>
      </column>
    </createTable>

    <createIndex indexName="idx_users_email" tableName="users">
      <column name="email"/>
    </createIndex>
  </changeSet>
</databaseChangeLog>
```

```yaml
# db/changelog/db.changelog-master.yaml
databaseChangeLog:
  - include:
      file: changes/001-create-users.xml
      relativeToChangelogFile: true
```

```sql
-- Alternative: SQL format with Liquibase
-- liquibase formatted sql

-- changeset dev:001-create-users
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
-- rollback DROP TABLE users;
```

### Safe Migrations
- Add columns as nullable first
- Create indexes concurrently
- Never drop columns in the same deploy

## Indexing Strategy

```sql
-- B-tree (default): Equality and range queries
CREATE INDEX idx_users_email ON users(email);

-- Partial index: When you query a subset
CREATE INDEX idx_active_users ON users(id) WHERE active = true;

-- Composite index: Multiple columns
CREATE INDEX idx_orders_user_date ON orders(user_id, created_at DESC);
```

## Connection Management (HikariCP + Spring Boot)

```yaml
# application.yaml - HikariCP is default in Spring Boot
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/funnyapp
    username: ${DB_USER}
    password: ${DB_PASS}
    hikari:
      maximum-pool-size: 20              # Max connections
      minimum-idle: 5                     # Min idle connections
      connection-timeout: 2000            # Max wait for connection (ms)
      idle-timeout: 30000                 # Close idle connections after (ms)
      max-lifetime: 1800000               # Max connection lifetime (30 min)
      pool-name: FunnyAppPool
      leak-detection-threshold: 60000     # Detect connection leaks (1 min)

  jpa:
    hibernate:
      ddl-auto: none                      # Use Liquibase instead
    properties:
      hibernate:
        default_schema: public
        format_sql: true
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true

  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
    enabled: true
```

```java
// JPA Entity with proper relationships
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Video> videos = new ArrayList<>();
}

// Repository with proper query optimization
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.videos WHERE u.id = :id")
    Optional<User> findByIdWithVideos(@Param("id") Long id);

    @Query(value = "SELECT * FROM users WHERE created_at > :since ORDER BY created_at DESC",
           nativeQuery = true)
    List<User> findRecentUsers(@Param("since") Instant since);
}
```

## Data Integrity

- Use foreign key constraints
- Add NOT NULL where appropriate
- Use CHECK constraints for validation
- Consider using ENUM types for fixed values
