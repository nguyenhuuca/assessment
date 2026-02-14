---
name: test-driven-development
description: Write tests before implementation code. Use when starting new features or fixing bugs. Covers Red-Green-Refactor cycle and TDD best practices.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Test-Driven Development

## The TDD Cycle

1. **Red**: Write a failing test
2. **Green**: Write minimal code to pass
3. **Refactor**: Improve code while keeping tests green

## Workflows

- [ ] **Write Test**: Write a test that describes desired behavior
- [ ] **Run Test**: Verify it fails (Red)
- [ ] **Implement**: Write minimal code to pass
- [ ] **Run Test**: Verify it passes (Green)
- [ ] **Refactor**: Clean up while tests stay green
- [ ] **Repeat**: Next test case

## TDD Example (Java + JUnit 5)

### Step 1: Red - Write Failing Test

```java
class CalculatorTest {
    @Test
    void add_TwoNumbers_ReturnsSum() {
        // Arrange
        Calculator calc = new Calculator();

        // Act
        int result = calc.add(2, 3);

        // Assert
        assertThat(result).isEqualTo(5);
    }
}

// Run: mvn test
// FAIL - Cannot resolve symbol 'Calculator'
```

### Step 2: Green - Minimal Implementation

```java
public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}

// Run: mvn test
// PASS ✓
```

### Step 3: Refactor (if needed)

Code is already clean, move to next test.

### Step 4: Next Test

```java
@Test
void subtract_TwoNumbers_ReturnsDifference() {
    // Arrange
    Calculator calc = new Calculator();

    // Act
    int result = calc.subtract(5, 3);

    // Assert
    assertThat(result).isEqualTo(2);
}

// Run: mvn test
// FAIL - Cannot resolve method 'subtract' in 'Calculator'
```

### Step 5: Implement Subtract

```java
public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }
}

// Run: mvn test
// PASS ✓ (2 tests passing)
```

## TDD Benefits

- **Design Feedback**: Tests reveal design issues early
- **Documentation**: Tests document expected behavior
- **Confidence**: Refactor fearlessly with test safety net
- **Focus**: One behavior at a time

## TDD Tips

1. **Start Simple**: Begin with the simplest test case
2. **One Assert**: Each test should verify one behavior
3. **Descriptive Names**: Test names are documentation
4. **No Logic in Tests**: Tests should be obvious
5. **Fast Feedback**: Tests should run in milliseconds

## When to Use TDD

- New features with clear requirements
- Bug fixes (write failing test first)
- Complex business logic
- API contract development

## When TDD is Less Useful

- Exploratory/prototype code
- UI layout changes
- Simple CRUD operations
