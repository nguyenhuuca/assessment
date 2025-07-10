package com.canhlabs.funnyapp.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
 public class AppCacheFactory  {

    public static final String GUAVA = "guava";
    private CacheProperties cacheProperties;
    public AppCacheFactory() {
    }
    @Autowired
    public AppCacheFactory(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    public <K, V> AppCache<K, V> createCache(long ttlMinutes, long maxSize) {
        String type = cacheProperties.getType();
        if (GUAVA.equalsIgnoreCase(type)) {
            return new GuavaAppCache<>(ttlMinutes, maxSize);
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    // Default
    public <K, V> AppCache<K, V> createDefaultCache() {
        String type = cacheProperties.getType();
        if (GUAVA.equalsIgnoreCase(type)) {
            return new GuavaAppCache<>(5, 1000);
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    // By RAM limit (ví dụ 150MB)
    public <K, V> AppCache<K, V> createCacheByWeight(long ttlMinutes, long maxBytes) {
        String type = cacheProperties.getType();
        if (GUAVA.equalsIgnoreCase(type)) {
            return new GuavaAppCache<>(ttlMinutes, maxBytes, true);
        }
        throw new IllegalArgumentException("Unsupported type: " + type);

    }

}
