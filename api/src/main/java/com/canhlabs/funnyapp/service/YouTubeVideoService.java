package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.VideoDto;

import java.util.List;

public interface YouTubeVideoService {
    /**
     * Get list of video IDs by cacheKey with flow: cache -> DB -> ChatGPT
     */
    List<VideoDto> getVideoIds();

    void processTop10YouTube();
}
