package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.FfmpegService;
import com.canhlabs.funnyapp.service.VideoAccessService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.LimitedInputStream;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;


@Slf4j
@Service
public class VideoStorageServiceImpl implements VideoStorageService {

    public static final String EXTENSION = ".full";
    private StatsCache statsCache;
    private VideoAccessService videoAccessService;
    private FfmpegService ffmpegService;
    private AppProperties appProps;
    private Drive drive;
    private VideoSourceRepository videoSourceRepository;
    private ChatGptService chatGptService;

    @Autowired
    public void injectChatGptService(ChatGptService chatGptService) {
        this.chatGptService = chatGptService;
    }

    @Autowired
    public void injectVideoSourceRepository(VideoSourceRepository videoSourceRepository) {
        this.videoSourceRepository = videoSourceRepository;
    }

    @Autowired
    public void injectDrive(Drive drive) {
        this.drive = drive;
    }

    @Autowired
    public void injectFfmpegService(FfmpegService ffmpegService) {
        this.ffmpegService = ffmpegService;
    }

    @Autowired
    public void injectAppProperties(AppProperties appProps) {
        this.appProps = appProps;
    }


    @Autowired
    public void injectVideoAccessService(VideoAccessService videoAccessService) {
        this.videoAccessService = videoAccessService;
    }

    @Autowired
    public void injectCacheStatsService(StatsCache statsCache) {
        this.statsCache = statsCache;
    }


    @WithSpan
    @Override
    public InputStream getFileRangeFromDisk(String fileId, long start, long end) throws IOException {
        File file = new File(AppConstant.CACHE_DIR, fileId + EXTENSION);
        if (!file.exists()) {
            throw new FileNotFoundException("Full file not found: " + file.getAbsolutePath());
        }
        statsCache.recordHit(fileId);
        videoAccessService.recordAccess(fileId);
        long length = end - start + 1;
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek(start);

        return new LimitedInputStream(new FileInputStream(raf.getFD()), length, raf);
    }

    @WithSpan
    @Override
    public long getFileSizeFromDisk(String fileId) throws IOException {
        File file = new File(AppConstant.CACHE_DIR, fileId + EXTENSION);
        if (!file.exists()) {
            throw new FileNotFoundException("Full file not found: " + file.getAbsolutePath());
        }
        return file.length();
    }

    @WithSpan
    public void downloadFileFromFolder(String folderId, String uploadedAfter) throws IOException {
        List<com.google.api.services.drive.model.File> files = listFilesInFolder(folderId, uploadedAfter);
        if (files.isEmpty()) {
            log.info("No new files found in folder {}", folderId);
            return;
        }

        try (var scope = new StructuredTaskScope<>("download", Thread.ofPlatform().factory())) {
            for (com.google.api.services.drive.model.File file : files) {
                scope.fork(() -> {
                    java.io.File localFile = new java.io.File(AppConstant.CACHE_DIR, file.getId().concat(EXTENSION));
                    if (localFile.exists()) {
                        log.info("File already exists: {}, skipping", file.getName());
                        return null;
                    }

                    log.info("Downloading {} ({} bytes)", file.getName(), file.getSize());
                    downloadFile(file.getId(), localFile);
                    log.info("Downloaded file {} completely", file.getName());
                    // Generate thumbnail
                    String imageName = file.getId().concat(".jpg");
                    String thumbnailPath = Paths.get(appProps.getImageStoragePath().concat("/thumbnails"), imageName).toString();
                    ffmpegService.generateThumbnail(localFile.getAbsolutePath(), thumbnailPath);
                    // update info in database
                    saveInfo(file.getId(), file.getName().replaceFirst("[.][^.]+$", ""), appProps.getImageUrl().concat("/") + imageName);
                    return null;
                });
            }
            scope.join(); // Wait for all downloads to complete
        } catch (InterruptedException e) {
            log.error("Error downloading files from folder {}: {}", folderId, e.getMessage());
            throw new RuntimeException(e);
        }

    }

    @WithSpan
    @Override
    public void deleteIfEligible(String fileId) {
        // AppConstant.CACHE_DIR, file.getId().concat(EXTENSION)
        Path filePath = Paths.get(AppConstant.CACHE_DIR, fileId.concat(EXTENSION));
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete file {}: {}", filePath, e.getMessage(), e);
        }
    }

    @WithSpan
    List<com.google.api.services.drive.model.File> listFilesInFolder(String folderId, String uploadedAfter) throws IOException {
        String query = String.format("'%s' in parents and trashed = false and createdTime >= '%s'", folderId, uploadedAfter);
        return drive.files().list()
                .setQ(query)
                .setFields("files(id, name, size, createdTime)")
                .execute()
                .getFiles();
    }

    @WithSpan
    void downloadFile(String fileId, java.io.File destination) throws IOException {
        try (OutputStream out = new FileOutputStream(destination)) {
            drive.files().get(fileId).executeMediaAndDownloadTo(out);
        }
    }

    @WithSpan
    List<com.google.api.services.drive.model.File> listFilesInFolder(Drive drive, String folderId) throws IOException {
        Instant fifteenMinutesAgo = Instant.now().minus(Duration.ofMinutes(15));
        String isoTime = DateTimeFormatter.ISO_INSTANT.format(fifteenMinutesAgo);

        String query = String.format("'%s' in parents and trashed = false and createdTime > '%s'", folderId, isoTime);
        log.info("Querying files in folder {}: {}", folderId, query);
        List<com.google.api.services.drive.model.File> files = new ArrayList<>();
        Drive.Files.List request = drive.files().list()
                .setQ(query)
                .setFields("nextPageToken, files(id, name, createdTime)");
        do {
            FileList fileList = request.execute();
            files.addAll(fileList.getFiles());
            request.setPageToken(fileList.getNextPageToken());
        } while (request.getPageToken() != null && !request.getPageToken().isEmpty());
        log.info("Found {} files in folder {}", files.size(), folderId);
        return files;
    }

    @WithSpan
    void saveInfo(String fileId, String title, String thumbnailPath) {
        if (!videoSourceRepository.existsBySourceId(fileId)) {
            String desc = chatGptService.makePoem(title);
            VideoSource entity = VideoSource.builder()
                    .videoId(System.nanoTime())
                    .sourceType("google_drive")
                    .sourceId(fileId)
                    .title(title)
                    .desc(desc)
                    .credentialsRef("")
                    .thumbnailPath(thumbnailPath)
                    .build();
            videoSourceRepository.save(entity);
        }

    }
}