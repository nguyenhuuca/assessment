package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.AppCache;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.VideoViewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VideoViewServiceImpl implements VideoViewService {

    private final VideoSourceRepository videoSourceRepository;
    private final AppCache<String, Boolean> dedupCache;

    public VideoViewServiceImpl(VideoSourceRepository videoSourceRepository,
                                @Qualifier("videoViewDedupCache") AppCache<String, Boolean> dedupCache) {
        this.videoSourceRepository = videoSourceRepository;
        this.dedupCache = dedupCache;
    }

    @Override
    public boolean recordView(Long videoId, String viewerKey) {
        String key = viewerKey + "|" + videoId;
        // putIfAbsent is atomic: two concurrent requests from the same viewer count once
        if (dedupCache.asMap().putIfAbsent(key, Boolean.TRUE) != null) {
            return false;
        }
        boolean counted = videoSourceRepository.incrementViewCount(videoId) > 0;
        if (!counted) {
            // unknown or hidden video: do not keep the key, nothing was counted
            dedupCache.invalidate(key);
        }
        return counted;
    }
}
