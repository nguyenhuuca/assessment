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

    private final RestExceptionHandler handler = new RestExceptionHandler(new com.canhlabs.funnyapp.config.I18nConfig().messageSource());
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

    @Test
    void rateLimitExceeded_isLocalizedByCurrentLocale() {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.forLanguageTag("vi"));
        try {
            ResponseEntity<Object> response = handler.handleRateLimitExceeded(new RateLimitExceededException("Rate limit exceeded."));
            Error error = (Error) ((java.util.Map<?, ?>) response.getBody()).get("error");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(error.getCode()).isEqualTo("error.rateLimit.exceeded");
            assertThat(error.getMessage()).isEqualTo("Bạn thao tác quá nhanh. Vui lòng thử lại sau.");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void authenticationException_usesInvalidCredentialsKey() {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.ENGLISH);
        try {
            ResponseEntity<Object> response = handler.handleAuthenticationException(
                    new org.springframework.security.authentication.BadCredentialsException("x"));
            Error error = (Error) ((java.util.Map<?, ?>) response.getBody()).get("error");

            assertThat(error.getCode()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(error.getMessage()).isEqualTo("Invalid email address or password");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void customExceptionBuildErrorMessage_exposesCode() {
        CustomException ex = CustomException.of(HttpStatus.NOT_FOUND, 404, "error.video.notFound");

        assertThat(ex.buildErrorMessage().getCode()).isEqualTo("error.video.notFound");
        assertThat(CustomException.builder().status(HttpStatus.BAD_REQUEST).message("m").build()
                .buildErrorMessage().getCode()).isNull();
    }

    @Test
    void raiseKey_throwsKeyedException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CustomException.raiseKey("error.otp.incorrect"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getMessageKey()).isEqualTo("error.otp.incorrect"));
    }
}
