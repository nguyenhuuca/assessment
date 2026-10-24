package com.canhlabs.funnyapp.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

@Component
@Slf4j
public class VideoCacheImpl implements VideoCache {

    private final AppCache<String, byte[]> chunkCache;
    private static final int CACHE_THRESHOLD = 5;
    static final long COUNTER_MAX_SIZE = 10_000;
    // keys are client-chosen (fileId_start_end), so the counter must be bounded
    private final Cache<String, AtomicInteger> accessCounter = CacheBuilder.newBuilder()
            .maximumSize(COUNTER_MAX_SIZE)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();
    private final StatsCache statsCache;

    public VideoCacheImpl(@Qualifier("videoCache") AppCache<String, byte[]> chunkCache, StatsCache statsCache) {
        this.chunkCache = chunkCache;
        this.statsCache = statsCache;
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
    public InputStream getChunkStream(String fileId, long start, long end, Callable<InputStream> loader) {
        String key = makeKey(fileId, start, end);

        // in case the chunk is already cached
        Optional<byte[]> cached = chunkCache.get(key);
        if (cached.isPresent()) {
            log.info("✅ [CACHE HIT] Chunk {} ({} - {})", fileId, start, end);
            statsCache.recordHit(fileId);
            return new ByteArrayInputStream(cached.get());
        }

        // Count hits for this chunk
        int hits = accessCounter.asMap()
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();

        try {
            InputStream disk = loader.call();

            if (start == 0 && hits >= CACHE_THRESHOLD) {
                byte[] data;
                try (disk) {
                    data = disk.readAllBytes();
                }
                chunkCache.put(key, data);
                accessCounter.invalidate(key);
                log.info("[CACHED] Chunk {} ({} - {}) after {} hits", fileId, start, end, hits);
                return new ByteArrayInputStream(data);
            }

            log.warn("[MISS] Chunk {} ({} - {}) hit count = {}", fileId, start, end, hits);
            return disk;

        } catch (Exception e) {
            log.error("❌ Failed to load chunk {} ({} - {})", fileId, start, end, e);
            throw new RuntimeException("Failed to load chunk for: " + key, e);
        }
    }

    /** Number of chunk keys currently counted (bounded by {@link #COUNTER_MAX_SIZE}). */
    long accessCounterSize() {
        return accessCounter.size();
    }

    @Override
    public void invalidateFile(String fileId) {
        // keys are fileId_start_end; Drive ids may contain '_', so also check the numeric tail
        String prefix = fileId + "_";
        Predicate<String> ofFile =
                k -> k.startsWith(prefix) && k.substring(prefix.length()).matches("\\d+_\\d+");
        chunkCache.asMap().keySet().removeIf(ofFile);
        accessCounter.asMap().keySet().removeIf(ofFile);
    }

    @Override
    public void invalidateAllChunks() {
        chunkCache.invalidateAll();
        accessCounter.invalidateAll();
        log.info("Cache & counter cleared");
    }
}
