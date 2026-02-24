package com.canhlabs.funnyapp.aop;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class SlidingWindowRateLimiter {

    private final Cache<String, Deque<Long>> requestCache;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter() {
        this.requestCache = CacheBuilder.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build();
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

        Deque<Long> timestamps = requestCache.getIfPresent(key);
        if (timestamps == null) {
            timestamps = new LinkedList<>();
            requestCache.put(key, timestamps);
        }

        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
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
            lock.unlock();
        }
    }
}