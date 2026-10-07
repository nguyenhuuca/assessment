package com.canhlabs.funnyapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderCompatibilityTest {

    private final PasswordEncoder encoder = new ConfigBeanAccessor().encoder();

    /** Exposes the @Bean method without needing the @Value-injected fields. */
    private static class ConfigBeanAccessor extends ConfigBean {
    }

    @Test
    void newHashes_useCost12() {
        assertThat(encoder.encode("some-long-password")).startsWith("$2a$12$");
    }

    @Test
    void existingHashes_createdWithDefaultCost10_stillVerify() {
        String legacy = new BCryptPasswordEncoder().encode("legacy-password-1");

        assertThat(legacy).startsWith("$2a$10$");
        assertThat(encoder.matches("legacy-password-1", legacy)).isTrue();
        assertThat(encoder.matches("other-password-1", legacy)).isFalse();
    }
}
