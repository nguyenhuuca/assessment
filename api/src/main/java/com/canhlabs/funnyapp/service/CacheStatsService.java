package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.CacheStat;

import java.util.Map;

public interface CacheStatsService {
    void recordHit(String fileId);
    void recordMiss(String fileId);
    long getTotalHits();
    long getTotalMisses();
    Map<String, CacheStat> getFileStats(); // fileId → CacheStat

    long calculateRatio();
}
