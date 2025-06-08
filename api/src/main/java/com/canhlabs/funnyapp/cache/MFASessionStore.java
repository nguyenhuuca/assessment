package com.canhlabs.funnyapp.cache;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MFASessionStore {

    // key: sessionToken, value: userId
    private final AppCache<String, String> sessionCache;

    public MFASessionStore(AppCacheFactory factory) {
        this.sessionCache = factory.createCache(5, 10000); // TTL: 5m
    }

    public void storeSession(String sessionToken, String userId) {
        sessionCache.put(sessionToken, userId);
    }

    public Optional<String> getUserId(String sessionToken) {
        return sessionCache.get(sessionToken);
    }

    public void remove(String sessionToken) {
        sessionCache.invalidate(sessionToken);
    }
}