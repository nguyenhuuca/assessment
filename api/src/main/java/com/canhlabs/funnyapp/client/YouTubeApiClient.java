package com.canhlabs.funnyapp.client;


import java.util.List;

public interface YouTubeApiClient {
    List<YouTubeVideoDTO> fetchVideoDetails(List<String> videoIds);
}