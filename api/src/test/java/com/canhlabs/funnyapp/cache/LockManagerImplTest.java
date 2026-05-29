package com.canhlabs.funnyapp.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LockManagerImpl using a real GuavaAppCache backend.
 *
 * LockManagerImpl.tryLock delegates to lockCache.asMap().putIfAbsent(), which
 * requires a real ConcurrentMap (not a mock). Using GuavaAppCache directly
 * avoids brittle Mockito setup and mirrors the project's existing test style.
 */
class LockManagerImplTest {

    private LockManagerImpl lockManager;

    @BeforeEach
    void setUp() {
        CacheProperties props = new CacheProperties();
        props.setType("guava");
        AppCacheFactory factory = new AppCacheFactory(props);
        AppCache<String, Boolean> backingCache = factory.createDefaultCache();
        lockManager = new LockManagerImpl(backingCache);
    }

    // -----------------------------------------------------------------------
    // tryLock — success cases
    // -----------------------------------------------------------------------

    @Test
    void tryLock_succeedsWhenNoLocksExist() {
        assertTrue(lockManager.tryLock("file1", 0, 100));
    }

    @Test
    void tryLock_succeedsForDifferentFileIdEvenIfRangeOverlaps() {
        lockManager.tryLock("fileA", 0, 500);
        // fileB has no locks; range overlap with fileA is irrelevant
        assertTrue(lockManager.tryLock("fileB", 0, 500));
    }

    @Test
    void tryLock_succeedsForNonOverlappingRangeOnSameFile() {
        // Lock [0, 100], then try [101, 200] — adjacent but not overlapping
        lockManager.tryLock("file1", 0, 100);
        assertTrue(lockManager.tryLock("file1", 101, 200));
    }

    @Test
    void tryLock_succeedsForRangeCompletelyAfterExistingLock() {
        lockManager.tryLock("file1", 0, 49);
        assertTrue(lockManager.tryLock("file1", 50, 99));
    }

    @Test
    void tryLock_succeedsForRangeCompletelyBeforeExistingLock() {
        lockManager.tryLock("file1", 200, 300);
        assertTrue(lockManager.tryLock("file1", 0, 100));
    }

    @Test
    void tryLock_succeedsAfterReleasingExistingLock() {
        lockManager.tryLock("file1", 0, 100);
        lockManager.release("file1", 0, 100);
        assertTrue(lockManager.tryLock("file1", 0, 100));
    }

    // -----------------------------------------------------------------------
    // tryLock — failure (overlap) cases
    // -----------------------------------------------------------------------

    @Test
    void tryLock_failsWhenExactSameRangeAlreadyLocked() {
        lockManager.tryLock("file1", 0, 100);
        assertFalse(lockManager.tryLock("file1", 0, 100));
    }

    @Test
    void tryLock_failsWhenNewRangeContainsExistingLock() {
        lockManager.tryLock("file1", 50, 150);
        // [0, 200] contains [50, 150] → overlap
        assertFalse(lockManager.tryLock("file1", 0, 200));
    }

    @Test
    void tryLock_failsWhenNewRangeIsContainedWithinExistingLock() {
        lockManager.tryLock("file1", 0, 200);
        // [50, 100] is inside [0, 200] → overlap
        assertFalse(lockManager.tryLock("file1", 50, 100));
    }

    @Test
    void tryLock_failsWhenNewRangeOverlapsStartOfExistingLock() {
        lockManager.tryLock("file1", 100, 200);
        // [50, 150] overlaps the start of [100, 200]
        assertFalse(lockManager.tryLock("file1", 50, 150));
    }

    @Test
    void tryLock_failsWhenNewRangeOverlapsEndOfExistingLock() {
        lockManager.tryLock("file1", 0, 100);
        // [50, 200] overlaps the end of [0, 100]
        assertFalse(lockManager.tryLock("file1", 50, 200));
    }

    @Test
    void tryLock_failsWhenRangeTouchesExistingLockBoundaryInclusive() {
        // overlaps() uses aStart <= bEnd && bStart <= aEnd, so touching endpoints overlap
        lockManager.tryLock("file1", 0, 100);
        assertFalse(lockManager.tryLock("file1", 100, 200));
    }

    // -----------------------------------------------------------------------
    // tryLock — multiple locks, partial overlap
    // -----------------------------------------------------------------------

    @Test
    void tryLock_failsWhenOverlappingAnyOfMultipleExistingLocks() {
        lockManager.tryLock("file1", 0, 99);
        lockManager.tryLock("file1", 200, 299);
        // [150, 250] overlaps second lock [200, 299]
        assertFalse(lockManager.tryLock("file1", 150, 250));
    }

    @Test
    void tryLock_succeedsInGapBetweenTwoExistingLocks() {
        lockManager.tryLock("file1", 0, 99);
        lockManager.tryLock("file1", 200, 299);
        // [100, 199] fits between existing locks without touching
        assertTrue(lockManager.tryLock("file1", 100, 199));
    }

    // -----------------------------------------------------------------------
    // tryLock — different fileId isolation
    // -----------------------------------------------------------------------

    @Test
    void tryLock_locksForDifferentFilesAreIndependent() {
        assertTrue(lockManager.tryLock("fileA", 0, 100));
        assertTrue(lockManager.tryLock("fileB", 0, 100));
        assertTrue(lockManager.tryLock("fileC", 0, 100));
    }

    @Test
    void tryLock_overlapCheckIgnoresLocksForOtherFiles() {
        lockManager.tryLock("fileA", 0, 1000);
        lockManager.tryLock("fileB", 0, 1000);
        // fileC has no locks; should succeed regardless of other files
        assertTrue(lockManager.tryLock("fileC", 0, 1000));
    }

    // -----------------------------------------------------------------------
    // release
    // -----------------------------------------------------------------------

    @Test
    void release_allowsRelockAfterRelease() {
        lockManager.tryLock("file1", 0, 100);
        lockManager.release("file1", 0, 100);
        assertTrue(lockManager.tryLock("file1", 0, 100));
    }

    @Test
    void release_onlyReleasesSpecificRange() {
        lockManager.tryLock("file1", 0, 100);
        lockManager.tryLock("file1", 200, 300);

        lockManager.release("file1", 0, 100);

        // Released range can be re-acquired
        assertTrue(lockManager.tryLock("file1", 0, 100));
        // Still-locked range cannot be re-acquired
        assertFalse(lockManager.tryLock("file1", 200, 300));
    }

    @Test
    void release_isIdempotentForNonExistentKey() {
        // Should not throw even if key was never locked
        assertDoesNotThrow(() -> lockManager.release("file1", 999, 9999));
    }

    @Test
    void release_doesNotAffectLocksForOtherFiles() {
        lockManager.tryLock("fileA", 0, 100);
        lockManager.tryLock("fileB", 0, 100);

        lockManager.release("fileA", 0, 100);

        // fileB lock is still held
        assertFalse(lockManager.tryLock("fileB", 0, 100));
    }
}
