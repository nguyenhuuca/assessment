package com.canhlabs.funnyapp.dto;

import lombok.Data;

@Data
public class YouTubeVideoDTO {
    private String videoId;
    private String title;
    private String description;
    private String embedLink;
    private String urlLink;
    private Long upCount;
    private Long downCount; // YouTube không còn cung cấp dislike, nên set = 0
}