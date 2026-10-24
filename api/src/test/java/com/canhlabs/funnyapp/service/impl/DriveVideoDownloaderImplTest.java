package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.DriveFileLocks;
import com.canhlabs.funnyapp.cache.VideoCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.exception.InsufficientDiskSpaceException;
import com.canhlabs.funnyapp.service.DriveVideoDownloader;
import com.canhlabs.funnyapp.service.FfmpegService;
import com.google.api.services.drive.Drive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriveVideoDownloaderImplTest {

    @TempDir Path cacheDir;

    Drive drive = mock(Drive.class);
    Drive.Files driveFiles = mock(Drive.Files.class);
    Drive.Files.Get getReq = mock(Drive.Files.Get.class);
    FfmpegService ffmpegService = mock(FfmpegService.class);
    AppProperties appProps = mock(AppProperties.class);
    VideoCache videoCache = mock(VideoCache.class);
    DriveFileLocks locks = new DriveFileLocks();
    VideoSourceRepository videoSourceRepository = mock(VideoSourceRepository.class);
    HlsStorage hlsStorage = mock(HlsStorage.class);
    HlsTranscodeWorker hlsWorker = mock(HlsTranscodeWorker.class);
    AppProperties.VideoImport videoImport = new AppProperties.VideoImport();

    DriveVideoDownloaderImpl downloader;

    @BeforeEach
    void setUp() throws IOException {
        videoImport.setMinFreeDiskBytes(0);
        when(appProps.getVideoImport()).thenReturn(videoImport);
        when(appProps.getImageStoragePath()).thenReturn("images");
        when(appProps.getImageUrl()).thenReturn("http://img.example.com");
        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.get("f1")).thenReturn(getReq);
        downloader = new DriveVideoDownloaderImpl(drive, ffmpegService, appProps, videoCache, locks,
                videoSourceRepository, hlsStorage, hlsWorker, cacheDir);
    }

    private void driveServes(String content) throws IOException {
        doAnswer(inv -> {
            OutputStream out = inv.getArgument(0);
            out.write(content.getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(getReq).executeMediaAndDownloadTo(any());
    }

    @Test
    void download_writesFileEvictsCacheMakesThumbnail() throws IOException {
        driveServes("video-bytes");
        AtomicLong progress = new AtomicLong();

        String url = downloader.download("f1", 11L, progress::set);

        assertThat(Files.readString(cacheDir.resolve("f1.full"))).isEqualTo("video-bytes");
        assertThat(cacheDir.resolve("f1.full.part")).doesNotExist();
        assertThat(progress.get()).isEqualTo(11);
        assertThat(url).isEqualTo("http://img.example.com/f1.jpg");
        verify(videoCache).invalidateFile("f1");
        verify(ffmpegService).generateThumbnail(eq(cacheDir.resolve("f1.full").toAbsolutePath().toString()),
                eq(Paths.get("images/thumbnails", "f1.jpg").toString()));
    }

    private VideoSource hlsRow(HlsStatus status) {
        VideoSource s = new VideoSource();
        s.setSourceId("f1");
        s.setHlsStatus(status);
        s.setHlsError(status == HlsStatus.ERROR ? "TIMEOUT" : null);
        when(videoSourceRepository.findBySourceId("f1")).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void download_readyOrError_deletesHlsDirAndRequeues() throws IOException {
        driveServes("new");
        for (HlsStatus from : new HlsStatus[]{HlsStatus.READY, HlsStatus.ERROR}) {
            VideoSource row = hlsRow(from);

            downloader.download("f1", null, b -> { });

            assertThat(row.getHlsStatus()).isEqualTo(HlsStatus.PENDING);
            assertThat(row.getHlsError()).isNull();
            verify(videoSourceRepository).save(row);
            clearInvocations(videoSourceRepository);
        }
        verify(hlsStorage, times(2)).delete("f1");
        verify(hlsWorker, times(2)).wakeUp();
    }

    @Test
    void download_nonePendingProcessing_deletesDirButKeepsStatus() throws IOException {
        driveServes("new");
        for (HlsStatus from : new HlsStatus[]{HlsStatus.NONE, HlsStatus.PENDING, HlsStatus.PROCESSING}) {
            VideoSource row = hlsRow(from);

            downloader.download("f1", null, b -> { });

            assertThat(row.getHlsStatus()).isEqualTo(from);
        }
        verify(hlsStorage, times(3)).delete("f1");
        verify(videoSourceRepository, never()).save(any(VideoSource.class));
        verify(hlsWorker, never()).wakeUp();
    }

    @Test
    void download_hlsResetFails_downloadStillSucceeds() throws IOException {
        driveServes("new");
        doThrow(new IllegalStateException("io")).when(hlsStorage).delete("f1");

        String url = downloader.download("f1", null, b -> { });

        assertThat(url).isEqualTo("http://img.example.com/f1.jpg");
        assertThat(Files.readString(cacheDir.resolve("f1.full"))).isEqualTo("new");
    }

    @Test
    void download_driveFails_leavesHlsUntouched() throws IOException {
        doAnswer(inv -> {
            throw new IOException("network");
        }).when(getReq).executeMediaAndDownloadTo(any());

        assertThatThrownBy(() -> downloader.download("f1", 100L, b -> { })).isInstanceOf(IOException.class);

        verify(hlsStorage, never()).delete(any());
        verify(hlsWorker, never()).wakeUp();
    }

    @Test
    void download_replacesExistingFile() throws IOException {
        Files.writeString(cacheDir.resolve("f1.full"), "old");
        driveServes("new");

        downloader.download("f1", null, b -> { });

        assertThat(Files.readString(cacheDir.resolve("f1.full"))).isEqualTo("new");
    }

    @Test
    void download_driveFails_keepsOldFileAndRemovesPart() throws IOException {
        Files.writeString(cacheDir.resolve("f1.full"), "old");
        doAnswer(inv -> {
            inv.<OutputStream>getArgument(0).write("partial".getBytes(StandardCharsets.UTF_8));
            throw new IOException("network");
        }).when(getReq).executeMediaAndDownloadTo(any());

        assertThatThrownBy(() -> downloader.download("f1", 100L, b -> { })).isInstanceOf(IOException.class);

        assertThat(Files.readString(cacheDir.resolve("f1.full"))).isEqualTo("old");
        assertThat(cacheDir.resolve("f1.full.part")).doesNotExist();
        verify(videoCache, never()).invalidateFile(anyString());
        verify(ffmpegService, never()).generateThumbnail(anyString(), anyString());
    }

    @Test
    void download_notEnoughDisk_failsBeforeDownloading() throws IOException {
        videoImport.setMinFreeDiskBytes(Long.MAX_VALUE / 2);

        assertThatThrownBy(() -> downloader.download("f1", 10L, b -> { }))
                .isInstanceOf(InsufficientDiskSpaceException.class);
        verify(getReq, never()).executeMediaAndDownloadTo(any());
    }

    @Test
    void deleteStalePartFiles_skipsFilesHeldByARunningSync() throws IOException {
        Files.writeString(cacheDir.resolve("stale.full.part"), "x");
        Files.writeString(cacheDir.resolve("busy.full.part"), "x");
        Files.writeString(cacheDir.resolve("done.full"), "x");
        locks.tryClaim("busy");

        downloader.deleteStalePartFiles();

        assertThat(cacheDir.resolve("stale.full.part")).doesNotExist();
        assertThat(cacheDir.resolve("busy.full.part")).exists();
        assertThat(cacheDir.resolve("done.full")).exists();
    }

    @Test
    void titleOf_stripsExtension() {
        assertThat(DriveVideoDownloader.titleOf("My clip.v2.mp4")).isEqualTo("My clip.v2");
        assertThat(DriveVideoDownloader.titleOf(null)).isNull();
    }
}
