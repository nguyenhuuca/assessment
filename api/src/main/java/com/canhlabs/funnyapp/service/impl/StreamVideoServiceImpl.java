package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.cache.VideoCache;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.streaming.StreamChunkResult;
import com.canhlabs.funnyapp.dto.video.VideoDto;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.HlsPlaybackService;
import com.canhlabs.funnyapp.service.StreamVideoService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
public class StreamVideoServiceImpl implements StreamVideoService {

    private VideoSourceRepository videoSourceRepository;
    private VideoStorageService videoStorageService;
    private ChatGptService chatGptService;
    private VideoCache videoCache;
    private HlsPlaybackService hlsPlaybackService;

    @Autowired
    public void injectHlsPlaybackService(HlsPlaybackService hlsPlaybackService) {
        this.hlsPlaybackService = hlsPlaybackService;
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

    @WithSpan
    @Override
    public StreamChunkResult getPartialFileUsingRAF(String fileId, long start, long end) {
        InputStream stream = videoCache.getChunkStream(fileId, start, end, () -> {
            log.info("Cache miss: fetching {} ({} - {}) from disk", fileId, start, end);
            return videoStorageService.getFileRangeFromDisk(fileId, start, end);
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
        boolean hlsEnabled = hlsPlaybackService.hlsEnabled();
        return videoSourceRepository
                .findAllByIsHideAndStatusOrderByPriorityDescCreatedAtDesc(false, VideoStatus.PUBLISHED).stream()
                .map(v -> toDto(v, hlsEnabled))
                .toList();
    }

    @WithSpan
    @Override
    public VideoDto getVideoById(Long id) {
        // hidden / PENDING / FLAGGED videos answer exactly like a missing one (deep links included)
        VideoSource source = videoSourceRepository.findById(id)
                .filter(v -> !v.isHide() && v.getStatus() == VideoStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with id: " + id));
        return toDto(source, hlsPlaybackService.hlsEnabled());
    }

    @WithSpan
    @Override
    public VideoDto getVideoBySourceId(String sourceId) {
        VideoSource source = videoSourceRepository.findBySourceId(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with sourceId: " + sourceId));
        return toDto(source, hlsPlaybackService.hlsEnabled());
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

    VideoDto toDto(VideoSource source, boolean hlsEnabled) {
        return Converter.toVideoDto(source, hlsEnabled);
    }
}
