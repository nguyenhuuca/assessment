package com.canhlabs.funnyapp.client;

import com.canhlabs.funnyapp.dto.YouTubeVideoDTO;

import java.util.List;

public interface YouTubeApiClient {
    List<YouTubeVideoDTO> fetchVideoDetails(List<String> videoIds);
}