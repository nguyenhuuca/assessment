package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.AppCacheFactory;
import com.canhlabs.funnyapp.cache.CacheProperties;
import com.canhlabs.funnyapp.dto.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class InMemoryChunkIndexServiceImplTest {
    private InMemoryChunkIndexServiceImpl chunkIndexService;

    @BeforeEach
    void setUp() {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.setType("guava");
        AppCacheFactory factory = new AppCacheFactory(cacheProperties);
        chunkIndexService = new InMemoryChunkIndexServiceImpl(factory);
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
}
