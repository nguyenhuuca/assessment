package com.canhlabs.funnyapp.share;

import com.canhlabs.funnyapp.share.exception.CustomException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContractTest {

    @Test
    void require_shouldNotThrow_whenConditionIsTrue() {
        assertDoesNotThrow(() -> Contract.require(true, "Should not throw"));
    }

    @Test
    void require_shouldThrow_whenConditionIsFalse() {
        Exception ex = assertThrows(CustomException.class, () -> Contract.require(false, "Error!"));
        assertEquals("Error!", ex.getMessage());
    }

    @Test
    void require_withValidator_shouldReturnValue_whenValid() {
        String result = Contract.require("abc", s -> s.length() == 3, "Length must be 3");
        assertEquals("abc", result);
    }

    @Test
    void require_withValidator_shouldThrow_whenInvalid() {
        assertThrows(CustomException.class, () -> Contract.require("ab", s -> s.length() == 3, "Length must be 3"));
    }

    @Test
    void requireNonNull_shouldReturnValue_whenNotNull() {
        Integer value = 5;
        assertEquals(value, Contract.requireNonNull(value, "Must not be null"));
    }

    @Test
    void requireNonNull_shouldThrow_whenNull() {
        assertThrows(CustomException.class, () -> Contract.requireNonNull(null, "Must not be null"));
    }

    @Test
    void requireNotBlank_shouldReturn_whenNotBlank() {
        assertEquals("abc", Contract.requireNotBlank("abc", "Must not be blank"));
    }

    @Test
    void requireNotBlank_shouldThrow_whenBlank() {
        assertThrows(CustomException.class, () -> Contract.requireNotBlank("   ", "Must not be blank"));
        assertThrows(CustomException.class, () -> Contract.requireNotBlank(null, "Must not be blank"));
    }

    @Test
    void ensure_shouldNotThrow_whenConditionIsTrue() {
        assertDoesNotThrow(() -> Contract.ensure(true, "Should not throw"));
    }

    @Test
    void ensure_shouldThrow_whenConditionIsFalse() {
        assertThrows(CustomException.class, () -> Contract.ensure(false, "Error!"));
    }

    @Test
    void ensure_withValidator_shouldReturnValue_whenValid() {
        int value = 10;
        assertEquals(value, Contract.ensure(value, v -> v > 0, "Must be positive"));
    }

    @Test
    void ensure_withValidator_shouldThrow_whenInvalid() {
        assertThrows(CustomException.class, () -> Contract.ensure(-1, v -> v > 0, "Must be positive"));
    }

    @Test
    void ensureNonNull_shouldReturn_whenNotNull() {
        String s = "test";
        assertEquals(s, Contract.ensureNonNull(s, "Must not be null"));
    }

    @Test
    void ensureNonNull_shouldThrow_whenNull() {
        assertThrows(CustomException.class, () -> Contract.ensureNonNull(null, "Must not be null"));
    }

    @Test
    void invariant_shouldNotThrow_whenConditionIsTrue() {
        assertDoesNotThrow(() -> Contract.invariant(true, "Should not throw"));
    }

    @Test
    void invariant_shouldThrow_whenConditionIsFalse() {
        assertThrows(CustomException.class, () -> Contract.invariant(false, "Invariant failed"));
    }

    @Test
    void invariant_withValidator_shouldReturnValue_whenValid() {
        String s = "ok";
        assertEquals(s, Contract.invariant(s, v -> v.equals("ok"), "Must be 'ok'"));
    }

    @Test
    void invariant_withValidator_shouldThrow_whenInvalid() {
        assertThrows(CustomException.class, () -> Contract.invariant("fail", v -> v.equals("ok"), "Must be 'ok'"));
    }
}