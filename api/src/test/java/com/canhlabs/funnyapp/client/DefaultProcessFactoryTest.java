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

    @Test
    void applyEnvironment_addsExtraVarsAndStillStripsSecrets() {
        Map<String, String> env = new HashMap<>(Map.of("PATH", "/usr/bin", "DB_PASS", "x", "JWT_SECRET", "y"));
        DefaultProcessFactory.applyEnvironment(env, Map.of("RCLONE_CONFIG_PASS", "p"));
        assertThat(env).containsOnlyKeys("PATH", "RCLONE_CONFIG_PASS");
    }

    @Test
    void applyEnvironment_nullExtraEnvOnlyRestricts() {
        Map<String, String> env = new HashMap<>(Map.of("PATH", "/usr/bin", "DB_PASS", "x"));
        DefaultProcessFactory.applyEnvironment(env, null);
        assertThat(env).containsOnlyKeys("PATH");
    }

    @Test
    void defaultOverload_emptyDelegatesNonEmptyUnsupported() throws Exception {
        ProcessFactory pf = cmd -> null;
        assertThat(pf.start(java.util.List.of("x"), null)).isNull();
        assertThat(pf.start(java.util.List.of("x"), Map.of())).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pf.start(java.util.List.of("x"), Map.of("A", "b")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
