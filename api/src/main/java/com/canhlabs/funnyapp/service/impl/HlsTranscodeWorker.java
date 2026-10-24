package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.DriveFileLocks;
import com.canhlabs.funnyapp.client.FfmpegHlsClient;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.HlsErrorCode;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.exception.HlsException;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.utils.AppConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * In-process worker that stream-copies a Drive video's {@code .full} file into an HLS rendition (ADR-0031).
 * One job at a time ({@code Semaphore(1)}, BR-4); the file lock keeps it away from running downloads.
 */
@Slf4j
@Component
public class HlsTranscodeWorker {

    /** Every 5 minutes on the clock; a fallback for a missed wake-up or a job left PENDING by a busy file. */
    static final String POLL_CRON = "0 */5 * * * *";
    private static final String SOURCE_EXTENSION = ".full";

    private final AppProperties props;
    private final VideoSourceRepository repository;
    private final HlsStorage storage;
    private final FfmpegHlsClient ffmpeg;
    private final DriveFileLocks locks;
    private final Executor executor;
    private final Path cacheDir;
    private final Semaphore permits = new Semaphore(1);
    /** Set once startup recovery has run, so a poll cannot claim a job that recovery would then reset. */
    private volatile boolean ready;

    @Autowired
    public HlsTranscodeWorker(AppProperties props, VideoSourceRepository repository, HlsStorage storage,
                              FfmpegHlsClient ffmpeg, DriveFileLocks locks) {
        this(props, repository, storage, ffmpeg, locks, Executors.newVirtualThreadPerTaskExecutor(),
                Path.of(AppConstant.CACHE_DIR));
    }

    HlsTranscodeWorker(AppProperties props, VideoSourceRepository repository, HlsStorage storage,
                       FfmpegHlsClient ffmpeg, DriveFileLocks locks, Executor executor, Path cacheDir) {
        this.props = props;
        this.repository = repository;
        this.storage = storage;
        this.ffmpeg = ffmpeg;
        this.locks = locks;
        this.executor = executor;
        this.cacheDir = cacheDir;
    }

    void markReady() {
        this.ready = true;
    }

    // ── triggers ──────────────────────────────────────────────────────────────

    /** BR-11: recover from a restart, then look for work. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            int reset = repository.resetHlsProcessing();
            if (reset > 0) {
                log.warn("Reset {} interrupted HLS job(s)", reset);
            }
            storage.deleteTmpDirs();
            markMissingOutputs();
            deleteOrphanDirs();
        } catch (RuntimeException e) {
            log.error("HLS startup recovery failed", e);
        }
        markReady();
        wakeUp();
    }

    @Scheduled(cron = POLL_CRON)
    public void poll() {
        drain();
    }

    /** Immediately look for queued work on a virtual thread (after a PUT or a re-download). */
    public void wakeUp() {
        if (!ready) {
            return;
        }
        Thread.ofVirtual().name("hls-wakeup").start(this::drain);
    }

    // ── claim loop ────────────────────────────────────────────────────────────

    void drain() {
        if (!ready) {
            return;
        }
        while (permits.tryAcquire()) {
            Optional<VideoSource> taken;
            try {
                taken = takeNext();
            } catch (RuntimeException e) {
                permits.release();
                log.error("Claiming an HLS job failed", e);
                return;
            }
            if (taken.isEmpty()) {
                permits.release();
                return; // nothing can be taken now (empty queue or every row busy): wait for the next trigger
            }
            VideoSource source = taken.get();
            try {
                executor.execute(() -> {
                    try {
                        runJob(source);
                    } finally {
                        locks.release(source.getSourceId());
                        permits.release();
                    }
                    drain();
                });
            } catch (RejectedExecutionException e) {
                locks.release(source.getSourceId());
                permits.release();
                fail(source.getId(), HlsErrorCode.FFMPEG_FAILED);
                return;
            }
        }
    }

    /** Peek the queue, then lock the file and win the DB row; rows whose file is busy stay PENDING (EC-7). */
    private Optional<VideoSource> takeNext() {
        for (VideoSource row : repository.findHlsQueue()) {
            String fileId = row.getSourceId();
            if (!HlsStorage.isValidFileId(fileId)) {
                log.warn("HLS job {} has an invalid file id", row.getId());
                if (repository.claimHls(row.getId()) == 1) {
                    fail(row.getId(), HlsErrorCode.SOURCE_MISSING);
                }
                continue;
            }
            if (!locks.tryClaim(fileId)) {
                continue;
            }
            if (repository.claimHls(row.getId()) == 1) {
                return Optional.of(row);
            }
            locks.release(fileId);
        }
        return Optional.empty();
    }

