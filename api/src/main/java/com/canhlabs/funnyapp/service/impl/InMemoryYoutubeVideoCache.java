package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.service.YoutubeVideoCache;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryYoutubeVideoCache implements YoutubeVideoCache {

    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    @Override
    public List<String> get(String key) {
        return cache.get(key);
    }

    @Override
    public void put(String key, List<String> videoIds) {
        // cache.put(key, videoIds);
    }

    @Override
    public void put(String key, String videoId) {
        cache.compute(key, (k, v) -> {
            if (v == null) {
                return new ArrayList<>(List.of(videoId));
            }
            if (!v.contains(videoId)) {
                v.add(videoId);
            }
            return v;
        });
    }

    @Override
    public boolean contains(String key) {
        return cache.containsKey(key);
    }

}