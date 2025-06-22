package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.domain.VideoSource;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class GoogleDriveVideoService {
    private final Drive drive;
    private final AppProperties appProperties;
    private VideoSourceRepository videoSourceRepository;

    @Autowired
    public void injectRepo(VideoSourceRepository videoSourceRepository) {
        this.videoSourceRepository = videoSourceRepository;
    }

    public GoogleDriveVideoService(AppProperties appProperties, Drive drive) {
        this.appProperties = appProperties;
        this.drive = drive;
    }

    public InputStream getPartialFile(String fileId, long start, long end) throws IOException {
        HttpRequestInitializer requestInitializer = drive.getRequestFactory().getInitializer();
        GenericUrl url = new GenericUrl("https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media");

        HttpRequest request = drive.getRequestFactory()
                .buildGetRequest(url);
        request.getHeaders().setRange("bytes=" + start + "-" + end);
        return request.execute().getContent();
    }

    public long getFileSize(String fileId) throws IOException {
        File file = drive.files().get(fileId).setFields("size").execute();
        return file.getSize();
    }

    public List<VideoDto> getVideosToStream() {
        return videoSourceRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public VideoDto getVideoById(Long id) {
        VideoSource source = videoSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with id: " + id));
        return toDto(source);
    }
    public VideoDto getVideoBySourceId(String sourceId) {
        VideoSource source = videoSourceRepository.findBySourceId(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with sourceId: " + sourceId));
        return toDto(source);
    }

    public VideoDto toDto(VideoSource source) {
        VideoDto dto = new VideoDto();
        dto.setId(source.getId());
        dto.setUserShared("unknown"); // set nếu có user info
        dto.setTitle("Video from " + source.getSourceType());
        dto.setDesc("Auto-generated video description");
        dto.setUrlLink("https://canh-labs.com//v1/funny-app/video-stream/stream/" + source.getSourceId());
        dto.setEmbedLink("https://canh-labs.com/v1/funny-app/video-stream/stream/" + source.getSourceId());
        return dto;
    }
}
