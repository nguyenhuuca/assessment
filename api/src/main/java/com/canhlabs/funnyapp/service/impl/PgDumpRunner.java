package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.ProcessFactory;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.service.backup.BackupErrorCode;
import com.canhlabs.funnyapp.service.backup.BackupException;
import com.canhlabs.funnyapp.service.backup.BackupRedactor;
import com.canhlabs.funnyapp.service.backup.BackupStep;
import com.canhlabs.funnyapp.service.backup.JdbcUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs {@code pg_dump} (custom format) into {@code <target>.part}, validates the result and renames it to the target.
 * The password only travels in the child's environment, never in argv or logs.
 */
@Slf4j
@Component
public class PgDumpRunner {

    static final int STDERR_TAIL_BYTES = 2048;
    private static final byte[] MAGIC = "PGDMP".getBytes(StandardCharsets.US_ASCII);
    private static final Duration KILL_WAIT = Duration.ofSeconds(10);
    private static final long JOIN_MILLIS = 2000;

    private final ProcessFactory processFactory;
    private final AppProperties props;

    public PgDumpRunner(ProcessFactory processFactory, AppProperties props) {
        this.processFactory = processFactory;
        this.props = props;
    }

    public Path dump(JdbcUrl db, String user, String password, Path target) {
        AppProperties.Backup cfg = props.getBackup();
        Path part = target.resolveSibling(target.getFileName() + ".part");
        List<String> argv = List.of(cfg.getPgDumpPath(),
                "--host", db.host(),
                "--port", String.valueOf(db.port()),
                "--username", user,
                "--dbname", db.database(),
                "--format=custom",
                "--no-password",
                "--file", part.toString());
        List<String> secrets = List.of(password == null ? "" : password);

        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new BackupException(BackupErrorCode.INTERNAL_ERROR, BackupStep.DUMP,
                    "cannot create the backup directory", e);
        }

        Process process;
        try {
            process = processFactory.start(argv, Map.of("PGPASSWORD", password == null ? "" : password));
        } catch (IOException e) {
            log.warn("Cannot start pg_dump: {}", e.getClass().getSimpleName());
            throw new BackupException(BackupErrorCode.PG_DUMP_NOT_FOUND, BackupStep.DUMP,
                    "pg_dump could not be started", e);
        }

        AtomicReference<String> stderrTail = new AtomicReference<>("");
        Thread outReader = Thread.ofVirtual().start(() -> drain(process.getInputStream(), null));
        Thread errReader = Thread.ofVirtual().start(() -> drain(process.getErrorStream(), stderrTail));

        boolean finished;
        try {
            finished = process.waitFor(cfg.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                kill(process);
                process.waitFor(KILL_WAIT.toSeconds(), TimeUnit.SECONDS);
            }
            outReader.join(JOIN_MILLIS);
            errReader.join(JOIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            kill(process);
            deleteQuietly(part);
            throw new BackupException(BackupErrorCode.INTERNAL_ERROR, BackupStep.DUMP, "backup was interrupted", e);
        }

        if (!finished) {
            deleteQuietly(part);
            throw new BackupException(BackupErrorCode.PG_DUMP_TIMEOUT, BackupStep.DUMP,
                    "pg_dump exceeded " + cfg.getTimeout());
        }
        restrictPermissions(part);
        int exit = process.exitValue();
        if (exit != 0) {
            deleteQuietly(part);
            String reason = BackupRedactor.redact(stderrTail.get(), STDERR_TAIL_BYTES, secrets);
            log.warn("pg_dump exited with {}", exit);
            throw new BackupException(BackupErrorCode.PG_DUMP_FAILED, BackupStep.DUMP, reason);
        }
        validate(part);
        move(part, target);
        return target;
    }

    private static void validate(Path part) {
        try {
            if (!Files.isRegularFile(part) || Files.size(part) <= MAGIC.length) {
                throw invalid(part, "dump file is missing or too small");
            }
            try (InputStream in = Files.newInputStream(part)) {
                if (!java.util.Arrays.equals(in.readNBytes(MAGIC.length), MAGIC)) {
                    throw invalid(part, "dump file is not in pg_dump custom format");
                }
            }
        } catch (IOException e) {
            deleteQuietly(part);
            throw new BackupException(BackupErrorCode.DUMP_INVALID, BackupStep.DUMP, "dump file is unreadable", e);
        }
    }

    private static BackupException invalid(Path part, String reason) {
        deleteQuietly(part);
        return new BackupException(BackupErrorCode.DUMP_INVALID, BackupStep.DUMP, reason);
    }

    private static void move(Path part, Path target) {
        try {
            try {
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            deleteQuietly(part);
            throw new BackupException(BackupErrorCode.INTERNAL_ERROR, BackupStep.DUMP,
                    "cannot move the dump to its final name", e);
        }
    }

    private static void restrictPermissions(Path part) {
        if (!Files.exists(part)) {
            return;
        }
        try {
            Files.setPosixFilePermissions(part, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // not a POSIX file system (Windows): nothing to restrict
        }
    }

    /** Kills children too: an orphan keeps the pipes open and blocks the readers. */
    private static void kill(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (UnsupportedOperationException e) {
            // Process implementations without a native handle; the parent is still killed below
        } finally {
            process.destroyForcibly();
        }
    }

    /** Reads to EOF; when {@code tail} is given keeps only the last {@link #STDERR_TAIL_BYTES} chars in it. */
    private static void drain(InputStream in, AtomicReference<String> tail) {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[1024];
            StringBuilder window = new StringBuilder();
            int n;
            while ((n = reader.read(buf)) >= 0) {
                if (tail != null) {
                    window.append(buf, 0, n);
                    if (window.length() > STDERR_TAIL_BYTES) {
                        window.delete(0, window.length() - STDERR_TAIL_BYTES);
                    }
                    tail.set(window.toString());
                }
            }
        } catch (IOException e) {
            // stream closed because the process was destroyed; nothing left to read
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Cannot delete {}", file.getFileName());
        }
    }
}
