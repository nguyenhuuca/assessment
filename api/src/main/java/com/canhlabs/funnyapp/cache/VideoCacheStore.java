package com.canhlabs.funnyapp.cache;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.Callable;

public interface VideoCacheStore {
    byte[] getOrLoadChunk(String fileId, long start, long end, Callable<byte[]> loader);

    void putChunk(String fileId, long start, long end, byte[] data);

    Optional<byte[]> getCachedChunk(String fileId, long start, long end);

    void invalidateChunk(String fileId, long start, long end);

    InputStream getChunkStream(String fileId, long start, long end, Callable<byte[]> loader);

    void invalidateAllChunks();
}
