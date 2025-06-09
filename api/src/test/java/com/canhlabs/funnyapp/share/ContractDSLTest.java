package com.canhlabs.funnyapp.share;

import com.canhlabs.funnyapp.share.exception.CustomException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
}
