package com.canhlabs.funnyapp.cache;

import com.canhlabs.funnyapp.dto.video.VideoDto;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Short-lived cache of the public feed list, keyed by the HLS flag value (the DTO's {@code hlsUrl} depends on it,
 * so a flag flip needs no eviction). Every write that changes the rows, their order or a DTO field calls
 * {@link #invalidate()}; view counts are not evicted and may be up to {@link #TTL_SECONDS} stale.
 */
@Component
public class FeedCache {

    static final long TTL_SECONDS = 30;
    private static final int MAX_SIZE = 2;

    private final Cache<Boolean, List<VideoDto>> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(MAX_SIZE)
            .build();

    /**
     * @return the cached feed for the flag value; concurrent misses load once. A throwing loader is not cached.
     */
    public List<VideoDto> get(boolean hlsEnabled, Supplier<List<VideoDto>> loader) {
        try {
            return cache.get(hlsEnabled, () -> List.copyOf(loader.get()));
        } catch (UncheckedExecutionException | ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    /** Drops every entry, after commit when a transaction is active. */
    public void invalidate() {
        AfterCommit.run(cache::invalidateAll);
    }
}
