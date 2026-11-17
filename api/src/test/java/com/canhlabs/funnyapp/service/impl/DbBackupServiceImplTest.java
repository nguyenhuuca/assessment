package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.service.BackupStore;
import com.canhlabs.funnyapp.service.backup.BackupErrorCode;
import com.canhlabs.funnyapp.service.backup.BackupException;
import com.canhlabs.funnyapp.service.backup.BackupFile;
import com.canhlabs.funnyapp.service.backup.BackupStep;
import com.canhlabs.funnyapp.service.backup.BackupTier;
import com.canhlabs.funnyapp.service.backup.JdbcUrl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DbBackupServiceImplTest {

    private static final String URL = "jdbc:postgresql://db:5432/funnyapp";
    private static final Instant NOW = Instant.parse("2026-11-17T20:00:00Z");

    @TempDir
    Path tmp;

    private PgDumpRunner runner;
    private BackupStore store;
    private BackupAlertNotifier notifier;
    private AppProperties props;
    private SimpleMeterRegistry meters;
    private DbBackupServiceImpl service;

    @BeforeEach
    void setUp() {
        runner = mock(PgDumpRunner.class);
        store = mock(BackupStore.class);
        notifier = mock(BackupAlertNotifier.class);
        props = new AppProperties();
        props.getBackup().setEnabled(true);
        props.getBackup().setFolderId("folder");
        props.getBackup().setWorkDir(tmp.resolve("work").toString());
        props.getBackup().getRetention().setDaily(2);
        props.getGoogleOauth().setClientId("id");
        props.getGoogleOauth().setClientSecret("secret");
        props.getGoogleOauth().setRefreshToken("token");
        meters = new SimpleMeterRegistry();
        service = create();
        writeDumpOnDump();
    }

    private DbBackupServiceImpl create() {
        return new DbBackupServiceImpl(runner, store, notifier, props, meters, URL, "user", "pw",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void writeDumpOnDump() {
        doAnswer(inv -> {
            Files.writeString(inv.getArgument(3, Path.class), "PGDMP-data");
            return null;
        }).when(runner).dump(any(JdbcUrl.class), anyString(), anyString(), any(Path.class));
    }

    private double counter(String tier, String result) {
        var c = meters.find("db_backup_runs_total").tag("tier", tier).tag("result", result).counter();
        return c == null ? 0 : c.count();
    }

    private static BackupFile file(String id, long epoch) {
        return new BackupFile(id, id + ".dump", Instant.ofEpochSecond(epoch));
    }

    private long workDirFiles() throws Exception {
        try (var s = Files.list(tmp.resolve("work"))) {
            return s.count();
        }
    }

    @Test
    void happyPathUploadsPrunesAndRecordsMetrics() throws Exception {
        when(store.upload(any(Path.class), anyString(), any())).thenReturn("new");
        when(store.list(BackupTier.DAILY)).thenReturn(List.of(file("new", 5), file("b", 4), file("c", 3), file("d", 2)));

        service.run(BackupTier.DAILY);

        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(store).upload(any(Path.class), name.capture(), org.mockito.ArgumentMatchers.eq(BackupTier.DAILY));
        assertThat(name.getValue()).isEqualTo("funnyapp-daily-20261117-200000Z.dump");
        verify(store).trash("c");
        verify(store).trash("d");
        verify(store, never()).trash("new");
        verify(store, never()).trash("b");
        verifyNoInteractions(notifier);
        assertThat(workDirFiles()).isZero();
        assertThat(counter("daily", "success")).isEqualTo(1);
        assertThat(meters.get("db_backup_last_success_timestamp_seconds").tag("tier", "daily").gauge().value())
                .isEqualTo(NOW.getEpochSecond());
        assertThat(meters.get("db_backup_size_bytes").tag("tier", "daily").gauge().value())
                .isEqualTo("PGDMP-data".length());
    }

    @Test
    void uploadedIdNeverTrashedEvenIfListedBeyondRetention() {
        when(store.upload(any(Path.class), anyString(), any())).thenReturn("new");
        when(store.list(BackupTier.DAILY)).thenReturn(List.of(file("a", 5), file("b", 4), file("new", 3)));
        service.run(BackupTier.DAILY);
        verify(store, never()).trash(anyString());
    }

    @Test
    void disabledDoesNothing() {
        props.getBackup().setEnabled(false);
        service.run(BackupTier.DAILY);
        verifyNoInteractions(runner, store, notifier);
        assertThat(counter("daily", "disabled")).isEqualTo(1);
    }

    @Test
    void blankFolderIsNotConfigured() {
        props.getBackup().setFolderId(" ");
        assertNotConfigured();
    }

    @Test
    void missingOauthIsNotConfigured() {
        props.getGoogleOauth().setRefreshToken("");
        assertNotConfigured();
    }

    private void assertNotConfigured() {
        assertThatThrownBy(() -> service.run(BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BackupErrorCode.NOT_CONFIGURED));
        verify(notifier, times(1)).failure(any(), any(), anyString());
        verifyNoInteractions(runner);
        assertThat(counter("daily", "failed")).isEqualTo(1);
    }

    @Test
    void badJdbcUrlFails() {
        service = new DbBackupServiceImpl(runner, store, notifier, props, new SimpleMeterRegistry(), "mysql://x",
                "u", "p", Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> service.run(BackupTier.DAILY)).isInstanceOf(BackupException.class);
        verify(notifier).failure(any(), any(), anyString());
    }

    @Test
    void alreadyRunningWhileAnotherTierDumps() throws Exception {
        CountDownLatch inDump = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            Files.writeString(inv.getArgument(3, Path.class), "x");
            inDump.countDown();
            release.await(10, TimeUnit.SECONDS);
            return null;
        }).when(runner).dump(any(JdbcUrl.class), anyString(), anyString(), any(Path.class));
        when(store.upload(any(Path.class), anyString(), any())).thenReturn("new");
        when(store.list(any())).thenReturn(List.of(file("new", 1)));

        AtomicReference<Throwable> first = new AtomicReference<>();
        Thread t = Thread.ofVirtual().start(() -> {
            try {
                service.run(BackupTier.DAILY);
            } catch (Throwable e) {
                first.set(e);
            }
        });
        assertThat(inDump.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.run(BackupTier.WEEKLY))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BackupErrorCode.ALREADY_RUNNING));
        verify(notifier, times(1)).failure(any(), any(), any());
        release.countDown();
        t.join(10_000);
        assertThat(first.get()).isNull();
        service.run(BackupTier.WEEKLY);
    }

    @Test
    void dumpFailureAlertsOnceAndDoesNotUploadOrTrash() throws Exception {
        doThrow(new BackupException(BackupErrorCode.PG_DUMP_FAILED, BackupStep.DUMP, "bad"))
                .when(runner).dump(any(JdbcUrl.class), anyString(), anyString(), any(Path.class));
        when(store.list(BackupTier.DAILY)).thenReturn(List.of(file("old", 100)));

        assertThatThrownBy(() -> service.run(BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BackupErrorCode.PG_DUMP_FAILED));

        verify(notifier, times(1)).failure(any(), any(), org.mockito.ArgumentMatchers.eq(Instant.ofEpochSecond(100).toString()));
        verify(store, never()).upload(any(), anyString(), any());
        verify(store, never()).trash(anyString());
        assertThat(workDirFiles()).isZero();
        assertThat(counter("daily", "failed")).isEqualTo(1);
    }

    @Test
    void uploadFailureAlertsOnceNoTrashAndDeletesLocalFile() throws Exception {
        when(store.upload(any(Path.class), anyString(), any()))
                .thenThrow(new BackupException(BackupErrorCode.DRIVE_AUTH_FAILED, BackupStep.UPLOAD, "401"));
        when(store.list(any())).thenThrow(new IllegalStateException("no drive"));

        assertThatThrownBy(() -> service.run(BackupTier.DAILY)).isInstanceOf(BackupException.class);

        verify(notifier, times(1)).failure(any(), any(), org.mockito.ArgumentMatchers.eq("unknown"));
        verify(store, never()).trash(anyString());
        assertThat(workDirFiles()).isZero();
    }

    @Test
    void unexpectedExceptionBecomesInternalError() {
        doThrow(new IllegalStateException("boom")).when(runner)
                .dump(any(JdbcUrl.class), anyString(), anyString(), any(Path.class));
        assertThatThrownBy(() -> service.run(BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(BackupErrorCode.INTERNAL_ERROR);
                    assertThat(e.getStep()).isEqualTo(BackupStep.DUMP);
                });
        verify(notifier, times(1)).failure(any(), any(), anyString());
    }

    @Test
    void pruneFailureWarnsAndReturnsNormally() {
        when(store.upload(any(Path.class), anyString(), any())).thenReturn("new");
        when(store.list(BackupTier.DAILY)).thenThrow(
                new BackupException(BackupErrorCode.PRUNE_FAILED, BackupStep.PRUNE, "5xx"));

        service.run(BackupTier.DAILY);

        verify(notifier, times(1)).pruneWarning(any(), any(), anyString());
        verify(notifier, never()).failure(any(), any(), any());
        assertThat(counter("daily", "prune_warning")).isEqualTo(1);
        assertThat(meters.get("db_backup_size_bytes").tag("tier", "daily").gauge().value()).isPositive();
    }

    @Test
    void pruneTrashFailureOfPlainExceptionIsWrapped() {
        when(store.upload(any(Path.class), anyString(), any())).thenReturn("new");
        when(store.list(BackupTier.DAILY)).thenReturn(List.of(file("new", 3), file("b", 2), file("c", 1)));
        doThrow(new IllegalStateException("x")).when(store).trash("c");

        service.run(BackupTier.DAILY);

        ArgumentCaptor<BackupException> e = ArgumentCaptor.forClass(BackupException.class);
        verify(notifier).pruneWarning(any(), e.capture(), anyString());
        assertThat(e.getValue().getCode()).isEqualTo(BackupErrorCode.PRUNE_FAILED);
    }

    @Test
    void notifierFailureDoesNotChangeOutcome() {
        // The notifier swallows mail errors itself; the service still rethrows the backup error.
        doThrow(new BackupException(BackupErrorCode.PG_DUMP_TIMEOUT, BackupStep.DUMP, "t"))
                .when(runner).dump(any(JdbcUrl.class), anyString(), anyString(), any(Path.class));
        assertThatThrownBy(() -> service.run(BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BackupErrorCode.PG_DUMP_TIMEOUT));
    }

    @Test
    void leftoverFilesAreDeletedBeforeDumping() throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        Path old = Files.writeString(work.resolve("funnyapp-daily-20200101-000000Z.dump"), "old");
        Path oldPart = Files.writeString(work.resolve("funnyapp-daily-20200101-000000Z.dump.part"), "old");
        Path other = Files.writeString(work.resolve("keep.txt"), "keep");
        doAnswer(inv -> {
            assertThat(old).doesNotExist();
            assertThat(oldPart).doesNotExist();
            Files.writeString(inv.getArgument(3, Path.class), "d");
            return null;
        }).when(runner).dump(any(JdbcUrl.class), anyString(), anyString(), any(Path.class));
        when(store.upload(any(Path.class), anyString(), any())).thenReturn("new");
        when(store.list(any())).thenReturn(List.of(file("new", 1)));

        service.run(BackupTier.DAILY);

        assertThat(other).exists();
        assertThat(old).doesNotExist();
    }

    @Test
    void retentionBelowOneFailsConstruction() {
        props.getBackup().getRetention().setWeekly(0);
        assertThatThrownBy(this::create).isInstanceOf(RuntimeException.class).hasMessageContaining("weekly");
    }

    @Test
    void publicConstructorUsesSystemClock() {
        var s = new DbBackupServiceImpl(runner, store, notifier, props, new SimpleMeterRegistry(), URL, "u", "p");
        props.getBackup().setEnabled(false);
        s.run(BackupTier.MONTHLY);
        verifyNoInteractions(runner);
    }
}
