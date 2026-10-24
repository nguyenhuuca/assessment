package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.DriveFileLocks;
import com.canhlabs.funnyapp.client.FfmpegHlsClient;
import com.canhlabs.funnyapp.client.FfmpegHlsClient.ProbeResult;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.HlsErrorCode;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.exception.HlsException;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HlsTranscodeWorkerTest {

    private static final long ID = 7L;
    private static final String FILE = "file1";

    @TempDir
    Path tmp;

    @Mock VideoSourceRepository repo;
    @Mock FfmpegHlsClient ffmpeg;

    private final DriveFileLocks locks = new DriveFileLocks();
    private final Map<Long, VideoSource> rows = new HashMap<>();
    private AppProperties props;
    private HlsStorage storage;
    private Path cacheDir;
    private VideoSource row;
    private HlsTranscodeWorker worker;

    @BeforeEach
    void setUp() throws IOException {
        props = new AppProperties();
        props.getHls().setMinFreeDiskBytes(0);
        cacheDir = tmp.resolve("cache");
        Files.createDirectories(cacheDir);
        storage = new HlsStorage(cacheDir.resolve("hls"));
        row = source(ID, FILE, HlsStatus.PROCESSING);
        rows.put(ID, row);
        when(repo.findById(ID)).thenReturn(Optional.of(row));
        when(repo.markHlsReady(anyLong())).thenAnswer(inv -> {
            VideoSource s = rows.get(inv.<Long>getArgument(0));
            if (s == null || s.getHlsStatus() != HlsStatus.PROCESSING) {
                return 0;
            }
            s.setHlsStatus(HlsStatus.READY);
            s.setHlsError(null);
            return 1;
        });
        when(repo.markHlsError(anyLong(), any(String.class))).thenAnswer(inv -> {
            VideoSource s = rows.get(inv.<Long>getArgument(0));
            if (s == null || s.getHlsStatus() != HlsStatus.PROCESSING) {
                return 0;
            }
            s.setHlsStatus(HlsStatus.ERROR);
            s.setHlsError(inv.getArgument(1));
            return 1;
        });
        when(repo.save(any(VideoSource.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ffmpeg.probe(any(Path.class))).thenReturn(new ProbeResult(List.of("h264"), List.of("aac")));
        doAnswer(inv -> {
            Path dir = inv.getArgument(1);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(HlsStorage.PLAYLIST), "#EXTM3U");
            Files.writeString(dir.resolve("seg_0.ts"), "x");
            return null;
        }).when(ffmpeg).segment(any(Path.class), any(Path.class), any(Duration.class));
        worker = newWorker();
    }

    private HlsTranscodeWorker newWorker() {
        HlsTranscodeWorker w = new HlsTranscodeWorker(props, repo, storage, ffmpeg, locks, Runnable::run, cacheDir);
        w.markReady();
        return w;
    }

    private static VideoSource source(long id, String fileId, HlsStatus status) {
        VideoSource s = new VideoSource();
        s.setId(id);
        s.setSourceId(fileId);
        s.setHlsStatus(status);
        return s;
    }

    private void writeSource(String fileId) throws IOException {
        Files.writeString(cacheDir.resolve(fileId + ".full"), "video-bytes");
    }

    // ── runJob ────────────────────────────────────────────────────────────────

    @Test
    void runJob_success_publishesAndMarksReady() throws IOException {
        writeSource(FILE);

        worker.runJob(row);

        assertThat(row.getHlsStatus()).isEqualTo(HlsStatus.READY);
        assertThat(row.getHlsError()).isNull();
        assertThat(storage.resolvePlaylist(FILE)).exists();
        assertThat(storage.tmpDir(FILE)).doesNotExist();
        verify(ffmpeg).segment(eq(cacheDir.resolve(FILE + ".full")), eq(storage.tmpDir(FILE)), eq(Duration.ofMinutes(10)));
    }

    @Test
    void runJob_sourceMissing_errorWithoutProbe() {
        worker.runJob(row);

        assertError(HlsErrorCode.SOURCE_MISSING);
        verify(ffmpeg, never()).probe(any(Path.class));
    }

    @Test
    void runJob_unsupportedCodec_errorWithoutSegment() throws IOException {
        writeSource(FILE);
        when(ffmpeg.probe(any(Path.class))).thenReturn(new ProbeResult(List.of("hevc"), List.of("aac")));

        worker.runJob(row);

        assertError(HlsErrorCode.UNSUPPORTED_CODEC);
        verify(ffmpeg, never()).segment(any(Path.class), any(Path.class), any(Duration.class));
    }

    @Test
    void runJob_lowDisk_errorAndFfmpegNeverStarted() throws IOException {
        writeSource(FILE);
        props.getHls().setMinFreeDiskBytes(Long.MAX_VALUE / 2);

        worker.runJob(row);

        assertError(HlsErrorCode.DISK_FULL);
        verify(ffmpeg, never()).segment(any(Path.class), any(Path.class), any(Duration.class));
    }

    @Test
    void runJob_timeout_errorAndTmpRemoved() throws IOException {
        writeSource(FILE);
        doAnswer(inv -> {
            Path dir = inv.getArgument(1);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("seg_0.ts"), "partial");
            throw new HlsException(HlsErrorCode.TIMEOUT);
        }).when(ffmpeg).segment(any(Path.class), any(Path.class), any(Duration.class));

        worker.runJob(row);

        assertError(HlsErrorCode.TIMEOUT);
        assertThat(storage.tmpDir(FILE)).doesNotExist();
        assertThat(storage.dir(FILE)).doesNotExist();
    }

    @Test
    void runJob_unexpectedException_isFfmpegFailed() throws IOException {
        writeSource(FILE);
        doThrow(new IllegalStateException("boom")).when(ffmpeg).probe(any(Path.class));

        worker.runJob(row);

        assertError(HlsErrorCode.FFMPEG_FAILED);
    }

    @Test
    void runJob_statusTurnedOffDuringFfmpeg_deletesTmpAndKeepsNone() throws IOException {
        writeSource(FILE);
        doAnswer(inv -> {
            Path dir = inv.getArgument(1);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(HlsStorage.PLAYLIST), "#EXTM3U");
            row.setHlsStatus(HlsStatus.NONE);
            return null;
        }).when(ffmpeg).segment(any(Path.class), any(Path.class), any(Duration.class));

        worker.runJob(row);

        assertThat(row.getHlsStatus()).isEqualTo(HlsStatus.NONE);
        assertThat(storage.tmpDir(FILE)).doesNotExist();
        assertThat(storage.dir(FILE)).doesNotExist();
        verify(repo, never()).save(any(VideoSource.class));
    }

    @Test
    void runJob_rowDeletedDuringFfmpeg_deletesTmp() throws IOException {
        writeSource(FILE);
        doAnswer(inv -> {
            Path dir = inv.getArgument(1);
            Files.createDirectories(dir);
            when(repo.findById(ID)).thenReturn(Optional.empty());
            return null;
        }).when(ffmpeg).segment(any(Path.class), any(Path.class), any(Duration.class));

        worker.runJob(row);

        assertThat(storage.tmpDir(FILE)).doesNotExist();
        assertThat(storage.dir(FILE)).doesNotExist();
    }

    @Test
    void runJob_failureAfterUserTurnedOff_doesNotOverwriteNone() {
        row.setHlsStatus(HlsStatus.NONE);

        worker.runJob(row); // source missing

        assertThat(row.getHlsStatus()).isEqualTo(HlsStatus.NONE);
        assertThat(row.getHlsError()).isNull();
    }

    @Test
    void runJob_statusFlipsToNoneBetweenRereadAndMarkReady_deletesPublishedDirAndKeepsNone() throws IOException {
        writeSource(FILE);
        doAnswer(inv -> {
            row.setHlsStatus(HlsStatus.NONE); // owner turned HLS off while the worker held the lock
            return 0;
        }).when(repo).markHlsReady(ID);

        worker.runJob(row);

        assertThat(row.getHlsStatus()).isEqualTo(HlsStatus.NONE);
        assertThat(storage.dir(FILE)).doesNotExist();
        assertThat(storage.tmpDir(FILE)).doesNotExist();
    }

    @Test
    void runJob_failureAfterDeleteThenPut_doesNotOverwritePending() throws IOException {
        writeSource(FILE);
        doAnswer(inv -> {
            row.setHlsStatus(HlsStatus.PENDING); // DELETE then PUT during the job
            throw new HlsException(HlsErrorCode.TIMEOUT);
        }).when(ffmpeg).probe(any(Path.class));

        worker.runJob(row);

        assertThat(row.getHlsStatus()).isEqualTo(HlsStatus.PENDING);
        assertThat(row.getHlsError()).isNull();
    }

    private void assertError(HlsErrorCode code) {
        assertThat(row.getHlsStatus()).isEqualTo(HlsStatus.ERROR);
        assertThat(row.getHlsError()).isEqualTo(code.name());
    }

    // ── drain ─────────────────────────────────────────────────────────────────

    @Test
    void drain_notReady_doesNothing() {
        HlsTranscodeWorker cold = new HlsTranscodeWorker(props, repo, storage, ffmpeg, locks, Runnable::run, cacheDir);

        cold.drain();
        cold.wakeUp();

        verify(repo, never()).findHlsQueue();
    }

    @Test
    void drain_threePending_runsOneAtATimeInOrder() throws IOException {
        VideoSource a = source(1, "a", HlsStatus.PENDING);
        VideoSource b = source(2, "b", HlsStatus.PENDING);
        VideoSource c = source(3, "c", HlsStatus.PENDING);
        List<VideoSource> queue = new java.util.ArrayList<>(List.of(a, b, c));
        for (VideoSource s : queue) {
            writeSource(s.getSourceId());
            rows.put(s.getId(), s);
            when(repo.findById(s.getId())).thenReturn(Optional.of(s));
        }
        when(repo.findHlsQueue()).thenAnswer(inv -> queue.stream().filter(s -> s.getHlsStatus() == HlsStatus.PENDING).toList());
        when(repo.claimHls(anyLong())).thenAnswer(inv -> {
            VideoSource s = queue.stream().filter(x -> x.getId().equals(inv.getArgument(0))).findFirst().orElseThrow();
            s.setHlsStatus(HlsStatus.PROCESSING);
            return 1;
        });
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        List<String> order = new java.util.ArrayList<>();
        doAnswer(inv -> {
            int now = running.incrementAndGet();
            maxRunning.accumulateAndGet(now, Math::max);
            Path dir = inv.getArgument(1);
            order.add(dir.getFileName().toString());
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(HlsStorage.PLAYLIST), "#EXTM3U");
            running.decrementAndGet();
            return null;
        }).when(ffmpeg).segment(any(Path.class), any(Path.class), any(Duration.class));

        worker.drain();

        assertThat(order).containsExactly("a.tmp", "b.tmp", "c.tmp");
        assertThat(maxRunning.get()).isEqualTo(1);
        assertThat(queue).allMatch(s -> s.getHlsStatus() == HlsStatus.READY);
        assertThat(locks.isHeld("a") || locks.isHeld("b") || locks.isHeld("c")).isFalse();
    }

    @Test
    void drain_lockedFile_staysPendingAndDoesNotSpin() {
        VideoSource busy = source(1, "busy", HlsStatus.PENDING);
        when(repo.findHlsQueue()).thenReturn(List.of(busy));
        locks.tryClaim("busy");

        worker.drain();

        verify(repo, times(1)).findHlsQueue();
        verify(repo, never()).claimHls(anyLong());
        assertThat(busy.getHlsStatus()).isEqualTo(HlsStatus.PENDING);
        assertThat(locks.isHeld("busy")).isTrue(); // still the other holder's
    }

    @Test
    void drain_busyRowSkipped_nextRowTaken() throws IOException {
        VideoSource busy = source(1, "busy", HlsStatus.PENDING);
        VideoSource free = source(ID, FILE, HlsStatus.PENDING);
        writeSource(FILE);
        locks.tryClaim("busy");
        when(repo.findHlsQueue()).thenReturn(List.of(busy), List.of(busy));
        when(repo.claimHls(ID)).thenReturn(1);
        when(repo.findHlsQueue()).thenReturn(List.of(busy, free)).thenReturn(List.of(busy));

        worker.drain();

        verify(repo).claimHls(ID);
        verify(repo, never()).claimHls(1L);
        assertThat(locks.isHeld(FILE)).isFalse();
    }

    @Test
    void drain_lostClaim_releasesLockAndStops() {
        VideoSource lost = source(ID, FILE, HlsStatus.PENDING);
        when(repo.findHlsQueue()).thenReturn(List.of(lost));
        when(repo.claimHls(ID)).thenReturn(0);

        worker.drain();

        verify(ffmpeg, never()).probe(any(Path.class));
        assertThat(locks.isHeld(FILE)).isFalse();
    }

    @Test
    void drain_queueQueryFails_releasesPermit() {
        when(repo.findHlsQueue()).thenThrow(new IllegalStateException("db down")).thenReturn(List.of());

        worker.drain();
        worker.drain();

        verify(repo, times(2)).findHlsQueue();
    }

    @Test
    void drain_invalidFileId_marksSourceMissing() {
        VideoSource bad = source(ID, "../x", HlsStatus.PENDING);
        when(repo.findHlsQueue()).thenReturn(List.of(bad));
        when(repo.claimHls(ID)).thenReturn(1);

        worker.drain();

        assertThat(row.getHlsStatus()).isEqualTo(HlsStatus.ERROR);
        assertThat(row.getHlsError()).isEqualTo(HlsErrorCode.SOURCE_MISSING.name());
    }

    @Test
    void drain_rejectedExecution_failsJobAndReleasesLock() {
        VideoSource pending = source(ID, FILE, HlsStatus.PENDING);
        when(repo.findHlsQueue()).thenReturn(List.of(pending));
        when(repo.claimHls(ID)).thenReturn(1);
        HlsTranscodeWorker w = new HlsTranscodeWorker(props, repo, storage, ffmpeg, locks, r -> {
            throw new RejectedExecutionException("full");
        }, cacheDir);
        w.markReady();

        w.drain();

        assertThat(row.getHlsStatus()).isEqualTo(HlsStatus.ERROR);
        assertThat(locks.isHeld(FILE)).isFalse();
    }

    @Test
    void poll_delegatesToDrain() {
        when(repo.findHlsQueue()).thenReturn(List.of());

        worker.poll();

        verify(repo).findHlsQueue();
    }

    // ── startup recovery ──────────────────────────────────────────────────────

    @Test
    void onApplicationReady_recoversState() throws IOException {
        when(repo.resetHlsProcessing()).thenReturn(2);
        Files.createDirectories(storage.tmpDir("stale"));
        Files.createDirectories(storage.dir("ok"));
        Files.createDirectories(storage.dir("orphan"));
        Files.createDirectories(storage.dir("notReady"));
        VideoSource okRow = source(1, "ok", HlsStatus.READY);
        VideoSource gone = source(ID, "gone", HlsStatus.READY);
        VideoSource notReady = source(3, "notReady", HlsStatus.PENDING);
        gone.setId(ID);
        when(repo.findAllByHlsStatus(HlsStatus.READY)).thenReturn(List.of(okRow, row));
        row.setSourceId("gone");
        row.setHlsStatus(HlsStatus.READY);
        when(repo.findBySourceId("ok")).thenReturn(Optional.of(okRow));
        when(repo.findBySourceId("orphan")).thenReturn(Optional.empty());
        when(repo.findBySourceId("notReady")).thenReturn(Optional.of(notReady));
        when(repo.findHlsQueue()).thenReturn(List.of());
        HlsTranscodeWorker fresh = new HlsTranscodeWorker(props, repo, storage, ffmpeg, locks, Runnable::run, cacheDir);

        fresh.onApplicationReady();

        verify(repo).resetHlsProcessing();
        assertThat(storage.tmpDir("stale")).doesNotExist();
        assertThat(storage.dir("ok")).exists();
        assertThat(storage.dir("orphan")).doesNotExist();
        assertThat(storage.dir("notReady")).doesNotExist();
        verify(repo).markHlsOutputMissing(ID);
        verify(repo, never()).markHlsOutputMissing(1L);
    }

    @Test
    void onApplicationReady_recoveryFailure_stillBecomesReady() {
        when(repo.resetHlsProcessing()).thenThrow(new IllegalStateException("db down"));
        HlsTranscodeWorker fresh = new HlsTranscodeWorker(props, repo, storage, ffmpeg, locks, Runnable::run, cacheDir);

        fresh.onApplicationReady();
        fresh.drain();

        verify(repo, atLeastOnce()).findHlsQueue(); // the wake-up thread may add a second call
    }
}
