package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.FakeProcess;
import com.canhlabs.funnyapp.client.ProcessFactory;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.service.backup.BackupErrorCode;
import com.canhlabs.funnyapp.service.backup.BackupException;
import com.canhlabs.funnyapp.service.backup.BackupStep;
import com.canhlabs.funnyapp.service.backup.JdbcUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PgDumpRunnerTest {

    private static final String PASSWORD = "s3cr3t-pw";
    private static final JdbcUrl DB = new JdbcUrl("db.local", 5433, "funnyapp");

    @TempDir
    Path dir;

    private final List<String> argv = new ArrayList<>();
    private Map<String, String> env;
    private AppProperties props;
    private Path target;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getBackup().setPgDumpPath("/usr/bin/pg_dump");
        props.getBackup().setTimeout(Duration.ofSeconds(5));
        target = dir.resolve("sub").resolve("daily.dump");
    }

    private PgDumpRunner runner(Supplier<Process> process, byte[] fileContent) {
        ProcessFactory factory = new ProcessFactory() {
            @Override
            public Process start(List<String> command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Process start(List<String> command, Map<String, String> extraEnv) throws IOException {
                argv.addAll(command);
                env = extraEnv;
                if (fileContent != null) {
                    Files.write(Path.of(command.get(command.size() - 1)), fileContent);
                }
                return process.get();
            }
        };
        return new PgDumpRunner(factory, props);
    }

    private static byte[] dumpBytes() {
        return "PGDMP\u0001\u000e-rest-of-dump".getBytes(StandardCharsets.ISO_8859_1);
    }

    private BackupException failure(PgDumpRunner runner) {
        return assertThrows(BackupException.class, () -> runner.dump(DB, "app", PASSWORD, target));
    }

    /** Regression: the dump must be owner-only from the first byte, not chmod-ed after pg_dump finished. */
    @Test
    void partFileExistsOwnerOnlyBeforePgDumpStarts() throws IOException {
        java.util.concurrent.atomic.AtomicReference<String> modeAtStart = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean existedAtStart = new java.util.concurrent.atomic.AtomicBoolean();
        boolean posix = Files.getFileStore(dir).supportsFileAttributeView("posix");
        ProcessFactory factory = new ProcessFactory() {
            @Override
            public Process start(List<String> command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Process start(List<String> command, Map<String, String> extraEnv) throws IOException {
                Path part = Path.of(command.get(command.size() - 1));
                existedAtStart.set(Files.exists(part));
                if (posix) {
                    modeAtStart.set(java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(part)));
                }
                Files.write(part, dumpBytes());
                return FakeProcess.finished("", "", 0);
            }
        };

        new PgDumpRunner(factory, props).dump(DB, "app", PASSWORD, target);

        assertTrue(existedAtStart.get());
        if (posix) {
            assertEquals("rw-------", modeAtStart.get());
        }
    }

    @Test
    void buildsExactArgvWithoutPasswordAndPassesItInEnv() {
        PgDumpRunner runner = runner(() -> FakeProcess.finished("", "", 0), dumpBytes());

        runner.dump(DB, "app", PASSWORD, target);

        assertEquals(List.of("/usr/bin/pg_dump", "--host", "db.local", "--port", "5433", "--username", "app",
                "--dbname", "funnyapp", "--format=custom", "--no-password", "--file", target + ".part"), argv);
        assertFalse(argv.stream().anyMatch(a -> a.contains(PASSWORD)));
        assertEquals(Map.of("PGPASSWORD", PASSWORD), env);
    }

    @Test
    void successRenamesPartToTarget() {
        PgDumpRunner runner = runner(() -> FakeProcess.finished("", "", 0), dumpBytes());

        Path result = runner.dump(DB, "app", PASSWORD, target);

        assertEquals(target, result);
        assertTrue(Files.isRegularFile(target));
        assertFalse(Files.exists(Path.of(target + ".part")));
    }

    @Test
    void nonZeroExitRedactsPasswordFromStderr() {
        PgDumpRunner runner = runner(
                () -> FakeProcess.finished("", "pg_dump: error: password " + PASSWORD + " rejected", 1), dumpBytes());

        BackupException e = failure(runner);

        assertEquals(BackupErrorCode.PG_DUMP_FAILED, e.getCode());
        assertEquals(BackupStep.DUMP, e.getStep());
        assertTrue(e.getSafeReason().contains("***"));
        assertFalse(e.getSafeReason().contains(PASSWORD));
        assertFalse(e.getMessage().contains(PASSWORD));
        assertFalse(Files.exists(Path.of(target + ".part")));
    }

    @Test
    void stderrIsLimitedToLastTwoKilobytes() {
        String noise = "x".repeat(10_000) + "THE-END";
        PgDumpRunner runner = runner(() -> FakeProcess.finished("", noise, 1), null);

        BackupException e = failure(runner);

        assertTrue(e.getSafeReason().endsWith("THE-END"));
        assertTrue(e.getSafeReason().length() <= 2048);
    }

    @Test
    void timeoutKillsProcessAndDeletesPart() {
        props.getBackup().setTimeout(Duration.ofMillis(200));
        FakeProcess hanging = FakeProcess.hanging("");
        PgDumpRunner runner = runner(() -> hanging, dumpBytes());

        BackupException e = failure(runner);

        assertEquals(BackupErrorCode.PG_DUMP_TIMEOUT, e.getCode());
        assertTrue(hanging.wasDestroyed());
        assertFalse(Files.exists(Path.of(target + ".part")));
    }

    @Test
    void startFailureMapsToNotFound() {
        ProcessFactory factory = new ProcessFactory() {
            @Override
            public Process start(List<String> command) throws IOException {
                throw new IOException("no such file");
            }

            @Override
            public Process start(List<String> command, Map<String, String> extraEnv) throws IOException {
                throw new IOException("no such file");
            }
        };

        BackupException e = assertThrows(BackupException.class,
                () -> new PgDumpRunner(factory, props).dump(DB, "app", PASSWORD, target));

        assertEquals(BackupErrorCode.PG_DUMP_NOT_FOUND, e.getCode());
        assertTrue(e.getCause() instanceof IOException);
    }

    @Test
    void emptyFileIsInvalid() {
        PgDumpRunner runner = runner(() -> FakeProcess.finished("", "", 0), new byte[0]);

        BackupException e = failure(runner);

        assertEquals(BackupErrorCode.DUMP_INVALID, e.getCode());
        assertFalse(Files.exists(Path.of(target + ".part")));
        assertFalse(Files.exists(target));
    }

    @Test
    void missingFileIsInvalid() {
        PgDumpRunner runner = runner(() -> FakeProcess.finished("", "", 0), null);

        assertEquals(BackupErrorCode.DUMP_INVALID, failure(runner).getCode());
    }

    @Test
    void wrongMagicIsInvalid() {
        PgDumpRunner runner = runner(() -> FakeProcess.finished("", "", 0),
                "-- plain sql dump".getBytes(StandardCharsets.UTF_8));

        BackupException e = failure(runner);

        assertEquals(BackupErrorCode.DUMP_INVALID, e.getCode());
        assertFalse(Files.exists(Path.of(target + ".part")));
    }

    @Test
    void interruptKillsProcessAndKeepsInterruptFlag() {
        FakeProcess hanging = FakeProcess.hanging("");
        PgDumpRunner runner = runner(() -> hanging, dumpBytes());
        Thread.currentThread().interrupt();
        try {
            BackupException e = failure(runner);

            assertEquals(BackupErrorCode.INTERNAL_ERROR, e.getCode());
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(hanging.wasDestroyed());
        } finally {
            Thread.interrupted();
        }
    }
}
