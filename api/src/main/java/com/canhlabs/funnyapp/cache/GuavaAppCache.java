package com.canhlabs.funnyapp.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
class GuavaAppCache<K, V> implements AppCache<K, V> {

    private final Cache<K, V> cache;

    public GuavaAppCache(long ttlMinutes, long maxSize) {
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .build();
    }

    // ✅ Constructor theo RAM (maximumWeight, bytes)
    public GuavaAppCache(long ttlMinutes, long maxWeightInBytes, boolean useWeight) {
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumWeight(maxWeightInBytes)
                .weigher((K key, V value) -> {
                    if (value instanceof byte[]) {
                        return ((byte[]) value).length;
                    }
                    return 1; // fallback
                })
                .build();
    }


    public GuavaAppCache(long ttlMinutes, long maxWeightInBytes, long maxSize) {
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumWeight(maxWeightInBytes)
                .weigher((K key, V value) -> {
                    if (value instanceof byte[]) {
                        return ((byte[]) value).length;
                    }
                    return 1; // fallback
                })
                .build();
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public Optional<V> get(K key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    @Override
    public void invalidate(K key) {
        cache.invalidate(key);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    @Override
    public V get(K key, Callable<? extends V> loader) {
        try {
            return cache.get(key, loader);
        } catch (ExecutionException e) {
            log.error("Error getting value from cache for key: {}", key, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<K, V> asMap() {
        return cache.asMap();
    }

}
