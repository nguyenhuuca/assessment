package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.Range;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface VideoCacheService {
    File getCacheFile(String fileId);

    InputStream getCache(String fileId, long start, long end) throws IOException;


    // Chunk functionality
    boolean hasChunk(String fileId, long start, long end);
    InputStream getChunk(String fileId, long start, long end) throws IOException;
    void saveChunk(String fileId, long start, long end, InputStream stream) throws IOException;
    Optional<Range> findNearestChunk(String fileId, long requestedStart, long requestedEnd, long tolerance);

    InputStream getFileRangeFromDisk(String fileId, long start, long end) throws IOException;
    long getFileSizeFromDisk(String fileId) throws IOException;

}
