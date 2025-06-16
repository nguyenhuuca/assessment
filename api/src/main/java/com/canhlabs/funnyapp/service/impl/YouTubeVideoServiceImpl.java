package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.YouTubeApiClient;
import com.canhlabs.funnyapp.domain.YouTubeVideo;
import com.canhlabs.funnyapp.repo.YoutubeVideoRepo;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.dto.YouTubeVideoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class YouTubeVideoServiceImpl implements com.canhlabs.funnyapp.service.YouTubeVideoService {

    private final YoutubeVideoRepo repository;
    private final ChatGptService chatGptService;
    private final YouTubeApiClient youTubeApiClient;

    public YouTubeVideoServiceImpl(YoutubeVideoRepo repository, ChatGptService chatGptService, YouTubeApiClient youTubeApiClient) {
        this.repository = repository;
        this.chatGptService = chatGptService;
        this.youTubeApiClient = youTubeApiClient;
    }

    /**
     * Get list of video IDs by cacheKey with flow: cache -> DB -> ChatGPT
     */
    @Override
    public List<VideoDto> getVideoIds() {

        // Step 2: Try get from DB
        List<YouTubeVideo> videosFromDb = repository.findAllBySource("youtube");
        if (!videosFromDb.isEmpty()) {
            log.info("Get from Db");

            return videosFromDb.stream()
                    .map(this::mapToDto)
                    .toList();
        }

        return Collections.emptyList();
    }

    public VideoDto mapToDto(YouTubeVideo entity) {
        VideoDto dto = new VideoDto();
        dto.setId(entity.getId());
        dto.setUserShared("system");
        dto.setTitle(entity.getTitle());
        dto.setDesc(entity.getDesc());
        dto.setEmbedLink(entity.getEmbedLink());
        dto.setUrlLink(entity.getUrlLink());
        return dto;
    }


    public void updateVideoDetails(List<YouTubeVideoDTO> dtos) {
        // get videos Ids
        List<String> videoIds = dtos.stream()
                .map(YouTubeVideoDTO::getVideoId)
                .toList();

        // find existed in db
        List<YouTubeVideo> existingVideos = repository.findByVideoIdIn(videoIds);

        // Map videoId -> entity
        Map<String, YouTubeVideo> existingMap = existingVideos.stream()
                .collect(Collectors.toMap(YouTubeVideo::getVideoId, Function.identity()));

        List<YouTubeVideo> upserts = new ArrayList<>();

        for (YouTubeVideoDTO dto : dtos) {
            YouTubeVideo entity = existingMap.getOrDefault(dto.getVideoId(), new YouTubeVideo());

            entity.setVideoId(dto.getVideoId());
            entity.setTitle(dto.getTitle());
            entity.setDesc(dto.getDescription());
            entity.setUrlLink(dto.getUrlLink());
            entity.setEmbedLink(dto.getEmbedLink());
            entity.setUpCount(dto.getUpCount());
            entity.setDownCount(dto.getDownCount());
            entity.setSource("youtube");

            // Nếu là mới thì set createdAt
            if (entity.getId() == null) {
                entity.setCreatedAt(Instant.now());
            }

            upserts.add(entity);
        }

        log.info("Updating video details: {}", upserts.size());

        repository.saveAll(upserts);
    }

    @Override
    public void processTop10YouTube(){
        List<String> videoIdsFromChatGpt = chatGptService.getTopYoutubeVideoIds();

        if (videoIdsFromChatGpt == null || videoIdsFromChatGpt.isEmpty()) {
           log.warn("Cannot get data from ChatGPT");
        }

        List<YouTubeVideoDTO> dtos = youTubeApiClient.fetchVideoDetails(videoIdsFromChatGpt);
        log.info("Fetched {} video details from YouTube API", dtos.size());
        updateVideoDetails(dtos);

    }
}