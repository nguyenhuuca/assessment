package com.canhlabs.funnyapp.enums;

import lombok.Getter;

@Getter
public enum CacheKey {
    YOUTUBE_TOP_10("top-youtube-videos");

    private final String keyVal;

    CacheKey(String key) {
        this.keyVal = key;
    }
}
