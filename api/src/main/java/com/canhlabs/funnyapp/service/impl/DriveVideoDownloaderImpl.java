package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.DriveFileLocks;
import com.canhlabs.funnyapp.cache.FeedCache;
import com.canhlabs.funnyapp.cache.VideoCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.exception.InsufficientDiskSpaceException;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.DriveVideoDownloader;
import com.canhlabs.funnyapp.service.FfmpegService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.google.api.services.drive.Drive;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.LongConsumer;

@Slf4j
@Service
public class DriveVideoDownloaderImpl implements DriveVideoDownloader {

    static final String EXTENSION = VideoStorageServiceImpl.EXTENSION;
    static final String PART_SUFFIX = ".part";

    private final Drive drive;
    private final FfmpegService ffmpegService;
    private final AppProperties appProps;
    private final VideoCache videoCache;
    private final DriveFileLocks locks;
    private final VideoSourceRepository videoSourceRepository;
    private final HlsStorage hlsStorage;
    private final HlsTranscodeWorker hlsWorker;
    private final FeedCache feedCache;
    private final Path cacheDir;

    @Autowired
    public DriveVideoDownloaderImpl(Drive drive, FfmpegService ffmpegService, AppProperties appProps,
                                    VideoCache videoCache, DriveFileLocks locks,
                                    VideoSourceRepository videoSourceRepository, HlsStorage hlsStorage,
                                    HlsTranscodeWorker hlsWorker, FeedCache feedCache) {
        this(drive, ffmpegService, appProps, videoCache, locks, videoSourceRepository, hlsStorage, hlsWorker,
                feedCache, Paths.get(AppConstant.CACHE_DIR));
    }

    DriveVideoDownloaderImpl(Drive drive, FfmpegService ffmpegService, AppProperties appProps,
                             VideoCache videoCache, DriveFileLocks locks,
                             VideoSourceRepository videoSourceRepository, HlsStorage hlsStorage,
                             HlsTranscodeWorker hlsWorker, FeedCache feedCache, Path cacheDir) {
        this.drive = drive;
        this.ffmpegService = ffmpegService;
        this.appProps = appProps;
        this.videoCache = videoCache;
        this.locks = locks;
        this.videoSourceRepository = videoSourceRepository;
        this.hlsStorage = hlsStorage;
        this.hlsWorker = hlsWorker;
        this.feedCache = feedCache;
        this.cacheDir = cacheDir;
    }

    @WithSpan
    @Override
    public String download(String fileId, Long expectedSize, LongConsumer progress) throws IOException {
        Files.createDirectories(cacheDir);
        checkFreeDisk(expectedSize);
        Path target = cacheDir.resolve(fileId + EXTENSION);
        Path part = cacheDir.resolve(fileId + EXTENSION + PART_SUFFIX);
        try {
            try (OutputStream out = new CountingOutputStream(Files.newOutputStream(part), progress)) {
                drive.files().get(fileId).executeMediaAndDownloadTo(out);
            }
            moveIntoPlace(part, target);
        } finally {
            Files.deleteIfExists(part);
        }
        // the old bytes may still be cached under the same file id
        videoCache.invalidateFile(fileId);
        resetHls(fileId);

        String imageName = fileId + ".jpg";
        String thumbnailPath = Paths.get(appProps.getImageStoragePath().concat("/thumbnails"), imageName).toString();
        ffmpegService.generateThumbnail(target.toAbsolutePath().toString(), thumbnailPath);
        return appProps.getImageUrl() + "/" + imageName;
    }

    /**
     * BR-10: the HLS rendition was made from the old bytes. The caller still holds the file lock, so the
     * directory can go; READY and ERROR rows are queued again, other statuses keep their place.
     */
    private void resetHls(String fileId) {
        if (!HlsStorage.isValidFileId(fileId)) {
            return;
        }
        try {
            hlsStorage.delete(fileId);
            videoSourceRepository.findBySourceId(fileId)
                    .filter(s -> s.getHlsStatus() == HlsStatus.READY || s.getHlsStatus() == HlsStatus.ERROR)
                    .ifPresent(this::requeueHls);
        } catch (RuntimeException e) {
            log.warn("Could not reset the HLS rendition of {}: {}", fileId, e.getMessage());
        }
    }

    private void requeueHls(VideoSource source) {
        source.setHlsStatus(HlsStatus.PENDING);
        source.setHlsError(null);
        videoSourceRepository.save(source);
        feedCache.invalidate();
        hlsWorker.wakeUp();
    }

    /** A crash or restart mid-download leaves .part files behind; downloads never resume, so delete them. */
    @EventListener(ApplicationReadyEvent.class)
    public void deleteStalePartFiles() {
        if (!Files.isDirectory(cacheDir)) {
            return;
        }
        try (DirectoryStream<Path> parts = Files.newDirectoryStream(cacheDir, "*" + EXTENSION + PART_SUFFIX)) {
            for (Path p : parts) {
                String name = p.getFileName().toString();
                String fileId = name.substring(0, name.length() - (EXTENSION + PART_SUFFIX).length());
                if (locks.isHeld(fileId)) {
                    continue; // a sync that started before the app was ready is writing it
                }
                Files.deleteIfExists(p);
                log.info("Deleted stale partial download {}", p.getFileName());
            }
        } catch (IOException e) {
            log.warn("Could not clean partial downloads in {}: {}", cacheDir, e.getMessage());
        }
    }

    private void checkFreeDisk(Long expectedSize) throws IOException {
        long reserve = appProps.getVideoImport().getMinFreeDiskBytes();
        long needed = (expectedSize == null ? 0 : expectedSize) + reserve;
        long usable = Files.getFileStore(cacheDir).getUsableSpace();
        if (usable < needed) {
            throw new InsufficientDiskSpaceException("Need " + needed + " bytes free, have " + usable);
        }
    }

    private static void moveIntoPlace(Path part, Path target) throws IOException {
        try {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Reports the running byte count; the consumer only stores a number, so calling it per write is cheap. */
    static final class CountingOutputStream extends FilterOutputStream {
        private final LongConsumer progress;
        private long count;

        CountingOutputStream(OutputStream out, LongConsumer progress) {
            super(out);
            this.progress = progress;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
            progress.accept(count);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
            progress.accept(count);
        }
    }
}
