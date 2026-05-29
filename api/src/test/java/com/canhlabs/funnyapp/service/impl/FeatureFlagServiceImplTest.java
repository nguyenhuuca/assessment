package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.utils.AppConstant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceImplTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── cfClient is null (no api-key) – isEnabled returns defaultValue ────────

    @Test
    void isEnabled_noApiKey_returnsDefaultFalse() {
        FeatureFlagServiceImpl svc = serviceWithKey("");
        assertThat(svc.isEnabled(AppConstant.Flags.PERMISSION_ENFORCEMENT, false)).isFalse();
    }

    @Test
    void isEnabled_noApiKey_returnsDefaultTrue() {
        FeatureFlagServiceImpl svc = serviceWithKey("");
        assertThat(svc.isEnabled(AppConstant.Flags.PERMISSION_ENFORCEMENT, true)).isTrue();
    }

    @Test
    void isEnabled_nullApiKey_returnsDefault() {
        FeatureFlagServiceImpl svc = serviceWithKey(null);
        assertThat(svc.isEnabled(AppConstant.Flags.PERMISSION_ENFORCEMENT, false)).isFalse();
    }

    @Test
    void isEnabled_blankApiKey_returnsDefaultTrue() {
        FeatureFlagServiceImpl svc = serviceWithKey("   ");
        assertThat(svc.isEnabled("any_flag", true)).isTrue();
    }

    // ── init does not create CfClient when key is blank ───────────────────────

    @Test
    void init_blankKey_doesNotCreateCfClient() {
        FeatureFlagServiceImpl svc = serviceWithKey("   ");
        Object cfClient = ReflectionTestUtils.getField(svc, "cfClient");
        assertThat(cfClient).isNull();
    }

    @Test
    void init_emptyKey_doesNotCreateCfClient() {
        FeatureFlagServiceImpl svc = serviceWithKey("");
        Object cfClient = ReflectionTestUtils.getField(svc, "cfClient");
        assertThat(cfClient).isNull();
    }

    @Test
    void init_nullKey_doesNotCreateCfClient() {
        FeatureFlagServiceImpl svc = serviceWithKey(null);
        Object cfClient = ReflectionTestUtils.getField(svc, "cfClient");
        assertThat(cfClient).isNull();
    }

    // ── currentUserId – SecurityContext branch ────────────────────────────────
    // When cfClient is null isEnabled returns the defaultValue immediately,
    // so we cover the SecurityContext branches indirectly here through the
    // destroy() and init() path; the userId resolution is observable only
    // when cfClient is not null.  The tests below verify that the service
    // compiles and operates correctly when the security context is populated,
    // even though cfClient remains null in unit tests.

    @Test
    void isEnabled_withAuthenticatedUser_cfClientNull_stillReturnsDefault() {
        // Populate security context with a named principal
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "alice", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        FeatureFlagServiceImpl svc = serviceWithKey("");
        // cfClient is null, so defaultValue is returned regardless of principal
        assertThat(svc.isEnabled("some_flag", true)).isTrue();
        assertThat(svc.isEnabled("some_flag", false)).isFalse();
    }

    @Test
    void isEnabled_withNoSecurityContext_cfClientNull_returnsDefault() {
        // Ensure no auth in context (currentUserId falls back to "system")
        SecurityContextHolder.clearContext();

        FeatureFlagServiceImpl svc = serviceWithKey("");
        assertThat(svc.isEnabled("some_flag", false)).isFalse();
    }

    // ── destroy – safe to call when cfClient is null ──────────────────────────

    @Test
    void destroy_whenCfClientIsNull_doesNotThrow() throws Exception {
        FeatureFlagServiceImpl svc = serviceWithKey("");
        // Must not throw even though cfClient is null
        svc.destroy();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private FeatureFlagServiceImpl serviceWithKey(String key) {
        FeatureFlagServiceImpl svc = new FeatureFlagServiceImpl();
        ReflectionTestUtils.setField(svc, "apiKey", key);
        svc.init();
        return svc;
    }
}
