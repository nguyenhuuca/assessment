package com.canhlabs.funnyapp.cache;

import org.springframework.stereotype.Component;

@Component
public class ChunkLockManager {

    private final AppCache<String, Boolean> lockCache;

    public ChunkLockManager(AppCacheFactory appCacheFactory) {
        // TTL = 30 giây, max 10_000 keys
        this.lockCache = appCacheFactory.createCache(1, 10_000);
    }

    public boolean tryLock(String fileId, long start, long end) {
        String key = buildKey(fileId, start, end);
        synchronized (this) {
            if (lockCache.get(key).isEmpty()) {
                lockCache.put(key, true);
                return true;
            }
            return false;
        }
    }

    public void release(String fileId, long start, long end) {
        String key = buildKey(fileId, start, end);
        lockCache.invalidate(key);
    }

    private String buildKey(String fileId, long start, long end) {
        return fileId + ":" + start + "-" + end;
    }
}