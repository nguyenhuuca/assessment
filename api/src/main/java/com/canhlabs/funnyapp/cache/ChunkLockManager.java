package com.canhlabs.funnyapp.cache;

public interface ChunkLockManager {
    boolean tryLock(String fileId, long start, long end);

    void release(String fileId, long start, long end);
}
