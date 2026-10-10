package com.canhlabs.funnyapp.client;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultProcessFactoryTest {

    @Test
    void restrictEnvironment_dropsSecretsKeepsPath() {
        Map<String, String> env = new HashMap<>(Map.of(
                "PATH", "/usr/bin", "HOME", "/home/app", "DB_PASS", "x", "JWT_SECRET", "y",
                "GOOGLE_OAUTH_REFRESH_TOKEN", "z", "Path", "C:/Windows"));
        DefaultProcessFactory.restrictEnvironment(env);
        assertThat(env).containsOnlyKeys("PATH", "HOME", "Path");
    }
}
