package com.canhlabs.funnyapp.cache;

import com.canhlabs.funnyapp.streaming.Range;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class CacheBean {

    /** videoCache is bounded by bytes (prod heap is small), not by entry count. */
    static final long VIDEO_CACHE_MAX_BYTES = 32L * 1024 * 1024;

    @Bean(name = "mfaSessionCache")
    public AppCache<String, String> mfaSessionCache(AppCacheFactory factory, CacheProperties props) {
        return factory.createCache( props.getMfa().getTtlMinutes(), props.getMfa().getMaxSize());
    }

    @Bean(name ="lockManagerCache")
    public AppCache<String, Boolean> lockManagerCache(AppCacheFactory factory) {
        return factory.createCache(1, 10_000);
    }

    @Bean(name="videoCache")
    public AppCache<String, byte[]> videoCache(AppCacheFactory factory) {
        return factory.createCacheByWeight(1440, VIDEO_CACHE_MAX_BYTES);
    }

    @Bean(name = "statsCache")
    public AppCache<String, StatsCacheImpl.FileCacheStats> statsCache(AppCacheFactory factory) {
        return factory.createDefaultCache();
    }

    @Bean(name = "chunkIdxCache")
    public  AppCache<String, Set<Range>> chunkIdxCache(AppCacheFactory factory, CacheProperties props) {
        return factory.createDefaultCache();
    }

    @Bean(name = "emailLimiterCache")
    public  AppCache<String, Integer> emailLimiterCache(AppCacheFactory factory, CacheProperties props) {
        return factory.createDefaultCache();
    }

    /** viewer|videoId keys already counted: one view per viewer per video per 30 min. */
    @Bean(name = "videoViewDedupCache")
    public AppCache<String, Boolean> videoViewDedupCache(AppCacheFactory factory) {
        return factory.createCache(30, 100_000);
    }
}
