package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.utils.AppConstant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagServiceImplTest {

    // ── no api-key configured (local dev / CI without Harness) ────────────────

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
    void init_blankKey_doesNotCreateCfClient() {
        FeatureFlagServiceImpl svc = serviceWithKey("   ");
        // cfClient field must remain null — no connection attempt
        Object cfClient = ReflectionTestUtils.getField(svc, "cfClient");
        assertThat(cfClient).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private FeatureFlagServiceImpl serviceWithKey(String key) {
        FeatureFlagServiceImpl svc = new FeatureFlagServiceImpl();
        ReflectionTestUtils.setField(svc, "apiKey", key);
        svc.init();
        return svc;
    }
}
