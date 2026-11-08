package com.canhlabs.funnyapp.cache;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MFASessionStoreImpl implements MFASessionStore {

    // key: sessionToken, value: userId
    private final AppCache<String, String> sessionCache;
    // key: sessionToken, value: wrong OTPs so far; same TTL as sessionCache and written later, so it never expires first
    private final AppCache<String, Integer> attemptCache;

    public MFASessionStoreImpl(@Qualifier("mfaSessionCache") AppCache<String, String> sessionCache,
                               @Qualifier("mfaAttemptCache") AppCache<String, Integer> attemptCache) {
        this.sessionCache = sessionCache;
        this.attemptCache = attemptCache;
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
    public int recordFailure(String sessionToken) {
        // merge on the cache's ConcurrentMap view is atomic: parallel guesses cannot lose a count
        return attemptCache.asMap().merge(sessionToken, 1, Integer::sum);
    }

    @Override
    public void remove(String sessionToken) {
        sessionCache.invalidate(sessionToken);
        attemptCache.invalidate(sessionToken);
    }
}