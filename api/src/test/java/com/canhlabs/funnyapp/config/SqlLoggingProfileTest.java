package com.canhlabs.funnyapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Binds the real application.yaml so a later "default" document cannot silently re-enable SQL logging in prod.
 */
class SqlLoggingProfileTest {

    private static StandardEnvironment load(String profile) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MockPropertySource().withProperty("spring.profiles.active", profile));
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        return env;
    }

    @Test
    void devProfile_enablesSqlLogging() {
        StandardEnvironment env = load("dev");
        assertEquals("true", env.getProperty("spring.jpa.show-sql"));
        assertEquals("true", env.getProperty("spring.jpa.properties.hibernate.format_sql"));
    }

    @Test
    void prodProfile_doesNotEnableSqlLogging() {
        StandardEnvironment env = load("prod");
        assertNotEquals("true", env.getProperty("spring.jpa.show-sql"));
        assertNotEquals("true", env.getProperty("spring.jpa.properties.hibernate.format_sql"));
    }
}
