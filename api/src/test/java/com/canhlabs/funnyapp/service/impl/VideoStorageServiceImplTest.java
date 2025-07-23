package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.ChunkIndexCache;
import com.canhlabs.funnyapp.cache.LockManager;
import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.dto.Range;
import com.canhlabs.funnyapp.service.VideoAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileNotFoundException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class VideoStorageServiceImplTest {
    @Mock
    private LockManager lockManager;
    @Mock
    private StatsCache statsCache;
    @Mock
    private ChunkIndexCache chunkIndexCache;

    @Mock
    VideoAccessService videoAccessService;

    @InjectMocks
    private VideoStorageServiceImpl videoStorageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        videoStorageService = new VideoStorageServiceImpl();
        videoStorageService.injectCacheStatsService(statsCache);
        videoStorageService.injectChunkIndexService(chunkIndexCache);
        videoStorageService.injectVideoAccessService(videoAccessService);
    }


    @Test
    void testGetFileRangeFromDisk_FileNotFound() {
        assertThrows(FileNotFoundException.class, () -> {
            videoStorageService.getFileRangeFromDisk("no_full", 0, 10);
        });
    }

    @Test
    void testGetFileSizeFromDisk_FileNotFound() {
        assertThrows(FileNotFoundException.class, () -> {
            videoStorageService.getFileSizeFromDisk("no_full");
        });
    }

    @Test
    void testFindNearestChunk_Delegates() {
        String fileId = "f";
        long start = 0, end = 10, tol = 5;
        Range range = new Range(0, 10);
        when(chunkIndexCache.findNearestChunk(fileId, start, end, tol)).thenReturn(Optional.of(range));
        Optional<Range> result = videoStorageService.findNearestChunk(fileId, start, end, tol);
        assertTrue(result.isPresent());
        assertEquals(range, result.get());
    }
}

