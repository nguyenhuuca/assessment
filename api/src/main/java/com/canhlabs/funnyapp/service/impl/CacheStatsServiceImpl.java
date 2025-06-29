package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.AppCache;
import com.canhlabs.funnyapp.cache.AppCacheFactory;
import com.canhlabs.funnyapp.dto.CacheStat;
import com.canhlabs.funnyapp.service.CacheStatsService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class CacheStatsServiceImpl implements CacheStatsService {

    private final AppCache<String, FileCacheStats> statsCache;

    public CacheStatsServiceImpl(AppCacheFactory appCacheFactory) {
        this.statsCache = appCacheFactory.createCache(1440, 10_000);
    }

    public void recordHit(String fileId) {
        statsCache.get(fileId, FileCacheStats::new).hitCount.incrementAndGet();
    }

    public void recordMiss(String fileId) {
        statsCache.get(fileId, FileCacheStats::new).missCount.incrementAndGet();
    }

    @Override
    public long getTotalHits() {
        long total = 0;
        for (FileCacheStats stats : statsCache.asMap().values()) {
            total += stats.getHitCount().get();
        }
        return total;
    }

    @Override
    public long getTotalMisses() {
        long total = 0;
        for (FileCacheStats stats : statsCache.asMap().values()) {
            total += stats.getMissCount().get();
        }
        return total;
    }

    @Override
    public Map<String, CacheStat> getFileStats() {
        Map<String, CacheStat> result = new HashMap<>();
        statsCache.asMap().forEach((fileId, stats) -> result.put(fileId, new CacheStat(
                stats.getHitCount().get(),
                stats.getMissCount().get()
        )));
        return result;
    }


    @Override
    public long calculateRatio() {
        long total = this.getTotalHits() + this.getTotalMisses();
        return (total == 0) ? 0 : Math.round(100.0 * this.getTotalHits() / total);
    }

    @Getter
    public static class FileCacheStats {
        private final AtomicLong hitCount = new AtomicLong();
        private final AtomicLong missCount = new AtomicLong();
    }
}