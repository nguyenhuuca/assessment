package com.canhlabs.funnyapp.cache;

import com.canhlabs.funnyapp.dto.Range;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class CacheBean {

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
        return factory.createCache(1440, 2000);
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
}

