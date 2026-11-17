package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.service.BackupStore;
import com.canhlabs.funnyapp.service.DbBackupService;
import com.canhlabs.funnyapp.service.backup.BackupErrorCode;
import com.canhlabs.funnyapp.service.backup.BackupException;
import com.canhlabs.funnyapp.service.backup.BackupFile;
import com.canhlabs.funnyapp.service.backup.BackupRedactor;
import com.canhlabs.funnyapp.service.backup.BackupStep;
import com.canhlabs.funnyapp.service.backup.BackupTier;
import com.canhlabs.funnyapp.service.backup.JdbcUrl;
import com.canhlabs.funnyapp.utils.Contract;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/** Orchestrates dump, upload and retention cleanup (spec FR-3). One run at a time across all tiers. */
@Slf4j
@Service
public class DbBackupServiceImpl implements DbBackupService {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final int LOG_REASON_MAX = 2048;
    private static final String RUNS = "db_backup_runs_total";
    private static final String UNKNOWN = "unknown";

    private final PgDumpRunner dumpRunner;
    private final BackupStore store;
    private final BackupAlertNotifier notifier;
    private final AppProperties props;
    private final MeterRegistry meters;
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<BackupTier, AtomicLong> lastSuccess = new EnumMap<>(BackupTier.class);
    private final Map<BackupTier, AtomicLong> lastSize = new EnumMap<>(BackupTier.class);

    @Autowired
    public DbBackupServiceImpl(PgDumpRunner dumpRunner, BackupStore store, BackupAlertNotifier notifier,
                               AppProperties props, MeterRegistry meters,
                               @Value("${spring.datasource.url:}") String jdbcUrl,
                               @Value("${spring.datasource.username:}") String dbUser,
                               @Value("${spring.datasource.password:}") String dbPassword) {
        this(dumpRunner, store, notifier, props, meters, jdbcUrl, dbUser, dbPassword, Clock.systemUTC());
    }

    DbBackupServiceImpl(PgDumpRunner dumpRunner, BackupStore store, BackupAlertNotifier notifier,
                        AppProperties props, MeterRegistry meters, String jdbcUrl, String dbUser,
                        String dbPassword, Clock clock) {
        this.dumpRunner = dumpRunner;
        this.store = store;
        this.notifier = notifier;
        this.props = props;
        this.meters = meters;
        this.jdbcUrl = jdbcUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.clock = clock;
        for (BackupTier tier : BackupTier.values()) {
            Contract.require(tier.retention(props.getBackup()) >= 1,
                    "app.backup.retention." + tier.id() + " must be at least 1");
            AtomicLong ts = new AtomicLong();
            AtomicLong size = new AtomicLong();
            lastSuccess.put(tier, ts);
            lastSize.put(tier, size);
            Gauge.builder("db_backup_last_success_timestamp_seconds", ts, AtomicLong::get)
                    .tag("tier", tier.id()).register(meters);
            Gauge.builder("db_backup_size_bytes", size, AtomicLong::get)
                    .tag("tier", tier.id()).register(meters);
        }
    }

