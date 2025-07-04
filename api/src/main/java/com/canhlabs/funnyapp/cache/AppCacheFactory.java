package com.canhlabs.funnyapp.cache;

import org.springframework.stereotype.Component;

@Component
 public class AppCacheFactory {

    public <K, V> AppCache<K, V> createCache(long ttlMinutes, long maxSize) {
        return new GuavaAppCache<>(ttlMinutes, maxSize);
    }

    // Default
    public <K, V> AppCache<K, V> createDefaultCache() {
        return new GuavaAppCache<>(5, 1000);
    }

    // Dùng theo RAM (ví dụ 150MB)
    public <K, V> AppCache<K, V> createCacheByWeight(long ttlMinutes, long maxBytes) {
        return new GuavaAppCache<>(ttlMinutes, maxBytes, true);
    }


}
