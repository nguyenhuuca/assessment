# Code Quality Standards

Deep-dive reference for SOLID principles and type safety. See Core Principles in CLAUDE.md for the essentials.

## SOLID Principles

1. **Single Responsibility**: Each module/class should have one reason to change
2. **Open/Closed**: Open for extension, closed for modification
3. **Liskov Substitution**: Subtypes must be substitutable for base types
4. **Interface Segregation**: Prefer small, specific interfaces over large general ones
5. **Dependency Inversion**: Depend on abstractions, not concretions

## DRY (Don't Repeat Yourself)

- **Knowledge duplication** (must fix): Same business logic in multiple places
- **Incidental duplication** (evaluate carefully): Similar code that may evolve differently
- Maintain a single source of truth for business logic

## Type Safety

- Use strict typing where available
- Avoid `any` types in TypeScript (if applicable)
- Use type narrowing and discriminated unions
- Leverage compile-time type checking

## Performance Checklist

- N+1 query patterns (loops with DB calls)
- Blocking I/O in async paths (readFileSync, execSync)
- Excessive memory allocations
- Missing pagination
- Inefficient algorithms (O(n²) when O(n) possible)
- Cache opportunities missed

## Quality Gates

All of these must pass before committing:

- Tests pass
- Linter passes
- Type checker passes (if applicable)
- Build succeeds
- Security audit passes

## Refactoring Discipline

**Two Hats Rule**: Never mix refactoring and optimization in the same session.

- **Hat 1: Refactoring** - Change structure, NOT behavior. Tests must pass unchanged.
- **Hat 2: Optimization** - Improve performance, NOT behavior. Benchmarks required.

When switching hats, commit first, then switch context.

## Naming Conventions (suggested defaults)

Suggested defaults — customize per project.

- **Files**: camelCase (e.g., `userService.ts`, `authMiddleware.py`)
- **Components**: PascalCase (e.g., `UserProfile.tsx`, `LoginForm.vue`)
- **Directories**: kebab-case for multi-word (e.g., `api-utils/`, `auth-handlers/`)
- **Constants**: UPPER_SNAKE_CASE
- **Variables/functions**: camelCase (JS/TS), snake_case (Python/Go/Rust)

## Structured Logging

- Use a structured logger instead of raw console/print calls
- Include correlation IDs for request tracing
- Log levels: ERROR (failures), WARN (degraded), INFO (key events), DEBUG (troubleshooting)
- Never log secrets, tokens, or PII

## Automated Quality Suite

Generic categories — configure with the tools appropriate for your stack.

| Phase | Checks |
|-------|--------|
| Pre-commit | Lint + format + type check + secret scan |
| CI pipeline | Lint + secret scan + vulnerability scan + tests |
| Continuous | Dependency updates + security advisories |
