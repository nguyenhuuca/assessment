package com.canhlabs.funnyapp.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AppPropertiesTest {

    @Test
    void watchHistoryMaxEntries_defaultsTo500() {
        assertThat(new AppProperties().getWatchHistory().getMaxEntries()).isEqualTo(500);
    }

    @Test
    void hls_defaultsToOneGibReserveAndTenMinuteTimeout() {
        AppProperties.Hls hls = new AppProperties().getHls();

        assertThat(hls.getMinFreeDiskBytes()).isEqualTo(1073741824L);
        assertThat(hls.getTimeout()).isEqualTo(java.time.Duration.ofMinutes(10));
    }

    @Test
    void passwordLoginEnabled_defaultsToTrue() {
        assertThat(new AppProperties().isPasswordLoginEnabled()).isTrue();
    }

    @Test
    void passwordLoginEnabled_followsAuthSetting() {
        AppProperties props = new AppProperties();
        props.getAuth().setPasswordLoginEnabled(false);

        assertThat(props.isPasswordLoginEnabled()).isFalse();
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedUsePasswordlessKey_doesNotChangeBehaviour_andOnlyWarns() {
        AppProperties props = new AppProperties();
        props.setUsePasswordless(true);

        assertThat(props.isPasswordLoginEnabled()).isTrue();
        assertThatCode(props::warnDeprecatedKeys).doesNotThrowAnyException();
        assertThatCode(() -> new AppProperties().warnDeprecatedKeys()).doesNotThrowAnyException();
    }
}
