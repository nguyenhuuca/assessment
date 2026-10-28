package com.canhlabs.funnyapp.exception;

import com.canhlabs.funnyapp.contract.JsonContractSupport;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Pins the sub-error JSON built from a real Bean Validation violation. {@code Error.addValidationErrors(Set)} is
 * package-private and reads the leaf node through Hibernate's {@code PathImpl}, which SB4-4 changes.
 */
class ErrorJsonContractTest {

    static class Probe {
        @NotBlank(message = "must not be blank")
        String name;
    }

    @Test
    void error_withConstraintViolationSubError_serializesToRecordedFixture() throws Exception {
        Set<ConstraintViolation<?>> violations;
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            violations = Set.copyOf(validator.validate(new Probe()));
        }
        Error error = new Error(HttpStatus.BAD_REQUEST, 400, "Validation failed");
        error.setTimestamp(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        error.addValidationErrors(violations);

        JsonContractSupport.assertMatchesFixture("error-constraint-violation.json",
                JsonContractSupport.bootMapper().writeValueAsString(error));
    }
}
