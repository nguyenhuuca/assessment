package com.canhlabs.funnyapp.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class VideoCacheImpl implements VideoCache {

    private final AppCache<String, byte[]> chunkCache;
    private static final int CACHE_THRESHOLD = 5;
    private final ConcurrentMap<String, AtomicInteger> accessCounter = new ConcurrentHashMap<>();

    public VideoCacheImpl(@Qualifier("videoCache") AppCache<String, byte[]> chunkCache) {
        this.chunkCache = chunkCache;
    }

    private String makeKey(String fileId, long start, long end) {
        return fileId + "_" + start + "_" + end;
    }

    @Override
    public byte[] getOrLoadChunk(String fileId, long start, long end, Callable<byte[]> loader) {
        String key = makeKey(fileId, start, end);
        return chunkCache.get(key, loader);
    }

    @Override
    public void putChunk(String fileId, long start, long end, byte[] data) {
        String key = makeKey(fileId, start, end);
        chunkCache.put(key, data);
    }

    @Override
    public Optional<byte[]> getCachedChunk(String fileId, long start, long end) {
        String key = makeKey(fileId, start, end);
        return chunkCache.get(key);
    }

    @Override
    public void invalidateChunk(String fileId, long start, long end) {
        chunkCache.invalidate(makeKey(fileId, start, end));
    }

    @Override
    public InputStream getChunkStream(String fileId, long start, long end, Callable<byte[]> loader) {
        String key = makeKey(fileId, start, end);

        // in case the chunk is already cached
        Optional<byte[]> cached = chunkCache.get(key);
        if (cached.isPresent()) {
            log.info("✅ [CACHE HIT] Chunk {} ({} - {})", fileId, start, end);
            return new ByteArrayInputStream(cached.get());
        }

        // Count hits for this chunk
        int hits = accessCounter
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();

        try {
            byte[] data = loader.call();

            if (start == 0 && hits >= CACHE_THRESHOLD) {
                chunkCache.put(key, data);
                accessCounter.remove(key);
                log.info("🔥 [CACHED] Chunk {} ({} - {}) after {} hits", fileId, start, end, hits);
            } else {
                log.warn("⏳ [MISS] Chunk {} ({} - {}) hit count = {}", fileId, start, end, hits);
            }

            return new ByteArrayInputStream(data);

        } catch (Exception e) {
            log.error("❌ Failed to load chunk {} ({} - {})", fileId, start, end, e);
            throw new RuntimeException("Failed to load chunk for: " + key, e);
        }
    }

    @Override
    public void invalidateAllChunks() {
        chunkCache.invalidateAll();
        accessCounter.clear();
        log.info("🔄 Cache & counter cleared");
    }
}
