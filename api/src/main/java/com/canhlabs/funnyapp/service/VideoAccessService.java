package com.canhlabs.funnyapp.service;

import java.time.Duration;
import java.util.List;

public interface VideoAccessService {
    void recordAccess(String videoId);

    List<String> getLeastAccessedVideos(Duration oldThan, int limit);
}