    // ── one job ───────────────────────────────────────────────────────────────

    void runJob(VideoSource source) {
        long id = source.getId();
        String fileId = source.getSourceId();
        long started = System.nanoTime();
        try {
            Path src = cacheDir.resolve(fileId + SOURCE_EXTENSION);
            if (!Files.isRegularFile(src)) {
                throw new HlsException(HlsErrorCode.SOURCE_MISSING);
            }
            if (!ffmpeg.probe(src).isSupported()) {
                throw new HlsException(HlsErrorCode.UNSUPPORTED_CODEC);
            }
            long size = Files.size(src);
            if (storage.usableSpace() < size + props.getHls().getMinFreeDiskBytes()) {
                throw new HlsException(HlsErrorCode.DISK_FULL);
            }
            storage.deleteTmp(fileId);
            ffmpeg.segment(src, storage.tmpDir(fileId), props.getHls().getTimeout());

            HlsStatus current = repository.findById(id).map(VideoSource::getHlsStatus).orElse(null);
            if (current != HlsStatus.PROCESSING) {
                storage.deleteTmp(fileId); // turned off or deleted meanwhile (FR-4)
                log.info("HLS job {} for {} discarded, status is now {}", id, fileId, current);
                return;
            }
            storage.delete(fileId); // a stale directory must not block the publish
            storage.publish(fileId);
            update(id, s -> {
                s.setHlsStatus(HlsStatus.READY);
                s.setHlsError(null);
            });
            log.info("HLS job {} for {} ready in {} ms, {} bytes", id, fileId, millisSince(started),
                    dirSize(storage.dir(fileId)));
        } catch (HlsException e) {
            discard(fileId);
            fail(id, e.getErrorCode());
            log.warn("HLS job {} for {} failed with {} after {} ms", id, fileId, e.getErrorCode(), millisSince(started));
        } catch (Exception e) {
            discard(fileId);
            fail(id, HlsErrorCode.FFMPEG_FAILED);
            log.error("HLS job {} for {} failed unexpectedly after {} ms", id, fileId, millisSince(started), e);
        }
    }

    private void discard(String fileId) {
        try {
            storage.deleteTmp(fileId);
        } catch (RuntimeException e) {
            log.warn("Could not delete the HLS work directory of {}", fileId);
        }
    }

    // ── startup recovery ──────────────────────────────────────────────────────

    private void markMissingOutputs() {
        for (VideoSource row : repository.findAllByHlsStatus(HlsStatus.READY)) {
            if (!HlsStorage.isValidFileId(row.getSourceId()) || !storage.exists(row.getSourceId())) {
                log.warn("HLS output of video {} is missing", row.getId());
                fail(row.getId(), HlsErrorCode.OUTPUT_MISSING);
            }
        }
    }

    private void deleteOrphanDirs() {
        for (String fileId : storage.listRenditionIds()) {
            boolean ready = repository.findBySourceId(fileId)
                    .map(s -> s.getHlsStatus() == HlsStatus.READY)
                    .orElse(false);
            if (!ready) {
                log.info("Deleting orphan HLS directory {}", fileId);
                storage.delete(fileId);
            }
        }
    }

    // ── persistence helpers ───────────────────────────────────────────────────

    private void update(long id, Consumer<VideoSource> change) {
        repository.findById(id).ifPresent(s -> {
            change.accept(s);
            repository.save(s);
        });
    }

    /** Records ERROR, unless the owner turned HLS off in the meantime (never overwrite NONE). */
    private void fail(long id, HlsErrorCode code) {
        try {
            update(id, s -> {
                if (s.getHlsStatus() == HlsStatus.NONE) {
                    return;
                }
                s.setHlsStatus(HlsStatus.ERROR);
                s.setHlsError(code.name());
            });
        } catch (RuntimeException e) {
            log.error("Could not record failure of HLS job {}", id, e);
        }
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static long dirSize(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            long total = 0;
            for (Path p : files.toList()) {
                total += Files.size(p);
            }
            return total;
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }
}
