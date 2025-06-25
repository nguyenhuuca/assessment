package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.VideoSource;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.StorageVideoService;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GoogleDriveVideoServiceImpl implements StorageVideoService {
    private static final String FOLDER_ID = "1uk7TUSvUkE9if6HYnY4ap2Kj0gSZ5qlz";
    private final Drive drive;
    private VideoSourceRepository videoSourceRepository;

    @Autowired
    public void injectRepo(VideoSourceRepository videoSourceRepository) {
        this.videoSourceRepository = videoSourceRepository;
    }

    public GoogleDriveVideoServiceImpl(Drive drive) {
        this.drive = drive;
    }

    @Override
    public InputStream getPartialFile(String fileId, long start, long end) throws IOException {
        GenericUrl url = new GenericUrl("https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media");

        HttpRequest request = drive.getRequestFactory()
                .buildGetRequest(url);
        request.getHeaders().setRange("bytes=" + start + "-" + end);
        log.info("Start stream field {} by range: {}-{} and request {}", fileId, start, end, request);
        return request.execute().getContent();
    }

    @Override
    public long getFileSize(String fileId) throws IOException {
        File file = drive.files().get(fileId).setFields("size").execute();
        return file.getSize();
    }

    @Override
    public List<VideoDto> getVideosToStream() {
        return videoSourceRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public VideoDto getVideoById(Long id) {
        VideoSource source = videoSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with id: " + id));
        return toDto(source);
    }

    @Override
    public VideoDto getVideoBySourceId(String sourceId) {
        VideoSource source = videoSourceRepository.findBySourceId(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with sourceId: " + sourceId));
        return toDto(source);
    }

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

    @Override
    public void shareFile(Drive drive, String fileId, String email) throws IOException {
        Permission permission = new Permission()
                .setType("user")
                .setRole("reader")
                .setEmailAddress(email);

        drive.permissions().create(fileId, permission)
                .setSendNotificationEmail(false)
                .execute();
    }

    public void saveInfo(String fileId, String title) {
        if (!videoSourceRepository.existsBySourceId(fileId)) {
            VideoSource entity = VideoSource.builder()
                    .videoId(System.nanoTime())
                    .sourceType("google_drive")
                    .sourceId(fileId)
                    .title(title)
                    .desc("Video from Google Drive")
                    .credentialsRef("")
                    .build();
            videoSourceRepository.save(entity);
        }

    }

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
