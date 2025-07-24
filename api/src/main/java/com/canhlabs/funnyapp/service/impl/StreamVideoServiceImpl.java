package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.cache.VideoCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.domain.VideoSource;
import com.canhlabs.funnyapp.dto.StreamChunkResult;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.FfmpegService;
import com.canhlabs.funnyapp.service.StreamVideoService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import com.canhlabs.funnyapp.share.AppConstant;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

import static com.canhlabs.funnyapp.share.AppConstant.FOLDER_ID;

@Slf4j
@Service
public class StreamVideoServiceImpl implements StreamVideoService {

    private final Drive drive;
    private VideoSourceRepository videoSourceRepository;
    private VideoStorageService videoStorageService;
    private ChatGptService chatGptService;
    private VideoCache videoCache;
    private FfmpegService ffmpegService;
    private AppProperties appProps;

    @Autowired
    public void injectAppProperties(AppProperties appProps) {
        this.appProps = appProps;
    }

    @Autowired
    public void injectFfmpegService(FfmpegService ffmpegService) {
        this.ffmpegService = ffmpegService;
    }

    @Autowired
    public void injectVideoCacheStore(VideoCache videoCache) {
        this.videoCache = videoCache;
    }

    @Autowired
    public void injectChatGptService(ChatGptService chatGptService) {
        this.chatGptService = chatGptService;
    }

    @Autowired
    public void injectCacheService(VideoStorageService videoStorageService) {
        this.videoStorageService = videoStorageService;
    }

    @Autowired
    public void injectRepo(VideoSourceRepository videoSourceRepository) {
        this.videoSourceRepository = videoSourceRepository;
    }

    public StreamVideoServiceImpl(Drive drive) {
        this.drive = drive;
    }


    @WithSpan
    @Override
    public StreamChunkResult getPartialFileUsingRAF(String fileId, long start, long end) throws IOException {
        InputStream stream = videoCache.getChunkStream(fileId, start, end, () -> {
            log.info("🔴 Cache miss: fetching {} ({} - {}) from disk", fileId, start, end);
            return videoStorageService.getFileRangeFromDisk(fileId, start, end).readAllBytes();
        });

        return StreamChunkResult.builder()
                .stream(stream)
                .actualStart(start)
                .actualEnd(end)
                .build();
    }

    @WithSpan
    @Override
    public long getFileSize(String fileId) throws IOException {
        return videoStorageService.getFileSizeFromDisk(fileId);
    }

    @AuditLog("getALLShare")
    @WithSpan
    @Override
    public List<VideoDto> getVideosToStream() {
        return videoSourceRepository.findAllByIsHideOrderByCreatedAtDesc(Boolean.FALSE).stream()
                .map(this::toDto)
                .toList();
    }

    @WithSpan
    @Override
    public VideoDto getVideoById(Long id) {
        VideoSource source = videoSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with id: " + id));
        return toDto(source);
    }

    @WithSpan
    @Override
    public VideoDto getVideoBySourceId(String sourceId) {
        VideoSource source = videoSourceRepository.findBySourceId(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with sourceId: " + sourceId));
        return toDto(source);
    }

    @WithSpan
    List<File> listFilesInFolder(Drive drive, String folderId) throws IOException {
        // String query = String.format("'%s' in parents and trashed = false", folderId);
        Instant fifteenMinutesAgo = Instant.now().minus(Duration.ofMinutes(15));
        String isoTime = DateTimeFormatter.ISO_INSTANT.format(fifteenMinutesAgo);

        String query = String.format("'%s' in parents and trashed = false and createdTime > '%s'", folderId, isoTime);
        log.info("Querying files in folder {}: {}", folderId, query);
        List<File> files = new ArrayList<>();
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
    public void downloadFileFromFolder(String folderId, String uploadedAfter) throws IOException {
        List<File> files = listFilesInFolder(folderId, uploadedAfter);
        if (files.isEmpty()) {
            log.info("No new files found in folder {}", folderId);
            return;
        }

        try (var scope = new StructuredTaskScope<>("download", Thread.ofPlatform().factory())) {
            for (File file : files) {
                scope.fork(() -> {
                    java.io.File localFile = new java.io.File(AppConstant.CACHE_DIR, file.getId().concat(".full"));
                    if (localFile.exists()) {
                        log.info("✅ File already exists: {}, skipping", file.getName());
                        return null;
                    }

                    log.info("⬇️ Downloading {} ({} bytes)", file.getName(), file.getSize());
                    downloadFile(file.getId(), localFile);
                    log.info("Downloaded file {} completely", file.getName());
                    // ✅ Generate thumbnail
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
    public List<File> listFilesInFolder(String folderId, String uploadedAfter) throws IOException {
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
    public void saveInfo(String fileId, String title, String thumbnailPath) {
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

    @WithSpan
    @Override
    public void shareFilesInFolder() {
        try {
            List<File> files = listFilesInFolder(drive, FOLDER_ID);
            for (File file : files) {
                saveInfo(file.getId(), file.getName().replaceFirst("[.][^.]+$", ""), "");
                log.info("Shared file: {}", file.getName());
            }

        } catch (IOException e) {
            log.error("Error listing files in folder", e);
        }
    }

    @WithSpan
    @Override
    public void updateDesc() {
        List<VideoSource> sources = videoSourceRepository.findAllByDescIsNullOrDesc("");
        for (VideoSource source : sources) {
            String desc = chatGptService.makePoem(source.getTitle());
            source.setDesc(desc);
            videoSourceRepository.save(source);
            log.info("Updated description for video ID: {}", source.getId());
        }
    }

    public VideoDto toDto(VideoSource source) {
        VideoDto dto = new VideoDto();
        dto.setId(source.getId());
        dto.setFileId(source.getSourceId());
        dto.setUserShared("unknown"); // set nếu có user info
        dto.setTitle(source.getTitle());
        dto.setDesc(source.getDesc());
        dto.setUrlLink("https://canh-labs.com/api/v1/funny-app/video-stream/stream/" + source.getSourceId());
        dto.setEmbedLink("https://canh-labs.com/api/v1/funny-app/video-stream/stream/" + source.getSourceId());
        return dto;
    }
}
