package com.canhlabs.funnyapp.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Binds the real application.yaml so no profile document can silently drop the explicit Hikari limits.
 */
class HikariLimitsProfileTest {

    private static StandardEnvironment load(String profile) {
        StandardEnvironment env = new StandardEnvironment();
        MockPropertySource source = new MockPropertySource();
        if (!profile.isEmpty()) {
            source.withProperty("spring.profiles.active", profile);
        }
        env.getPropertySources().addFirst(source);
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        return env;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "dev", "prod"})
    void everyProfile_hasPoolSizeAndConnectionTimeoutDefaults(String profile) {
        StandardEnvironment env = load(profile);

        assertEquals("10", env.getProperty("spring.datasource.hikari.maximum-pool-size"));
        assertEquals("5000", env.getProperty("spring.datasource.hikari.connection-timeout"));
    }
}
