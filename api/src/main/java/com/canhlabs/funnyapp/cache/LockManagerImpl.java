package com.canhlabs.funnyapp.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LockManagerImpl implements LockManager {

    private final AppCache<String, Boolean> lockCache;

    public LockManagerImpl(@Qualifier("lockManagerCache") AppCache<String, Boolean> lockCache) {
        this.lockCache = lockCache;
        log.info("ChunkLockManager initialized with cache: {}", lockCache.getClass().getSimpleName());

    }

    @Override
    public boolean tryLock(String fileId, long start, long end) {
        String newKey = buildKey(fileId, start, end);

        for (String key : lockCache.asMap().keySet()) {
            if (!key.startsWith(fileId + ":")) continue;

            long[] range = parseRange(key);
            long existingStart = range[0];
            long existingEnd = range[1];

            if (overlaps(start, end, existingStart, existingEnd)) {
                log.warn("⛔ Overlap with existing lock: {}", key);
                return false;
            }
        }

        // No overlap found → acquire lock
        return lockCache.asMap().putIfAbsent(newKey, Boolean.TRUE) == null;
    }

    @Override
    public void release(String fileId, long start, long end) {
        String key = buildKey(fileId, start, end);
        lockCache.invalidate(key);
    }

    private String buildKey(String fileId, long start, long end) {
        return fileId + ":" + start + "-" + end;
    }

    private boolean overlaps(long aStart, long aEnd, long bStart, long bEnd) {
        return aStart <= bEnd && bStart <= aEnd;
    }

    private long[] parseRange(String key) {
        // key: abc123:3932160-4456447
        String[] parts = key.split(":");
        String[] range = parts[1].split("-");
        return new long[]{
                Long.parseLong(range[0]),
                Long.parseLong(range[1])
        };
    }
}