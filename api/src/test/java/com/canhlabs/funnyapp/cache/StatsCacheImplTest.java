package com.canhlabs.funnyapp.cache;

import com.canhlabs.funnyapp.cache.CacheStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StatsCacheImpl using a real GuavaAppCache backend,
 * matching the project's existing cache-test style (no Spring context).
 */
class StatsCacheImplTest {

    private StatsCacheImpl statsCache;

    @BeforeEach
    void setUp() {
        CacheProperties props = new CacheProperties();
        props.setType("guava");
        AppCacheFactory factory = new AppCacheFactory(props);
        AppCache<String, StatsCacheImpl.FileCacheStats> backingCache = factory.createDefaultCache();
        statsCache = new StatsCacheImpl(backingCache);
    }

    // -----------------------------------------------------------------------
    // recordHit
    // -----------------------------------------------------------------------

    @Test
    void recordHit_incrementsHitCountForNewFile() {
        statsCache.recordHit("file1");
        assertEquals(1L, statsCache.getTotalHits());
    }

    @Test
    void recordHit_accumulatesMultipleHitsForSameFile() {
        statsCache.recordHit("file1");
        statsCache.recordHit("file1");
        statsCache.recordHit("file1");
        assertEquals(3L, statsCache.getTotalHits());
    }

    @Test
    void recordHit_doesNotIncrementMissCount() {
        statsCache.recordHit("file1");
        assertEquals(0L, statsCache.getTotalMisses());
    }

    // -----------------------------------------------------------------------
    // recordMiss
    // -----------------------------------------------------------------------

    @Test
    void recordMiss_incrementsMissCountForNewFile() {
        statsCache.recordMiss("file1");
        assertEquals(1L, statsCache.getTotalMisses());
    }

    @Test
    void recordMiss_accumulatesMultipleMissesForSameFile() {
        statsCache.recordMiss("file1");
        statsCache.recordMiss("file1");
        assertEquals(2L, statsCache.getTotalMisses());
    }

    @Test
    void recordMiss_doesNotIncrementHitCount() {
        statsCache.recordMiss("file1");
        assertEquals(0L, statsCache.getTotalHits());
    }

    // -----------------------------------------------------------------------
    // getTotalHits — sums across multiple files
    // -----------------------------------------------------------------------

    @Test
    void getTotalHits_sumsHitsAcrossMultipleFiles() {
        statsCache.recordHit("fileA");
        statsCache.recordHit("fileA");
        statsCache.recordHit("fileB");
        assertEquals(3L, statsCache.getTotalHits());
    }

    @Test
    void getTotalHits_returnsZeroWhenNothingRecorded() {
        assertEquals(0L, statsCache.getTotalHits());
    }

    // -----------------------------------------------------------------------
    // getTotalMisses — sums across multiple files
    // -----------------------------------------------------------------------

    @Test
    void getTotalMisses_sumsMissesAcrossMultipleFiles() {
        statsCache.recordMiss("fileA");
        statsCache.recordMiss("fileB");
        statsCache.recordMiss("fileB");
        assertEquals(3L, statsCache.getTotalMisses());
    }

    @Test
    void getTotalMisses_returnsZeroWhenNothingRecorded() {
        assertEquals(0L, statsCache.getTotalMisses());
    }

    // -----------------------------------------------------------------------
    // calculateRatio
    // -----------------------------------------------------------------------

    @Test
    void calculateRatio_returnsZeroWhenNoData() {
        assertEquals(0L, statsCache.calculateRatio());
    }

    @Test
    void calculateRatio_returns100WhenAllHits() {
        statsCache.recordHit("file1");
        statsCache.recordHit("file1");
        assertEquals(100L, statsCache.calculateRatio());
    }

    @Test
    void calculateRatio_returns0WhenAllMisses() {
        statsCache.recordMiss("file1");
        statsCache.recordMiss("file1");
        assertEquals(0L, statsCache.calculateRatio());
    }

    @Test
    void calculateRatio_returns50WhenHalfHitsHalfMisses() {
        statsCache.recordHit("file1");
        statsCache.recordMiss("file1");
        assertEquals(50L, statsCache.calculateRatio());
    }

    @Test
    void calculateRatio_roundsCorrectly() {
        // 1 hit, 2 misses → 33.33% → rounds to 33
        statsCache.recordHit("file1");
        statsCache.recordMiss("file1");
        statsCache.recordMiss("file1");
        assertEquals(33L, statsCache.calculateRatio());
    }

    @Test
    void calculateRatio_roundsUpAtMidpoint() {
        // 2 hits, 1 miss → 66.66% → rounds to 67
        statsCache.recordHit("file1");
        statsCache.recordHit("file1");
        statsCache.recordMiss("file1");
        assertEquals(67L, statsCache.calculateRatio());
    }

    // -----------------------------------------------------------------------
    // getFileStats
    // -----------------------------------------------------------------------

    @Test
    void getFileStats_returnsEmptyMapWhenNothingRecorded() {
        Map<String, CacheStat> stats = statsCache.getFileStats();
        assertTrue(stats.isEmpty());
    }

    @Test
    void getFileStats_returnsCorrectHitsAndMissesPerFile() {
        statsCache.recordHit("fileA");
        statsCache.recordHit("fileA");
        statsCache.recordMiss("fileA");

        statsCache.recordHit("fileB");
        statsCache.recordMiss("fileB");
        statsCache.recordMiss("fileB");

        Map<String, CacheStat> stats = statsCache.getFileStats();

        assertEquals(2, stats.size());

        CacheStat statA = stats.get("fileA");
        assertNotNull(statA);
        assertEquals(2L, statA.getHits());
        assertEquals(1L, statA.getMisses());

        CacheStat statB = stats.get("fileB");
        assertNotNull(statB);
        assertEquals(1L, statB.getHits());
        assertEquals(2L, statB.getMisses());
    }

    @Test
    void getFileStats_tracksOnlyMissesWhenNoHitsRecorded() {
        statsCache.recordMiss("fileX");
        Map<String, CacheStat> stats = statsCache.getFileStats();
        CacheStat stat = stats.get("fileX");
        assertNotNull(stat);
        assertEquals(0L, stat.getHits());
        assertEquals(1L, stat.getMisses());
    }

    @Test
    void getFileStats_tracksOnlyHitsWhenNoMissesRecorded() {
        statsCache.recordHit("fileY");
        Map<String, CacheStat> stats = statsCache.getFileStats();
        CacheStat stat = stats.get("fileY");
        assertNotNull(stat);
        assertEquals(1L, stat.getHits());
        assertEquals(0L, stat.getMisses());
    }

    // -----------------------------------------------------------------------
    // Mixed hits and misses across files
    // -----------------------------------------------------------------------

    @Test
    void mixedRecording_totalHitsAndMissesAreIndependent() {
        statsCache.recordHit("file1");
        statsCache.recordHit("file2");
        statsCache.recordMiss("file1");
        statsCache.recordMiss("file3");

        assertEquals(2L, statsCache.getTotalHits());
        assertEquals(2L, statsCache.getTotalMisses());
    }
}
