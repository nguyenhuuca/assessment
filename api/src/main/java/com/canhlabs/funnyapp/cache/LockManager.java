package com.canhlabs.funnyapp.cache;

public interface LockManager {

    /**
     * Attempts to acquire a lock for the specified file and range.
     *
     * @param fileId The ID of the file to lock.
     * @param start  The start position of the range to lock.
     * @param end    The end position of the range to lock.
     * @return true if the lock was acquired, false otherwise.
     */
    boolean tryLock(String fileId, long start, long end);

    /**
     * Releases the lock for the specified file and range.
     *
     * @param fileId The ID of the file to unlock.
     * @param start  The start position of the range to unlock.
     * @param end    The end position of the range to unlock.
     */
    void release(String fileId, long start, long end);
}