    @Override
    public void run(BackupTier tier) {
        if (!lock.tryLock()) {
            fail(tier, new BackupException(BackupErrorCode.ALREADY_RUNNING, BackupStep.CONFIG,
                    "another backup run is in progress"));
        }
        Path file = null;
        Path part = null;
        BackupStep step = BackupStep.CONFIG;
        try {
            AppProperties.Backup cfg = props.getBackup();
            if (!cfg.isEnabled()) {
                log.info("DB backup disabled");
                count(tier, "disabled");
                return;
            }
            Instant start = clock.instant();
            checkConfigured(cfg);
            JdbcUrl db = JdbcUrl.parse(jdbcUrl);
            Path workDir = prepareWorkDir(cfg.getWorkDir());
            String name = "funnyapp-" + tier.id() + "-" + STAMP.format(start) + "Z.dump";
            file = workDir.resolve(name);
            part = workDir.resolve(name + ".part");

            step = BackupStep.DUMP;
            dumpRunner.dump(db, dbUser, dbPassword, file);
            long size = sizeOf(file);

            step = BackupStep.UPLOAD;
            String driveId = store.upload(file, name, tier);
            lastSuccess.get(tier).set(clock.instant().getEpochSecond());
            lastSize.get(tier).set(size);

            step = BackupStep.PRUNE;
            int trashed = prune(tier, cfg, driveId);
            log.info("DB backup tier={} file={} sizeBytes={} driveFileId={} trashed={} durationMs={}",
                    tier.id(), name, size, driveId, Math.max(trashed, 0),
                    Duration.between(start, clock.instant()).toMillis());
            if (trashed >= 0) {
                count(tier, "success");
            }
        } catch (BackupException e) {
            fail(tier, e);
        } catch (RuntimeException e) {
            fail(tier, new BackupException(BackupErrorCode.INTERNAL_ERROR, step, e.getClass().getSimpleName(), e));
        } finally {
            deleteQuietly(file);
            deleteQuietly(part);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void checkConfigured(AppProperties.Backup cfg) {
        if (cfg.getFolderId() == null || cfg.getFolderId().isBlank()) {
            throw new BackupException(BackupErrorCode.NOT_CONFIGURED, BackupStep.CONFIG,
                    "app.backup.folder-id is blank");
        }
        if (!props.getGoogleOauth().isConfigured()) {
            throw new BackupException(BackupErrorCode.NOT_CONFIGURED, BackupStep.CONFIG,
                    "Google OAuth credentials are not configured");
        }
    }

    private Path prepareWorkDir(String dir) {
        Path workDir = Path.of(dir);
        try {
            Files.createDirectories(workDir);
            try (DirectoryStream<Path> leftovers = Files.newDirectoryStream(workDir, "funnyapp-*.dump*")) {
                for (Path p : leftovers) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException e) {
            throw new BackupException(BackupErrorCode.INTERNAL_ERROR, BackupStep.CONFIG,
                    "cannot prepare the backup work directory", e);
        }
        return workDir;
    }

    private long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new BackupException(BackupErrorCode.INTERNAL_ERROR, BackupStep.DUMP,
                    "cannot read the dump size", e);
        }
    }

    /** Returns the number of trashed files, or -1 when the cleanup failed (warning already sent). */
    private int prune(BackupTier tier, AppProperties.Backup cfg, String uploadedId) {
        try {
            List<BackupFile> files = store.list(tier);
            int trashed = 0;
            for (int i = tier.retention(cfg); i < files.size(); i++) {
                String id = files.get(i).id();
                if (!id.equals(uploadedId)) {
                    store.trash(id);
                    trashed++;
                }
            }
            return trashed;
        } catch (RuntimeException e) {
            BackupException be = e instanceof BackupException b ? b
                    : new BackupException(BackupErrorCode.PRUNE_FAILED, BackupStep.PRUNE,
                    e.getClass().getSimpleName(), e);
            log.warn("DB backup cleanup failed tier={} reason={}", tier.id(), redact(be.getSafeReason()));
            count(tier, "prune_warning");
            notifier.pruneWarning(tier, be, lastGood(tier));
            return -1;
        }
    }

    private void fail(BackupTier tier, BackupException e) {
        log.error("DB backup failed tier={} code={} step={} reason={}", tier.id(), e.getCode(), e.getStep(),
                redact(e.getSafeReason()));
        count(tier, "failed");
        String lastGood = e.getCode() == BackupErrorCode.NOT_CONFIGURED ? UNKNOWN : lastGood(tier);
        notifier.failure(tier, e, lastGood);
        throw e;
    }

    private String lastGood(BackupTier tier) {
        try {
            return store.list(tier).stream().map(BackupFile::createdTime).max(Instant::compareTo)
                    .map(Instant::toString).orElse(UNKNOWN);
        } catch (RuntimeException e) {
            return UNKNOWN;
        }
    }

    private String redact(String text) {
        AppProperties.GoogleOauth oauth = props.getGoogleOauth();
        return BackupRedactor.redact(text, LOG_REASON_MAX,
                List.of(nz(dbPassword), nz(oauth.getClientSecret()), nz(oauth.getRefreshToken())));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private void count(BackupTier tier, String result) {
        meters.counter(RUNS, "tier", tier.id(), "result", result).increment();
    }

    private void deleteQuietly(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("Could not delete local backup file {}", p.getFileName());
        }
    }
}
