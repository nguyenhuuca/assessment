package com.canhlabs.funnyapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rate limiting and the SePay IP allowlist read getRemoteAddr(); behind the proxy that is only correct with this setting. */
class ForwardHeadersConfigTest {

    @Test
    void prodProfile_usesNativeForwardHeaders() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MockPropertySource().withProperty("spring.profiles.active", "prod"));
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        assertEquals("native", env.getProperty("server.forward-headers-strategy"));
    }
}
