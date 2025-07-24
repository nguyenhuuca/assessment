package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContractDSLTest {

    @Test
    void testMustNotNull_Pass() {
        assertDoesNotThrow(() -> {
            ContractDSL.single("name", "John")
                    .mustNotNull("Name is required");
        });
    }
    @Test
    void testMustNotNull_Fail() {
        Exception ex = assertThrows(CustomException.class, () -> {
            ContractDSL.single("name", null)
                    .mustNotNull("Name is required");
        });
        assertTrue(ex.getMessage().contains("name: Name is required"));
    }
    @Test
    void testMustMin_Fail() {
        Exception ex = assertThrows(CustomException.class, () -> {
            ContractDSL.single("age", -1)
                    .mustMin(0, "Age must be >= 0");
        });
        assertTrue(ex.getMessage().contains("age: Age must be >= 0"));
    }
    @Test
    void testMustMatchRegex_Fail() {
        Exception ex = assertThrows(CustomException.class, () -> {
            ContractDSL.single("email", "invalid-email")
                    .mustMatchRegex("^[\\w.-]+@[\\w.-]+\\.\\w+$", "Invalid email");
        });
        assertTrue(ex.getMessage().contains("email: Invalid email"));
    }

    @Test
    void testMustMax_Pass() {
        assertDoesNotThrow(() -> {
            ContractDSL.single("score", 100)
                    .mustMax(100, "Score must be <= 100");
        });
    }

    @Test
    void testMustMax_Fail() {
        Exception ex = assertThrows(CustomException.class, () -> {
            ContractDSL.single("score", 101)
                    .mustMax(100, "Score must be <= 100");
        });
        assertTrue(ex.getMessage().contains("score: Score must be <= 100"));
    }

    @Test
    void testMustInEnum_Pass() {
        assertDoesNotThrow(() -> {
            ContractDSL.single("status", "ACTIVE")
                    .mustInEnum(Status.class, "Invalid status");
        });
    }

    @Test
    void testMustInEnum_Fail() {
        Exception ex = assertThrows(CustomException.class, () -> {
            ContractDSL.single("status", "UNKNOWN")
                    .mustInEnum(Status.class, "Invalid status");
        });
        assertTrue(ex.getMessage().contains("status: Invalid status"));
    }

    @Test
    void testSoftMode_CollectErrors() {
        ContractDSL<Integer> dsl = ContractDSL.soft("age", -1)
                .mustMin(0, "Age must be >= 0")
                .mustMax(100, "Age must be <= 100");

        List<String> errors = dsl.getErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.contains("age: Age must be >= 0"));
    }

    @Test
    void testSoftMode_NoErrors() {
        ContractDSL<Integer> dsl = ContractDSL.soft("age", 50)
                .mustMin(0, "Age must be >= 0")
                .mustMax(100, "Age must be <= 100");

        List<String> errors = dsl.getErrors();
        assertTrue(errors.isEmpty());
    }

    @Test
    void testMustMatchRegex_Pass() {
        assertDoesNotThrow(() -> {
            ContractDSL.single("email", "test@example.com")
                    .mustMatchRegex("^[\\w.-]+@[\\w.-]+\\.\\w+$", "Invalid email");
        });
    }

    @Test
    void testMust_PassWithCustomPredicate() {
        assertDoesNotThrow(() -> {
            ContractDSL.single("value", 10)
                    .must(v -> v > 5, "Value must be greater than 5");
        });
    }

    @Test
    void testMust_FailWithCustomPredicate() {
        Exception ex = assertThrows(CustomException.class, () -> {
            ContractDSL.single("value", 3)
                    .must(v -> v > 5, "Value must be greater than 5");
        });
        assertTrue(ex.getMessage().contains("value: Value must be greater than 5"));
    }

    enum Status {
        ACTIVE, INACTIVE, PENDING
    }
}
