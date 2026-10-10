package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.CancelToken;
import com.canhlabs.funnyapp.client.VideoMetadata;
import com.canhlabs.funnyapp.client.YtDlpClient;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.VideoImportJob;
import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.enums.ImportStatus;
import com.canhlabs.funnyapp.exception.ImportErrorCode;
import com.canhlabs.funnyapp.exception.ImportException;
import com.canhlabs.funnyapp.repo.VideoImportJobRepository;
import com.canhlabs.funnyapp.service.DriveUploader;
import com.canhlabs.funnyapp.utils.ImportUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoImportWorkerTest {

    private static final long ID = 7L;
    private static final String URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @TempDir
    Path tmp;

    @Mock VideoImportJobRepository repo;
    @Mock YtDlpClient ytDlp;
    @Mock DriveUploader uploader;

    private AppProperties props;
    private VideoImportJob job;
    private VideoImportWorker worker;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getVideoImport().setEnabled(true);
        props.getVideoImport().setWorkDir(tmp.toString());
        props.getVideoImport().setMinFreeDiskBytes(0);
        job = VideoImportJob.builder().id(ID).sourceUrl(URL).normalizedUrl(URL).platform(ImportPlatform.YOUTUBE)
                .status(ImportStatus.DOWNLOADING).attempts(1).build();
        when(repo.findById(ID)).thenReturn(Optional.of(job));
        when(repo.save(any(VideoImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(uploader.isConfigured()).thenReturn(true);
        worker = newWorker();
    }

    private VideoImportWorker newWorker() {
        VideoImportWorker w = new VideoImportWorker(props, repo, ytDlp, uploader, new ImportUrlValidator(), Runnable::run);
        w.markReady();
        return w;
    }

    private void downloadCreatesFile() {
        when(ytDlp.download(anyString(), any(Path.class), any(), any(CancelToken.class))).thenAnswer(inv -> {
            Path dir = inv.getArgument(1);
            Files.createDirectories(dir);
            Path file = dir.resolve("video.mp4");
            Files.write(file, new byte[2000]);
            return file;
        });
    }

    private Path jobDir() {
        return tmp.resolve(String.valueOf(ID));
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void happyPath_metadataTitle_uploadsAndMarksDone_andCleansWorkDir() {
        downloadCreatesFile();
        when(ytDlp.fetchMetadata(URL)).thenReturn(new VideoMetadata("dQw4w9WgXcQ", "Rick: Roll/ed", 212L));
        when(uploader.upload(any(Path.class), anyString(), anyString(), any(), any(CancelToken.class))).thenAnswer(inv -> {
            assertThat(job.getStatus()).isEqualTo(ImportStatus.UPLOADING);
            assertThat((Path) inv.getArgument(0)).exists();
            return "drive-abc";
        });

        worker.runJob(ID, new CancelToken());

        assertThat(job.getStatus()).isEqualTo(ImportStatus.DONE);
        assertThat(job.getDriveFileId()).isEqualTo("drive-abc");
        assertThat(job.getResolvedTitle()).isEqualTo("Rick Rolled");
        assertThat(job.getProgressPct()).isEqualTo(100);
        assertThat(job.getTotalBytes()).isEqualTo(2000L);
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getErrorCode()).isNull();
        verify(uploader).upload(any(Path.class), eq("Rick Rolled.mp4"), eq("7"), any(), any(CancelToken.class));
        assertThat(jobDir()).doesNotExist();
    }

    @Test
    void requestedTitle_winsOverMetadata_andMetadataIsNotFetched() {
        job.setRequestedTitle("Admin Title");
        downloadCreatesFile();
        when(uploader.upload(any(), anyString(), anyString(), any(), any())).thenReturn("d1");

        worker.runJob(ID, new CancelToken());

        verify(ytDlp, never()).fetchMetadata(anyString());
        verify(uploader).upload(any(), eq("Admin Title.mp4"), anyString(), any(), any());
        assertThat(job.getStatus()).isEqualTo(ImportStatus.DONE);
    }

    @Test
    void metadataFailure_fallsBackToPlatformAndVideoId() {
        downloadCreatesFile();
        when(ytDlp.fetchMetadata(URL)).thenThrow(new ImportException(ImportErrorCode.TIMEOUT));
        when(uploader.upload(any(), anyString(), anyString(), any(), any())).thenReturn("d1");

        worker.runJob(ID, new CancelToken());

        verify(uploader).upload(any(), eq("youtube-dQw4w9WgXcQ.mp4"), anyString(), any(), any());
        assertThat(job.getResolvedTitle()).isEqualTo("youtube-dQw4w9WgXcQ");
        assertThat(job.getStatus()).isEqualTo(ImportStatus.DONE);
    }

    @Test
    void metadataWithUnusableTitle_fallsBack() {
        downloadCreatesFile();
        when(ytDlp.fetchMetadata(URL)).thenReturn(new VideoMetadata("x", "///", null));
        when(uploader.upload(any(), anyString(), anyString(), any(), any())).thenReturn("d1");

        worker.runJob(ID, new CancelToken());

        verify(uploader).upload(any(), eq("youtube-dQw4w9WgXcQ.mp4"), anyString(), any(), any());
    }

    // ── failure paths ─────────────────────────────────────────────────────────

    @Test
    void downloadFailure_marksFailedWithSanitizedMessage_andCleansWorkDir() throws IOException {
        job.setRequestedTitle("T");
        when(ytDlp.download(anyString(), any(Path.class), any(), any(CancelToken.class))).thenAnswer(inv -> {
            Files.createDirectories(jobDir());
            Files.write(jobDir().resolve("video.f1.mp4.part"), new byte[10]);
            throw new ImportException(ImportErrorCode.LOGIN_REQUIRED);
        });

        worker.runJob(ID, new CancelToken());

        assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("LOGIN_REQUIRED");
        assertThat(job.getErrorMessage()).isEqualTo(ImportErrorCode.messageFor("LOGIN_REQUIRED"));
        assertThat(job.getErrorMessage().length()).isLessThanOrEqualTo(500);
        assertThat(job.getFinishedAt()).isNotNull();
        verify(uploader, never()).upload(any(), anyString(), anyString(), any(), any());
        assertThat(jobDir()).doesNotExist();
    }

    @Test
    void uploadFailure_marksFailed_andCleansWorkDir() {
        job.setRequestedTitle("T");
        downloadCreatesFile();
        when(uploader.upload(any(), anyString(), anyString(), any(), any()))
                .thenThrow(new ImportException(ImportErrorCode.DRIVE_UPLOAD_FAILED));

        worker.runJob(ID, new CancelToken());

        assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("DRIVE_UPLOAD_FAILED");
        assertThat(job.getDriveFileId()).isNull();
        assertThat(jobDir()).doesNotExist();
    }

    @Test
    void driveNotConfigured_failsBeforeDownloading() {
        when(uploader.isConfigured()).thenReturn(false);

        worker.runJob(ID, new CancelToken());

        assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("DRIVE_NOT_CONFIGURED");
        verify(ytDlp, never()).download(anyString(), any(), any(), any());
    }

    @Test
    void lowDisk_failsBeforeDownloading() {
        props.getVideoImport().setMinFreeDiskBytes(Long.MAX_VALUE);

        worker.runJob(ID, new CancelToken());

        assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("DISK_LOW");
        verify(ytDlp, never()).download(anyString(), any(), any(), any());
    }

    @Test
    void unexpectedException_isInternalError_withoutLeakingDetail() {
        job.setRequestedTitle("T");
        when(ytDlp.download(anyString(), any(), any(), any())).thenThrow(new IllegalStateException("secret /etc/path"));

        worker.runJob(ID, new CancelToken());

        assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(job.getErrorMessage()).doesNotContain("secret");
    }

    @Test
    void unknownJob_isIgnored() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        worker.runJob(99L, new CancelToken());

        verifyNoInteractions(ytDlp);
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Test
    void cancelDuringDownload_marksCancelled_andCleansWorkDir() {
        job.setRequestedTitle("T");
        when(repo.claimNextDue()).thenReturn(Optional.of(ID), Optional.empty());
        when(ytDlp.download(anyString(), any(Path.class), any(), any(CancelToken.class))).thenAnswer(inv -> {
            Files.createDirectories(jobDir());
            Files.write(jobDir().resolve("video.mp4.part"), new byte[10]);
            assertThat(worker.requestCancel(ID)).isTrue();
            assertThat(((CancelToken) inv.getArgument(3)).isCancelled()).isTrue();
            throw new ImportException(ImportErrorCode.CANCELLED);
        });

        worker.drain();

        assertThat(job.getStatus()).isEqualTo(ImportStatus.CANCELLED);
        assertThat(job.getErrorCode()).isNull();
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(jobDir()).doesNotExist();
        assertThat(worker.requestCancel(ID)).isFalse();
    }

    @Test
    void cancelRequestedButDownloadFinished_stillCancelled_andNothingUploaded() {
        job.setRequestedTitle("T");
        CancelToken token = new CancelToken();
        when(ytDlp.download(anyString(), any(Path.class), any(), any(CancelToken.class))).thenAnswer(inv -> {
            Path dir = inv.getArgument(1);
            Files.createDirectories(dir);
            Path f = dir.resolve("video.mp4");
            Files.write(f, new byte[5]);
            token.cancel();
            return f;
        });

        worker.runJob(ID, token);

        assertThat(job.getStatus()).isEqualTo(ImportStatus.CANCELLED);
        verify(uploader, never()).upload(any(), anyString(), anyString(), any(), any());
        assertThat(jobDir()).doesNotExist();
    }

    @Test
    void requestCancel_unknownJob_isFalse() {
        assertThat(worker.requestCancel(12345L)).isFalse();
    }

    // ── progress ──────────────────────────────────────────────────────────────

    @Test
    void downloadProgress_isThrottled() {
        job.setRequestedTitle("T");
        when(ytDlp.download(anyString(), any(Path.class), any(), any(CancelToken.class))).thenAnswer(inv -> {
            YtDlpClient.ProgressListener l = inv.getArgument(2);
            l.onProgress(10, 100);
            l.onProgress(50, 100);
            l.onProgress(100, 100);
            throw new ImportException(ImportErrorCode.CANCELLED);
        });

        worker.runJob(ID, new CancelToken());

        verify(repo, times(1)).updateProgress(eq(ID), eq(10), eq(10L), eq(100L));
    }

    @Test
    void progress_withoutThrottle_computesPercentAndHandlesUnknownTotal() {
        worker.setProgressIntervalNanos(0);
        job.setRequestedTitle("T");
        when(ytDlp.download(anyString(), any(Path.class), any(), any(CancelToken.class))).thenAnswer(inv -> {
            YtDlpClient.ProgressListener l = inv.getArgument(2);
            l.onProgress(50, 200);
            l.onProgress(500, 200);
            l.onProgress(30, 0);
            throw new ImportException(ImportErrorCode.CANCELLED);
        });

        worker.runJob(ID, new CancelToken());

        verify(repo).updateProgress(ID, 25, 50L, 200L);
        verify(repo).updateProgress(ID, 100, 500L, 200L);
        verify(repo).updateProgress(ID, 0, 30L, 0L);
    }

    @Test
    void progressWriteFailure_doesNotAbortTheJob() {
        worker.setProgressIntervalNanos(0);
        job.setRequestedTitle("T");
        when(repo.updateProgress(anyLong(), anyInt(), anyLong(), anyLong())).thenThrow(new IllegalStateException("db down"));
        when(ytDlp.download(anyString(), any(Path.class), any(), any(CancelToken.class))).thenAnswer(inv -> {
            YtDlpClient.ProgressListener l = inv.getArgument(2);
            l.onProgress(1, 2);
            Path dir = inv.getArgument(1);
            Files.createDirectories(dir);
            Path f = dir.resolve("video.mp4");
            Files.write(f, new byte[1]);
            return f;
        });
        when(uploader.upload(any(), anyString(), anyString(), any(), any())).thenReturn("d");

        worker.runJob(ID, new CancelToken());

        assertThat(job.getStatus()).isEqualTo(ImportStatus.DONE);
    }

    @Test
    void uploadProgress_isForwardedToRepository() {
        job.setRequestedTitle("T");
        downloadCreatesFile();
        when(uploader.upload(any(), anyString(), anyString(), any(), any())).thenAnswer(inv -> {
            DriveUploader.ProgressListener l = inv.getArgument(3);
            l.onProgress(1000, 2000);
            return "d";
        });

        worker.runJob(ID, new CancelToken());

        verify(repo).updateProgress(ID, 50, 1000L, 2000L);
    }

    // ── claim loop ────────────────────────────────────────────────────────────

    @Test
    void drain_claimsJobsUntilNoneLeft() {
        job.setRequestedTitle("T");
        downloadCreatesFile();
        when(uploader.upload(any(), anyString(), anyString(), any(), any())).thenReturn("d");
        when(repo.claimNextDue()).thenReturn(Optional.of(ID), Optional.empty());

        worker.drain();

        assertThat(job.getStatus()).isEqualTo(ImportStatus.DONE);
        verify(repo, org.mockito.Mockito.atLeast(2)).claimNextDue();
    }

    @Test
    void drain_featureDisabled_isNoOp() {
        props.getVideoImport().setEnabled(false);

        worker.drain();
        worker.poll();
        worker.wakeUp();

        verifyNoInteractions(repo);
    }

    @Test
    void drain_beforeStartupRecovery_isNoOp() {
        VideoImportWorker notReady = new VideoImportWorker(props, repo, ytDlp, uploader, new ImportUrlValidator(), Runnable::run);

        notReady.poll();
        notReady.wakeUp();

        verify(repo, never()).claimNextDue();
    }

    @Test
    void drain_claimError_isSwallowed_andPermitReleased() {
        when(repo.claimNextDue()).thenThrow(new IllegalStateException("db down")).thenReturn(Optional.empty());

        worker.drain();
        worker.drain();

        verify(repo, times(2)).claimNextDue();
    }

    @Test
    void drain_respectsMaxConcurrent() {
        props.getVideoImport().setMaxConcurrent(1);
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        VideoImportWorker w = new VideoImportWorker(props, repo, ytDlp, uploader, new ImportUrlValidator(), queued::add);
        w.markReady();
        when(repo.claimNextDue()).thenReturn(Optional.of(ID), Optional.of(8L), Optional.empty());

        w.drain();

        assertThat(queued).hasSize(1);
        verify(repo, times(1)).claimNextDue();
    }

    @Test
    void drain_rejectedExecutor_failsTheClaimedJob() {
        VideoImportWorker w = new VideoImportWorker(props, repo, ytDlp, uploader, new ImportUrlValidator(), r -> {
            throw new RejectedExecutionException("shutdown");
        });
        w.markReady();
        when(repo.claimNextDue()).thenReturn(Optional.of(ID));

        w.drain();

        assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void wakeUp_runsDrainOnAnotherThread() {
        when(repo.claimNextDue()).thenReturn(Optional.empty());

        worker.wakeUp();

        verify(repo, timeout(3000)).claimNextDue();
    }

    @Test
    void defaultExecutorConstructor_runsJobOnVirtualThread() {
        job.setRequestedTitle("T");
        downloadCreatesFile();
        when(uploader.upload(any(), anyString(), anyString(), any(), any())).thenReturn("d");
        when(repo.claimNextDue()).thenReturn(Optional.of(ID), Optional.empty());
        VideoImportWorker w = new VideoImportWorker(props, repo, ytDlp, uploader, new ImportUrlValidator());
        w.markReady();

        w.drain();

        verify(uploader, timeout(5000)).upload(any(), anyString(), anyString(), any(), any());
    }

    // ── startup recovery ──────────────────────────────────────────────────────

    @Test
    void onApplicationReady_resetsInterrupted_cleansOnlyNumericDirs() throws IOException {
        Files.createDirectories(tmp.resolve("123"));
        Files.write(tmp.resolve("123").resolve("video.mp4"), new byte[3]);
        Files.createDirectories(tmp.resolve("keep-me"));
        Files.write(tmp.resolve("note.txt"), new byte[1]);
        when(repo.resetInterrupted()).thenReturn(2);
        when(ytDlp.version()).thenReturn(Optional.of("2026.10.01"));
        when(repo.claimNextDue()).thenReturn(Optional.empty());
        VideoImportWorker fresh = new VideoImportWorker(props, repo, ytDlp, uploader, new ImportUrlValidator(), Runnable::run);

        fresh.onApplicationReady();

        verify(repo).resetInterrupted();
        assertThat(tmp.resolve("123")).doesNotExist();
        assertThat(tmp.resolve("keep-me")).exists();
        assertThat(tmp.resolve("note.txt")).exists();
        verify(repo, timeout(3000)).claimNextDue();
    }

    @Test
    void onApplicationReady_missingYtDlp_stillRecovers() {
        when(ytDlp.version()).thenReturn(Optional.empty());
        when(repo.claimNextDue()).thenReturn(Optional.empty());
        VideoImportWorker fresh = new VideoImportWorker(props, repo, ytDlp, uploader, new ImportUrlValidator(), Runnable::run);

        fresh.onApplicationReady();

        verify(repo).resetInterrupted();
    }

    @Test
    void onApplicationReady_recoveryFailure_doesNotPreventStartup() {
        when(repo.resetInterrupted()).thenThrow(new IllegalStateException("db down"));
        when(ytDlp.version()).thenReturn(Optional.empty());
        VideoImportWorker fresh = new VideoImportWorker(props, repo, ytDlp, uploader, new ImportUrlValidator(), Runnable::run);

        fresh.onApplicationReady();
    }

    @Test
    void onApplicationReady_featureDisabled_doesNothing() {
        props.getVideoImport().setEnabled(false);
        VideoImportWorker fresh = new VideoImportWorker(props, repo, ytDlp, uploader, new ImportUrlValidator(), Runnable::run);

        fresh.onApplicationReady();

        verifyNoInteractions(repo);
        verifyNoInteractions(ytDlp);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Test
    void deleteRecursively_removesTreeAndToleratesMissing() throws IOException {
        Path dir = tmp.resolve("tree");
        Files.createDirectories(dir.resolve("a/b"));
        Files.write(dir.resolve("a/b/f.bin"), new byte[1]);

        VideoImportWorker.deleteRecursively(dir);
        VideoImportWorker.deleteRecursively(dir);

        assertThat(dir).doesNotExist();
    }

    @Test
    void errorMessage_isCappedAt500() {
        ArgumentCaptor<VideoImportJob> saved = ArgumentCaptor.forClass(VideoImportJob.class);
        job.setRequestedTitle("T");
        when(ytDlp.download(anyString(), any(), any(), any())).thenThrow(new ImportException("UNKNOWN_CODE_XYZ"));

        worker.runJob(ID, new CancelToken());

        verify(repo, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(job.getErrorCode()).isEqualTo("UNKNOWN_CODE_XYZ");
        assertThat(job.getErrorMessage().length()).isLessThanOrEqualTo(500);
    }
}
