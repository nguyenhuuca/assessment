package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.exception.CustomException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractBatchTest {
    @Test
    void testAllValid_NoException() {
        assertDoesNotThrow(() -> {
            ContractBatch batch = ContractBatch.start();

            batch.check("age", 25)
                    .mustMin(18, "Tuổi phải ≥ 18");

            batch.check("email", "john@example.com")
                    .mustMatchRegex("^[\\w.-]+@[\\w.-]+\\.\\w+$", "Email sai định dạng");

            batch.validate();
        });
    }

    @Test
    void testMultipleErrors_CollectAndThrow() {
        Exception ex = assertThrows(CustomException.class, () -> {
            ContractBatch batch = ContractBatch.start();

            batch.check("age", 15)
                    .mustMin(18, "age: must ≥ 18");

            batch.check("email", "invalid")
                    .mustMatchRegex("^[\\w.-]+@[\\w.-]+\\.\\w+$", "email: invalid email");

            batch.validate();
        });

        String msg = ex.getMessage();
        assertTrue(msg.contains("age: must ≥ 18"));
        assertTrue(msg.contains("invalid email"));
    }
}
