package com.canhlabs.funnyapp.aop;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class SlidingWindowRateLimiter {

    private final Cache<String, Deque<Long>> requestCache;

    public SlidingWindowRateLimiter() {
        this.requestCache = CacheBuilder.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build();
    }

    private String buildKey(String clientKey, String apiKey) {
        return clientKey + ":" + apiKey;
    }

    public boolean allowRequest(String clientKey, String apiKey, int limit, long windowMillis) {
        String key = buildKey(clientKey, apiKey);
        long now = Instant.now().toEpochMilli();

        Deque<Long> timestamps = requestCache.getIfPresent(key);
        if (timestamps == null) {
            timestamps = new LinkedList<>();
            requestCache.put(key, timestamps);
        }

        synchronized (timestamps) {
            // remove timestamps outside the window
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
                timestamps.pollFirst();
            }

            if (timestamps.size() < limit) {
                timestamps.addLast(now);
                return true;
            } else {
                return false;
            }
        }
    }
}