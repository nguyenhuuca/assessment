package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.CancelToken;
import com.canhlabs.funnyapp.client.VideoMetadata;
import com.canhlabs.funnyapp.client.YtDlpClient;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.VideoImportJob;
import com.canhlabs.funnyapp.enums.ImportStatus;
import com.canhlabs.funnyapp.exception.ImportErrorCode;
import com.canhlabs.funnyapp.exception.ImportException;
import com.canhlabs.funnyapp.repo.VideoImportJobRepository;
import com.canhlabs.funnyapp.service.DriveUploader;
import com.canhlabs.funnyapp.utils.ImportUrlValidator;
import com.canhlabs.funnyapp.utils.TitleSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * In-process worker for the admin video import (ADR-0019 D3): claim one due job, download with yt-dlp,
 * upload to Drive, always clean the work directory. Concurrency is bounded by a Semaphore.
 */
@Slf4j
@Component
public class VideoImportWorker {

    /** Every 5 minutes on the clock (:00, :05, …) — schedules are restricted to 5-minute slots. */
    static final String POLL_CRON = "0 */5 * * * *";
    private static final long PROGRESS_INTERVAL_NANOS = 2_000_000_000L;
    private static final int MAX_ERROR_MESSAGE = 500;

    private final AppProperties props;
    private final VideoImportJobRepository jobRepository;
    private final YtDlpClient ytDlpClient;
    private final DriveUploader driveUploader;
    private final ImportUrlValidator urlValidator;
    private final Executor executor;
    private final Semaphore permits;
    private final ConcurrentHashMap<Long, CancelToken> running = new ConcurrentHashMap<>();
    /** Set once startup recovery has run, so a poll cannot claim a job that recovery would then reset. */
    private volatile boolean ready;
    private long progressIntervalNanos = PROGRESS_INTERVAL_NANOS;

    @Autowired
    public VideoImportWorker(AppProperties props, VideoImportJobRepository jobRepository, YtDlpClient ytDlpClient,
                             DriveUploader driveUploader, ImportUrlValidator urlValidator) {
        this(props, jobRepository, ytDlpClient, driveUploader, urlValidator, Executors.newVirtualThreadPerTaskExecutor());
    }

    VideoImportWorker(AppProperties props, VideoImportJobRepository jobRepository, YtDlpClient ytDlpClient,
                      DriveUploader driveUploader, ImportUrlValidator urlValidator, Executor executor) {
        this.props = props;
        this.jobRepository = jobRepository;
        this.ytDlpClient = ytDlpClient;
        this.driveUploader = driveUploader;
        this.urlValidator = urlValidator;
        this.executor = executor;
        this.permits = new Semaphore(Math.max(1, props.getVideoImport().getMaxConcurrent()));
    }

    void markReady() {
        this.ready = true;
    }

    void setProgressIntervalNanos(long nanos) {
        this.progressIntervalNanos = nanos;
    }

