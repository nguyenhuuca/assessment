package com.canhlabs.funnyapp.aop;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class SlidingWindowRateLimiter {

    static final long MAX_KEYS = 50_000;

    /** Request timestamps of one key plus the lock guarding them (no separate lock map to prune). */
    private static final class Window {
        final ReentrantLock lock = new ReentrantLock();
        final Deque<Long> timestamps = new ArrayDeque<>();
    }

    private final Cache<String, Window> requestCache;

    public SlidingWindowRateLimiter() {
        // The 1 h idle expiry must stay >= the largest windowInSeconds of any @RateLimited
        // (today 3600 s in AdminReportController), or a window could be forgotten mid-way.
        this.requestCache = CacheBuilder.newBuilder()
                .maximumSize(MAX_KEYS)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build();
    }

    long cacheSize() {
        return requestCache.size();
    }

    /**
     * Builds a unique key for the combination of clientKey and apiKey.
     *
     * @param clientKey The client identifier.
     * @param apiKey    The API identifier.
     * @return A unique key string.
     */
    private String buildKey(String clientKey, String apiKey) {
        return clientKey + ":" + apiKey;
    }

    /**
     * Checks if a request is allowed based on the sliding window rate limiting algorithm.
     *
     * @param clientKey   The client identifier.
     * @param apiKey      The API identifier.
     * @param limit       The maximum number of requests allowed in the time window.
     * @param windowMillis The time window in milliseconds.
     * @return true if the request is allowed, false otherwise.
     */
    public boolean allowRequest(String clientKey, String apiKey, int limit, long windowMillis) {
        String key = buildKey(clientKey, apiKey);
        long now = Instant.now().toEpochMilli();

        Window window;
        try {
            window = requestCache.get(key, Window::new);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Rate limiter cache failure", e);
        }

        window.lock.lock();
        try {
            Deque<Long> timestamps = window.timestamps;
            // remove timestamps outside the window
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
                timestamps.pollFirst();
            }

            // check if we can add a new timestamp
            if (timestamps.size() < limit) {
                timestamps.addLast(now);
                return true;
            } else {
                return false;
            }
        } finally {
            window.lock.unlock();
        }
    }
}
