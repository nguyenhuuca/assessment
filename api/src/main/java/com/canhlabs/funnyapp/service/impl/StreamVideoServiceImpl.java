package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.VideoSource;
import com.canhlabs.funnyapp.dto.StreamChunkResult;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.StreamVideoService;
import com.canhlabs.funnyapp.service.VideoCacheService;
import com.canhlabs.funnyapp.share.AppConstant;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.canhlabs.funnyapp.share.AppConstant.FOLDER_ID;

@Slf4j
@Service
public class StreamVideoServiceImpl implements StreamVideoService {

    private final Drive drive;
    private VideoSourceRepository videoSourceRepository;
    private VideoCacheService videoCacheService;
    private ChatGptService chatGptService;
    @Autowired
    public void injectChatGptService(ChatGptService chatGptService) {
        this.chatGptService = chatGptService;
    }

    @Autowired
    public void injectCacheService(VideoCacheService videoCacheService) {
        this.videoCacheService = videoCacheService;
    }

    @Autowired
    public void injectRepo(VideoSourceRepository videoSourceRepository) {
        this.videoSourceRepository = videoSourceRepository;
    }

    public StreamVideoServiceImpl(Drive drive) {
        this.drive = drive;
    }

    @Override
    @WithSpan
    public InputStream getPartialFile(String fileId, long start, long end) throws IOException {
        log.info("Requesting video {} range: {}-{}", fileId, start, end);
        boolean isCacheRange = (start == 0 && end < AppConstant.CACHE_SIZE);
        if (isCacheRange) {
            if (!videoCacheService.hasCache(fileId, end + 1)) {
                log.info("📥 Cache miss for file {}, fetching Google Drive", fileId);
                try (InputStream in = new BufferedInputStream(fetchFromGoogleDrive(fileId, 0, end))) {
                    videoCacheService.saveToCache(fileId, in);
                }
            }
            log.info("📤 Serving file {} from disk cache", fileId);
            return videoCacheService.getCache(fileId);
        }

        log.info("🌐 Fetching file {} from Google Drive by range {}-{}", fileId, start, end);
        return fetchFromGoogleDrive(fileId, start, end);
    }

    @Override
    @WithSpan
    public StreamChunkResult getPartialFileByChunk(String fileId, long start, long end) throws IOException {
        if (videoCacheService.hasChunk(fileId, start, end)) {
            log.info("🟢 Cache hit: {} ({} - {})", fileId, start, end);
            return  StreamChunkResult.builder()
                    .stream(videoCacheService.getChunk(fileId, start, end))
                    .actualStart(start)
                    .actualEnd(end)
                    .build();

        }

        log.info("🔴 Cache miss: fetching {} ({} - {}) from Google Drive", fileId, start, end);
        InputStream googleStream = fetchFromGoogleDrive(fileId, start, end);
        try (BufferedInputStream bufferedStream = new BufferedInputStream(googleStream)) {
            log.info("💾 Saving chunk {} ({} - {}), size ≈ {} bytes", fileId, start, end, (end - start + 1));
            videoCacheService.saveChunk(fileId, start, end, bufferedStream);
        } catch (IOException e) {
            log.warn("⚠️ Failed to save chunk to cache: {} ({} - {}), fallback to direct stream", fileId, start, end);
            return StreamChunkResult.builder()
                    .stream(fetchFromGoogleDrive(fileId, start, end))
                    .actualStart(start)
                    .actualEnd(end)
                    .build();

        }
        return  StreamChunkResult.builder()
                .stream(videoCacheService.getChunk(fileId, start, end))
                .actualStart(start)
                .actualEnd(end)
                .build();
    }

    @WithSpan
    @Override
    public StreamChunkResult getPartialFileUsingRAF(String fileId, long start, long end) throws IOException {
        InputStream stream = videoCacheService.getFileRangeFromDisk(fileId, start, end);
        return  StreamChunkResult.builder()
                .stream(stream)
                .actualStart(start)
                .actualEnd(end)
                .build();
    }

    @WithSpan
    private InputStream fetchFromGoogleDrive(String fileId, long start, long end) throws IOException {
        GenericUrl url = new GenericUrl("https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media");

        HttpRequest request = drive.getRequestFactory()
                .buildGetRequest(url);
        request.getHeaders().setRange("bytes=" + start + "-" + end);
        log.info("Start stream from google drive , field {} by range: {}-{} and request {}", fileId, start, end, request);
        return request.execute().getContent();
    }

    @WithSpan
    @Override
    public long getFileSize(String fileId) throws IOException {
        return videoCacheService.getFileSizeFromDisk(fileId);
//        File file = drive.files().get(fileId).setFields("size").execute();
//        return file.getSize();
    }

    @WithSpan
    @Override
    public List<VideoDto> getVideosToStream() {
        return videoSourceRepository.findAllByOrderByCreatedAtDesc().stream()
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

    public void downloadFileFromFolder(String folderId, String uploadedAfter) throws IOException {
        List<File> files = listFilesInFolder(folderId, uploadedAfter);

        for (File file : files) {
            java.io.File localFile = new java.io.File(AppConstant.CACHE_DIR, file.getId().concat(".full"));
            if (localFile.exists()) {
                log.info("✅ File already exists: {}, skipping", file.getName());
                continue;
            }

            log.info("⬇️ Downloading {} ({} bytes)", file.getName(), file.getSize());
            downloadFile(file.getId(), localFile);
        }
    }

    public List<File> listFilesInFolder(String folderId, String uploadedAfter) throws IOException {
        String query = String.format("'%s' in parents and trashed = false and createdTime >= '%s'", folderId, uploadedAfter);
        return drive.files().list()
                .setQ(query)
                .setFields("files(id, name, size, createdTime)")
                .execute()
                .getFiles();
    }

    public void downloadFile(String fileId, java.io.File destination) throws IOException {
        try (OutputStream out = new FileOutputStream(destination)) {
            drive.files().get(fileId).executeMediaAndDownloadTo(out);
        }
    }

    @Override
    @WithSpan
    public void shareFile(Drive drive, String fileId, String email) throws IOException {
        Permission permission = new Permission()
                .setType("user")
                .setRole("reader")
                .setEmailAddress(email);

        drive.permissions().create(fileId, permission)
                .setSendNotificationEmail(false)
                .execute();
    }

    @WithSpan
    public void saveInfo(String fileId, String title) {
        if (!videoSourceRepository.existsBySourceId(fileId)) {
            String desc = chatGptService.makePoem(title);
            VideoSource entity = VideoSource.builder()
                    .videoId(System.nanoTime())
                    .sourceType("google_drive")
                    .sourceId(fileId)
                    .title(title)
                    .desc(desc)
                    .credentialsRef("")
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
                saveInfo(file.getId(), file.getName().replaceFirst("[.][^.]+$", ""));
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
        dto.setUserShared("unknown"); // set nếu có user info
        dto.setTitle(source.getTitle());
        dto.setDesc(source.getDesc());
        dto.setUrlLink("https://canh-labs.com/api/v1/funny-app/video-stream/stream/" + source.getSourceId());
        dto.setEmbedLink("https://canh-labs.com/api/v1/funny-app/video-stream/stream/" + source.getSourceId());
        return dto;
    }
}