    // ── triggers ──────────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled()) {
            log.info("Video import is disabled (app.video-import.enabled=false)");
            return;
        }
        ytDlpClient.version().ifPresentOrElse(
                v -> log.info("yt-dlp version {}", v),
                () -> log.warn("yt-dlp not found: imports will fail with {}", ImportErrorCode.NOT_INSTALLED));
        try {
            int reset = jobRepository.resetInterrupted();
            if (reset > 0) {
                log.warn("Reset {} interrupted import job(s)", reset);
            }
            cleanLeftoverDirs();
        } catch (RuntimeException e) {
            log.error("Import startup recovery failed", e);
        }
        markReady();
        wakeUp();
    }

    @Scheduled(cron = POLL_CRON)
    public void poll() {
        drain();
    }

    /** Immediately look for due work on a virtual thread (after create / run-now / retry). */
    public void wakeUp() {
        if (!enabled() || !ready) {
            return;
        }
        Thread.ofVirtual().name("import-wakeup").start(this::drain);
    }

    /** Cancels a running job; false when this node is not running it. */
    public boolean requestCancel(Long jobId) {
        CancelToken token = running.get(jobId);
        if (token == null) {
            return false;
        }
        token.cancel();
        return true;
    }

    // ── claim loop ────────────────────────────────────────────────────────────

    void drain() {
        if (!enabled() || !ready) {
            return;
        }
        while (permits.tryAcquire()) {
            Optional<Long> claimed;
            try {
                claimed = jobRepository.claimNextDue();
            } catch (RuntimeException e) {
                permits.release();
                log.error("Claiming an import job failed", e);
                return;
            }
            if (claimed.isEmpty()) {
                permits.release();
                return;
            }
            long id = claimed.get();
            CancelToken token = new CancelToken();
            running.put(id, token);
            try {
                executor.execute(() -> {
                    try {
                        runJob(id, token);
                    } finally {
                        permits.release();
                    }
                    drain();
                });
            } catch (RejectedExecutionException e) {
                running.remove(id);
                permits.release();
                fail(id, new ImportException(ImportErrorCode.INTERNAL_ERROR, e));
                return;
            }
        }
    }

    private boolean enabled() {
        return props.getVideoImport().isEnabled();
    }

    // ── one job ───────────────────────────────────────────────────────────────

    void runJob(long id, CancelToken token) {
        Path jobDir = workDir().resolve(String.valueOf(id));
        try {
            VideoImportJob job = jobRepository.findById(id).orElse(null);
            if (job == null) {
                return;
            }
            if (!driveUploader.isConfigured()) {
                throw new ImportException(ImportErrorCode.DRIVE_NOT_CONFIGURED);
            }
            ensureDiskSpace();
            String title = resolveTitle(job);
            throwIfCancelled(token);

            ThrottledProgress downloadProgress = new ThrottledProgress(id);
            Path file = ytDlpClient.download(job.getNormalizedUrl(), jobDir,
                    downloadProgress::report, token);
            throwIfCancelled(token);

            long size = fileSize(file);
            update(id, j -> {
                j.setStatus(ImportStatus.UPLOADING);
                j.setProgressPct(0);
                j.setDownloadedBytes(0);
                j.setTotalBytes(size);
            });
            ThrottledProgress uploadProgress = new ThrottledProgress(id);
            String driveId = driveUploader.upload(file, title + ".mp4", String.valueOf(id),
                    uploadProgress::report, token);

            update(id, j -> {
                j.setStatus(ImportStatus.DONE);
                j.setProgressPct(100);
                j.setDownloadedBytes(size);
                j.setTotalBytes(size);
                j.setDriveFileId(driveId);
                j.setErrorCode(null);
                j.setErrorMessage(null);
                j.setFinishedAt(Instant.now());
            });
            log.info("Import job {} uploaded to Drive", id);
        } catch (ImportException e) {
            fail(id, e);
        } catch (Exception e) {
            log.error("Import job {} failed unexpectedly", id, e);
            fail(id, new ImportException(ImportErrorCode.INTERNAL_ERROR, e));
        } finally {
            running.remove(id);
            deleteRecursively(jobDir);
        }
    }

    private String resolveTitle(VideoImportJob job) {
        String requested = TitleSanitizer.sanitize(job.getRequestedTitle());
        if (!requested.isEmpty()) {
            return requested;
        }
        String resolved = "";
        try {
            VideoMetadata meta = ytDlpClient.fetchMetadata(job.getNormalizedUrl());
            resolved = TitleSanitizer.sanitize(meta.title());
        } catch (ImportException e) {
            log.info("Metadata lookup for import job {} failed ({}), using fallback title", job.getId(), e.getErrorCode());
        }
        if (resolved.isEmpty()) {
            resolved = fallbackTitle(job);
        }
        String title = resolved;
        update(job.getId(), j -> j.setResolvedTitle(title));
        return title;
    }

    private String fallbackTitle(VideoImportJob job) {
        String videoId = urlValidator.parse(job.getNormalizedUrl())
                .map(ImportUrlValidator.ImportUrl::videoId)
                .orElse(String.valueOf(job.getId()));
        return TitleSanitizer.sanitize(job.getPlatform().name().toLowerCase() + "-" + videoId);
    }

    private void ensureDiskSpace() {
        Path dir = workDir();
        try {
            Files.createDirectories(dir);
            if (Files.getFileStore(dir).getUsableSpace() < props.getVideoImport().getMinFreeDiskBytes()) {
                throw new ImportException(ImportErrorCode.DISK_LOW);
            }
        } catch (IOException e) {
            throw new ImportException(ImportErrorCode.INTERNAL_ERROR, e);
        }
    }

    private static void throwIfCancelled(CancelToken token) {
        if (token.isCancelled()) {
            throw new ImportException(ImportErrorCode.CANCELLED);
        }
    }

    private static long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new ImportException(ImportErrorCode.INTERNAL_ERROR, e);
        }
    }

    // ── persistence helpers ───────────────────────────────────────────────────

    private void update(long id, Consumer<VideoImportJob> change) {
        jobRepository.findById(id).ifPresent(j -> {
            change.accept(j);
            jobRepository.save(j);
        });
    }

    private void fail(long id, ImportException e) {
        try {
            update(id, j -> {
                j.setFinishedAt(Instant.now());
                if (ImportErrorCode.CANCELLED.equals(e.getErrorCode())) {
                    j.setStatus(ImportStatus.CANCELLED);
                    j.setErrorCode(null);
                    j.setErrorMessage(null);
                } else {
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    j.setStatus(ImportStatus.FAILED);
                    j.setErrorCode(e.getErrorCode());
                    j.setErrorMessage(msg.length() > MAX_ERROR_MESSAGE ? msg.substring(0, MAX_ERROR_MESSAGE) : msg);
                }
            });
        } catch (RuntimeException ex) {
            log.error("Could not record failure of import job {}", id, ex);
        }
    }

    /** Writes progress to the DB at most once per interval. */
    private final class ThrottledProgress {
        private final long jobId;
        private long lastWriteNanos;
        private boolean written;

        ThrottledProgress(long jobId) {
            this.jobId = jobId;
        }

        void report(long done, long total) {
            long now = System.nanoTime();
            if (written && now - lastWriteNanos < progressIntervalNanos) {
                return;
            }
            written = true;
            lastWriteNanos = now;
            int pct = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
            try {
                jobRepository.updateProgress(jobId, pct, done, Math.max(total, 0));
            } catch (RuntimeException e) {
                log.debug("Progress update for import job {} failed", jobId);
            }
        }
    }

    // ── work dir ──────────────────────────────────────────────────────────────

    private Path workDir() {
        return Path.of(props.getVideoImport().getWorkDir());
    }

    /** At startup nothing is running, so numeric job directories left by a crash can go. */
    private void cleanLeftoverDirs() {
        Path dir = workDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> children = Files.list(dir)) {
            children.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().matches("\\d+"))
                    .forEach(VideoImportWorker::deleteRecursively);
        } catch (IOException e) {
            log.warn("Could not clean leftover import directories");
        }
    }

    static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Could not delete {}", p.getFileName());
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean import directory {}", dir.getFileName());
        }
    }
}
