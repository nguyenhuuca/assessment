package com.canhlabs.funnyapp.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoCacheImplTest {

    @Mock
    private AppCache<String, byte[]> chunkCache;

    private VideoCacheImpl videoCache;

    @BeforeEach
    void setUp() {
        videoCache = new VideoCacheImpl(chunkCache);
    }

    // ── key format ────────────────────────────────────────────────────────────

    @Test
    void putChunk_callsCachePutWithCorrectKey() {
        byte[] data = new byte[]{1, 2, 3};
        videoCache.putChunk("fileA", 0L, 99L, data);
        verify(chunkCache).put("fileA_0_99", data);
    }

    @Test
    void getCachedChunk_returnsOptionalFromCache() {
        byte[] data = new byte[]{4, 5, 6};
        when(chunkCache.get("fileB_100_199")).thenReturn(Optional.of(data));

        Optional<byte[]> result = videoCache.getCachedChunk("fileB", 100L, 199L);

        assertThat(result).isPresent().contains(data);
        verify(chunkCache).get("fileB_100_199");
    }

    @Test
    void getCachedChunk_returnsEmptyWhenNotCached() {
        when(chunkCache.get("fileC_0_50")).thenReturn(Optional.empty());

        Optional<byte[]> result = videoCache.getCachedChunk("fileC", 0L, 50L);

        assertThat(result).isEmpty();
    }

    @Test
    void invalidateChunk_callsCacheInvalidateWithCorrectKey() {
        videoCache.invalidateChunk("fileD", 200L, 399L);
        verify(chunkCache).invalidate("fileD_200_399");
    }

    // ── getOrLoadChunk ────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getOrLoadChunk_delegatesToCacheGetWithLoader() throws Exception {
        byte[] data = new byte[]{7, 8, 9};
        Callable<byte[]> loader = () -> data;
        when(chunkCache.get(eq("fileE_0_100"), any(Callable.class))).thenReturn(data);

        byte[] result = videoCache.getOrLoadChunk("fileE", 0L, 100L, loader);

        assertThat(result).isEqualTo(data);
        verify(chunkCache).get(eq("fileE_0_100"), any(Callable.class));
    }

    // ── invalidateAllChunks ───────────────────────────────────────────────────

    @Test
    void invalidateAllChunks_callsInvalidateAllAndClearsCounter() {
        // Prime the access counter by calling getChunkStream on a miss
        when(chunkCache.get("fileF_0_50")).thenReturn(Optional.empty());
        try {
            videoCache.getChunkStream("fileF", 0L, 50L, () -> new byte[]{1});
        } catch (Exception ignored) {
            // not the focus of this test
        }

        videoCache.invalidateAllChunks();

        verify(chunkCache).invalidateAll();
        // After invalidateAllChunks the counter is cleared; a subsequent miss
        // should start the hit count from 1 again (not continue from before).
        when(chunkCache.get("fileF_0_50")).thenReturn(Optional.empty());
        videoCache.getChunkStream("fileF", 0L, 50L, () -> new byte[]{1});
        // No put expected — hit count is only 1
        verify(chunkCache, never()).put(eq("fileF_0_50"), any());
    }

    // ── getChunkStream – cache HIT ────────────────────────────────────────────

    @Test
    void getChunkStream_cacheHit_returnsByteArrayInputStream() throws Exception {
        byte[] cached = new byte[]{10, 20, 30};
        when(chunkCache.get("fileG_0_100")).thenReturn(Optional.of(cached));

        InputStream result = videoCache.getChunkStream("fileG", 0L, 100L, () -> {
            throw new AssertionError("loader must not be called on cache hit");
        });

        assertThat(result).isInstanceOf(ByteArrayInputStream.class);
        byte[] actual = result.readAllBytes();
        assertThat(actual).isEqualTo(cached);
    }

    // ── getChunkStream – cache MISS ───────────────────────────────────────────

    @Test
    void getChunkStream_cacheMiss_callsLoaderAndReturnsStream() throws Exception {
        byte[] loaded = new byte[]{11, 22, 33};
        when(chunkCache.get("fileH_50_150")).thenReturn(Optional.empty());

        InputStream result = videoCache.getChunkStream("fileH", 50L, 150L, () -> loaded);

        assertThat(result).isInstanceOf(ByteArrayInputStream.class);
        byte[] actualLoaded = result.readAllBytes();
        assertThat(actualLoaded).isEqualTo(loaded);
        // start != 0 so no put even after many calls
        verify(chunkCache, never()).put(any(), any());
    }

    // ── getChunkStream – loader throws ───────────────────────────────────────

    @Test
    void getChunkStream_loaderThrowsException_wrapsInRuntimeException() {
        when(chunkCache.get("fileI_0_100")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                videoCache.getChunkStream("fileI", 0L, 100L, () -> {
                    throw new Exception("disk error");
                })
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fileI_0_100")
                .hasCauseInstanceOf(Exception.class);
    }

    // ── getChunkStream – threshold caching ───────────────────────────────────

    @Test
    void getChunkStream_startZero_cachesAfterFifthHit() throws Exception {
        byte[] data = new byte[]{1, 2, 3};
        // Always return cache miss so all 5 calls go through the loader
        when(chunkCache.get("fileJ_0_500")).thenReturn(Optional.empty());

        Callable<byte[]> loader = () -> data;

        // First 4 hits – must NOT trigger put
        for (int i = 0; i < 4; i++) {
            videoCache.getChunkStream("fileJ", 0L, 500L, loader);
        }
        verify(chunkCache, never()).put(any(), any());

        // 5th hit – must trigger put
        videoCache.getChunkStream("fileJ", 0L, 500L, loader);
        verify(chunkCache, times(1)).put("fileJ_0_500", data);
    }

    @Test
    void getChunkStream_startNotZero_doesNotCacheEvenAfterFifthHit() throws Exception {
        byte[] data = new byte[]{1, 2, 3};
        when(chunkCache.get("fileK_100_600")).thenReturn(Optional.empty());

        Callable<byte[]> loader = () -> data;

        // 5+ hits – must NEVER put because start != 0
        for (int i = 0; i < 6; i++) {
            videoCache.getChunkStream("fileK", 100L, 600L, loader);
        }
        verify(chunkCache, never()).put(any(), any());
    }
}
