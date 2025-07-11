package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.LockManager;
import com.canhlabs.funnyapp.dto.Range;
import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.cache.ChunkIndexCache;
import com.canhlabs.funnyapp.share.AppConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoStorageServiceImplTest {
    @Mock
    private LockManager lockManager;
    @Mock
    private StatsCache statsCache;
    @Mock
    private ChunkIndexCache chunkIndexCache;

    @InjectMocks
    private VideoStorageServiceImpl videoCacheService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        videoCacheService = new VideoStorageServiceImpl();
        videoCacheService.injectChunkLockManager(lockManager);
        videoCacheService.injectCacheStatsService(statsCache);
        videoCacheService.injectChunkIndexService(chunkIndexCache);
    }

    @Test
    void testHasChunk_Exists() {
        String fileId = "test";
        long start = 0, end = 10;
        File chunk = new File(AppConstant.CACHE_DIR + fileId + "/" + start + "-" + end + ".cache");
        chunk.getParentFile().mkdirs();
        try {
            chunk.createNewFile();
            boolean result = videoCacheService.hasChunk(fileId, start, end);
            assertTrue(result);
            verify(statsCache).recordHit(fileId);
        } catch (IOException e) {
            fail(e);
        } finally {
            chunk.delete();
            chunk.getParentFile().delete();
        }
    }

    @Test
    void testHasChunk_NotExists() {
        String fileId = "notfound";
        long start = 0, end = 10;
        File chunk = new File(AppConstant.CACHE_DIR + fileId + "/" + start + "-" + end + ".cache");
        if (chunk.exists()) chunk.delete();
        boolean result = videoCacheService.hasChunk(fileId, start, end);
        assertFalse(result);
        verify(statsCache).recordMiss(fileId);
    }

    @Test
    void testGetChunk_FileNotFound() {
        assertThrows(FileNotFoundException.class, () -> {
            videoCacheService.getChunk("nope", 0, 10);
        });
    }

    @Test
    void testSaveChunk_LockFail() throws IOException {
        String fileId = "lockfail";
        long start = 0, end = 10;
        when(lockManager.tryLock(fileId, start, end)).thenReturn(false);
        assertThrows(IOException.class, () -> {
            videoCacheService.saveChunk(fileId, start, end, new ByteArrayInputStream(new byte[5]));
        });
    }

    @Test
    void testSaveChunk_Success() throws IOException {
        String fileId = "saveok";
        long start = 0, end = 4;
        when(lockManager.tryLock(fileId, start, end)).thenReturn(true);
        doNothing().when(lockManager).release(fileId, start, end);
        doNothing().when(chunkIndexCache).addChunk(fileId, start, end);
        byte[] data = {1,2,3,4,5};
        videoCacheService.saveChunk(fileId, start, end, new ByteArrayInputStream(data));
        File chunk = new File(AppConstant.CACHE_DIR + fileId + "/" + start + "-" + end + ".cache");
        assertTrue(chunk.exists());
        chunk.delete();
        chunk.getParentFile().delete();
    }

    @Test
    void testGetFileRangeFromDisk_FileNotFound() {
        assertThrows(FileNotFoundException.class, () -> {
            videoCacheService.getFileRangeFromDisk("no_full", 0, 10);
        });
    }

    @Test
    void testGetFileSizeFromDisk_FileNotFound() {
        assertThrows(FileNotFoundException.class, () -> {
            videoCacheService.getFileSizeFromDisk("no_full");
        });
    }

    @Test
    void testFindNearestChunk_Delegates() {
        String fileId = "f";
        long start = 0, end = 10, tol = 5;
        Range range = new Range(0, 10);
        when(chunkIndexCache.findNearestChunk(fileId, start, end, tol)).thenReturn(Optional.of(range));
        Optional<Range> result = videoCacheService.findNearestChunk(fileId, start, end, tol);
        assertTrue(result.isPresent());
        assertEquals(range, result.get());
    }
}

