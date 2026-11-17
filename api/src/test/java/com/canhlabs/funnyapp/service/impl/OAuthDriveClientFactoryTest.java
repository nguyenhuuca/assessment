package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.exception.ImportErrorCode;
import com.canhlabs.funnyapp.exception.ImportException;
import com.google.api.services.drive.Drive;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OAuthDriveClientFactoryTest {

    @Test
    void get_isLazyAndBuildsOnce() {
        AtomicInteger calls = new AtomicInteger();
        Drive drive = mock(Drive.class);
        OAuthDriveClientFactory factory = new OAuthDriveClientFactory(() -> {
            calls.incrementAndGet();
            return drive;
        });
        assertThat(calls).hasValue(0);
        assertThat(factory.get()).isSameAs(drive);
        assertThat(factory.get()).isSameAs(drive);
        assertThat(calls).hasValue(1);
    }

    @Test
    void get_buildFailurePropagatesAndRetries() {
        AtomicInteger calls = new AtomicInteger();
        OAuthDriveClientFactory factory = new OAuthDriveClientFactory(() -> {
            calls.incrementAndGet();
            throw new ImportException(ImportErrorCode.DRIVE_AUTH_FAILED);
        });
        assertThatThrownBy(factory::get).isInstanceOf(ImportException.class);
        assertThatThrownBy(factory::get).isInstanceOf(ImportException.class);
        assertThat(calls).hasValue(2);
    }

    @Test
    void publicConstructor_doesNoWorkUntilGet() {
        assertThat(new OAuthDriveClientFactory(null, null, null)).isNotNull();
    }
}
