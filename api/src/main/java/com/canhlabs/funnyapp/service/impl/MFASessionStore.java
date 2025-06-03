package com.canhlabs.funnyapp.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class MFASessionStore {

    // key: sessionToken, value: userId
    private final Cache<String, String> sessionCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)   // TTL: 5m
            .maximumSize(10000)                      // optional: limi ammount
            .build();

    public void storeSession(String sessionToken, String userId) {
        sessionCache.put(sessionToken, userId);
    }

    public Optional<String> getUserId(String sessionToken) {
        return Optional.ofNullable(sessionCache.getIfPresent(sessionToken));
    }

    public void remove(String sessionToken) {
        sessionCache.invalidate(sessionToken);
    }
}