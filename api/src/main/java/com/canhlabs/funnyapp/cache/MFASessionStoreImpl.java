package com.canhlabs.funnyapp.cache;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MFASessionStoreImpl implements MFASessionStore {

    // key: sessionToken, value: userId
    private final AppCache<String, String> sessionCache;

    public MFASessionStoreImpl(@Qualifier("mfaSessionCache") AppCache<String, String> sessionCache) {
        this.sessionCache = sessionCache;
    }

    @Override
    public void storeSession(String sessionToken, String userId) {
        sessionCache.put(sessionToken, userId);
    }

    @Override
    public Optional<String> getUserId(String sessionToken) {
        return sessionCache.get(sessionToken);
    }

    @Override
    public void remove(String sessionToken) {
        sessionCache.invalidate(sessionToken);
    }
}