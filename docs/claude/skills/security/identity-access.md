---
name: identity-access
description: Implement identity and access management. Use when designing authentication, authorization, or user management. Covers OAuth2, OIDC, and RBAC.
allowed-tools: Read, Write, Glob, Grep
---

# Identity & Access Management

## Authentication vs Authorization

- **Authentication (AuthN)**: Who are you?
- **Authorization (AuthZ)**: What can you do?

## OAuth 2.0 Flows

### Authorization Code (Web Apps)
```
User -> App -> Auth Server -> User Login
User -> Auth Server -> App (code)
App -> Auth Server (code + secret) -> tokens
```

### PKCE (Mobile/SPA)
Like Authorization Code but with code verifier/challenge instead of secret.

### Client Credentials (Machine-to-Machine)
```
App -> Auth Server (client_id + secret) -> token
```

## OpenID Connect (OIDC)

OAuth 2.0 + identity layer.

**Key additions**:
- ID Token (JWT with user info)
- UserInfo endpoint
- Standard claims (sub, email, name)

## JWT Structure

```
header.payload.signature

Header: {"alg": "RS256", "typ": "JWT"}
Payload: {"sub": "123", "exp": 1234567890}
Signature: RSASHA256(header + payload, privateKey)
```

## Role-Based Access Control (RBAC) - Java/Spring Security

```java
// Entity definitions
@Entity
@Data
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();
}

@Entity
@Data
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String resource;

    @Enumerated(EnumType.STRING)
    private Action action;
}

public enum Action {
    READ, WRITE, DELETE
}

// Spring Security method-level authorization
@Service
public class UserService {
    @PreAuthorize("hasPermission(#userId, 'USER', 'WRITE')")
    public void updateUser(Long userId, UserDto updates) {
        // Implementation
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(Long userId) {
        // Only admins can delete
    }
}

// Custom permission evaluator
@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {
    @Override
    public boolean hasPermission(
        Authentication auth,
        Object targetId,
        Object permission
    ) {
        UserDetails user = (UserDetails) auth.getPrincipal();
        String resource = (String) targetId;
        String action = (String) permission;

        return user.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(resource + ":" + action));
    }
}

// Controller with role checks
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    @GetMapping("/users")
    public List<UserDto> getAllUsers() {
        // Only accessible to ADMIN role
    }
}
```

## Best Practices

### Passwords
- Minimum 12 characters
- Hash with Argon2id or bcrypt
- Never store plaintext
- Implement rate limiting

### Sessions
- Use secure, HttpOnly cookies
- Implement CSRF protection
- Set appropriate expiration
- Invalidate on logout

### Tokens
- Short-lived access tokens (15 min)
- Longer refresh tokens (days)
- Rotate refresh tokens
- Store securely (not localStorage)

### MFA
- Support TOTP (Google Authenticator)
- Consider WebAuthn/passkeys
- Backup codes for recovery
