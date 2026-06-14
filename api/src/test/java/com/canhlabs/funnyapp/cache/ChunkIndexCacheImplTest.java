package com.canhlabs.funnyapp.cache;

import com.canhlabs.funnyapp.streaming.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkIndexCacheImplTest {
    private ChunkIndexCacheImpl chunkIndexService;

    @BeforeEach
    void setUp() {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.setType("guava");
        AppCacheFactory factory = new AppCacheFactory(cacheProperties);
        chunkIndexService = new ChunkIndexCacheImpl(factory.createDefaultCache());
    }

    @Test
    void testAddAndFindChunk_ExactMatch() {
        String fileId = "file1";
        chunkIndexService.addChunk(fileId, 0, 99);
        Optional<Range> found = chunkIndexService.findNearestChunk(fileId, 0, 99, 0);
        assertTrue(found.isPresent());
        assertEquals(0, found.get().start());
        assertEquals(99, found.get().end());
    }

    @Test
    void testFindNearestChunk_Tolerance() {
        String fileId = "file2";
        chunkIndexService.addChunk(fileId, 100, 199);
        Optional<Range> found = chunkIndexService.findNearestChunk(fileId, 90, 210, 15);
        assertTrue(found.isPresent());
        assertEquals(100, found.get().start());
        assertEquals(199, found.get().end());
    }

    @Test
    void testFindNearestChunk_NoMatch() {
        String fileId = "file3";
        chunkIndexService.addChunk(fileId, 0, 50);
        Optional<Range> found = chunkIndexService.findNearestChunk(fileId, 100, 200, 5);
        assertFalse(found.isPresent());
    }

    @Test
    void testMultipleChunks() {
        String fileId = "file4";
        chunkIndexService.addChunk(fileId, 0, 49);
        chunkIndexService.addChunk(fileId, 50, 99);
        chunkIndexService.addChunk(fileId, 100, 149);
        assertTrue(chunkIndexService.findNearestChunk(fileId, 50, 99, 0).isPresent());
        assertTrue(chunkIndexService.findNearestChunk(fileId, 0, 49, 0).isPresent());
        assertTrue(chunkIndexService.findNearestChunk(fileId, 100, 149, 0).isPresent());
    }

    @Test
    void testNoChunksForFile() {
        assertFalse(chunkIndexService.findNearestChunk("no_such_file", 0, 10, 5).isPresent());
    }

    @Test
    void testAddChunkWithNullFileId() {
        assertThrows(NullPointerException.class, () -> chunkIndexService.addChunk(null, 0, 10));
    }

    @Test
    void testFindNearestChunkWithNullFileId() {
        assertThrows(NullPointerException.class, () -> chunkIndexService.findNearestChunk(null, 0, 10, 5));
    }

    @Test
    void testAddChunkWithNegativeRange() {
        String fileId = "fileX";
        assertThrows(IllegalArgumentException.class, () -> chunkIndexService.addChunk(fileId, 10, 5));
    }

    @Test
    void testFindNearestChunkWithNegativeTolerance() {
        String fileId = "fileY";
        chunkIndexService.addChunk(fileId, 0, 10);
        assertThrows(IllegalArgumentException.class, () -> chunkIndexService.findNearestChunk(fileId, 0, 10, -1));
    }

    // --- hasChunk tests ---

    @Test
    void testHasChunk_ReturnsTrue_WhenChunkContainsRange() {
        String fileId = "hasChunk1";
        chunkIndexService.addChunk(fileId, 0, 100);
        assertTrue(chunkIndexService.hasChunk(fileId, 0, 100));
    }

    @Test
    void testHasChunk_ReturnsTrue_WhenRangeIsSubset() {
        String fileId = "hasChunk2";
        chunkIndexService.addChunk(fileId, 0, 200);
        assertTrue(chunkIndexService.hasChunk(fileId, 50, 150));
    }

    @Test
    void testHasChunk_ReturnsFalse_WhenNoChunksForFile() {
        assertFalse(chunkIndexService.hasChunk("no_file", 0, 10));
    }

    @Test
    void testHasChunk_ReturnsFalse_WhenChunkDoesNotCoverRange() {
        String fileId = "hasChunk3";
        chunkIndexService.addChunk(fileId, 0, 50);
        assertFalse(chunkIndexService.hasChunk(fileId, 60, 100));
    }

    @Test
    void testHasChunk_ThrowsNullPointerException_WhenFileIdIsNull() {
        assertThrows(NullPointerException.class, () -> chunkIndexService.hasChunk(null, 0, 10));
    }

    @Test
    void testHasChunk_ReturnsFalse_WhenStartNotCovered() {
        String fileId = "hasChunk4";
        // Range [50, 100] — query for [40, 80]: start=40 < r.start=50, so not covered
        chunkIndexService.addChunk(fileId, 50, 100);
        assertFalse(chunkIndexService.hasChunk(fileId, 40, 80));
    }

    @Test
    void testHasChunk_ReturnsFalse_WhenEndNotCovered() {
        String fileId = "hasChunk5";
        // Range [0, 50] — query for [0, 60]: end=60 > r.end=50, so not covered
        chunkIndexService.addChunk(fileId, 0, 50);
        assertFalse(chunkIndexService.hasChunk(fileId, 0, 60));
    }

    // --- addChunk boundary: start == end ---

    @Test
    void testAddChunk_StartEqualsEnd_IsAccepted() {
        String fileId = "boundary1";
        chunkIndexService.addChunk(fileId, 42, 42);
        assertTrue(chunkIndexService.hasChunk(fileId, 42, 42));
    }

    // --- clear (invalidate) tests ---

    @Test
    void testClear_RemovesChunksForFile() {
        String fileId = "clearFile";
        chunkIndexService.addChunk(fileId, 0, 100);
        assertTrue(chunkIndexService.hasChunk(fileId, 0, 100));
        chunkIndexService.clear(fileId);
        assertFalse(chunkIndexService.hasChunk(fileId, 0, 100));
    }

    @Test
    void testClear_DoesNotAffectOtherFiles() {
        String fileId1 = "clearOther1";
        String fileId2 = "clearOther2";
        chunkIndexService.addChunk(fileId1, 0, 50);
        chunkIndexService.addChunk(fileId2, 0, 50);
        chunkIndexService.clear(fileId1);
        assertFalse(chunkIndexService.findNearestChunk(fileId1, 0, 50, 0).isPresent());
        assertTrue(chunkIndexService.findNearestChunk(fileId2, 0, 50, 0).isPresent());
    }

    // --- preload tests ---

    @Test
    void testPreload_LoadsAllRanges() {
        String fileId = "preloadFile1";
        Set<Range> ranges = new HashSet<>();
        ranges.add(new Range(0, 100));
        ranges.add(new Range(200, 300));
        chunkIndexService.preload(fileId, ranges);
        assertTrue(chunkIndexService.hasChunk(fileId, 0, 100));
        assertTrue(chunkIndexService.hasChunk(fileId, 200, 300));
    }

    @Test
    void testPreload_OverwritesPreviousChunks() {
        String fileId = "preloadFile2";
        chunkIndexService.addChunk(fileId, 0, 50);
        Set<Range> newRanges = new HashSet<>();
        newRanges.add(new Range(100, 200));
        chunkIndexService.preload(fileId, newRanges);
        // After preload the original chunk data is replaced
        Optional<Range> found = chunkIndexService.findNearestChunk(fileId, 100, 200, 0);
        assertTrue(found.isPresent());
        assertEquals(100, found.get().start());
    }

    @Test
    void testPreload_EmptySetResultsInNoChunks() {
        String fileId = "preloadEmpty";
        chunkIndexService.preload(fileId, new HashSet<>());
        assertFalse(chunkIndexService.hasChunk(fileId, 0, 10));
    }
}
