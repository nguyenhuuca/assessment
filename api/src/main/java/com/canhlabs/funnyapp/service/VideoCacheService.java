package com.canhlabs.funnyapp.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface VideoCacheService {
    File getCacheFile(String fileId);

    boolean hasCache(String fileId, long requiredBytes);

    InputStream getCache(String fileId) throws IOException;

    InputStream getCache(String fileId, long start, long end) throws IOException;

    void saveToCache(String fileId, InputStream inputStream) throws IOException;

    // Chunk functionality
    boolean hasChunk(String fileId, long start, long end);
    InputStream getChunk(String fileId, long start, long end) throws IOException;
    void saveChunk(String fileId, long start, long end, InputStream stream) throws IOException;
}
