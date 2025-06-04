package com.canhlabs.funnyapp.service;

import java.util.List;

public interface YoutubeVideoCache {
    List<String> get(String key);
    void put(String key, List<String> videoIds);
    void put(String key, String videoId);
    boolean contains(String key);
}