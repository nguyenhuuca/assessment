package com.canhlabs.funnyapp.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void dataIntegrityViolation_doesNotLeakSqlOrRowValues() throws Exception {
        SQLException cause = new SQLException(
                "ERROR: null value in column \"role\" of relation \"users\" violates not-null constraint "
                        + "Detail: Failing row contains (20, someone@example.com, null)");
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement [insert into users (user_name,role) values (?,?)]", cause);

        ResponseEntity<Object> response = handler.handleDataIntegrityViolation(ex);
        String json = mapper.writeValueAsString(response.getBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json).contains("SYSTEM_ERROR");
        assertThat(json)
                .doesNotContain("debugMessage")
                .doesNotContain("insert into")
                .doesNotContain("users")
                .doesNotContain("someone@example.com")
                .doesNotContain("not-null");
    }

    @Test
    void errorWithThrowable_keepsGenericMessageOnly() throws Exception {
        Error error = new Error(HttpStatus.INTERNAL_SERVER_ERROR, new IllegalStateException("db password=secret"));

        String json = mapper.writeValueAsString(error);

        assertThat(error.getMessage()).isEqualTo("Unexpected error");
        assertThat(json).doesNotContain("secret").doesNotContain("debugMessage");
    }
}
