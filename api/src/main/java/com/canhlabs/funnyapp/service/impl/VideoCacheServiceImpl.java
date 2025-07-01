package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.ChunkLockManager;
import com.canhlabs.funnyapp.dto.Range;
import com.canhlabs.funnyapp.service.CacheStatsService;
import com.canhlabs.funnyapp.service.ChunkIndexService;
import com.canhlabs.funnyapp.service.VideoCacheService;
import com.canhlabs.funnyapp.share.AppConstant;
import com.canhlabs.funnyapp.share.LimitedInputStream;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.canhlabs.funnyapp.share.AppConstant.CACHE_DIR;


@Slf4j
@Service
public class VideoCacheServiceImpl implements VideoCacheService {
    private static final int MAX_CACHE_FILES = 6000;
    private static final long MAX_CACHE_SIZE_BYTES = 1024 * 1024 * 1024L; //1G

    private ChunkLockManager chunkLockManager;
    private CacheStatsService cacheStatsService;
    private ChunkIndexService chunkIndexService;

    @Autowired
    public void injectChunkIndexService(ChunkIndexService chunkIndexService) {
        this.chunkIndexService = chunkIndexService;
    }

    @Autowired
    public void injectChunkLockManager(ChunkLockManager chunkLockManager) {
        this.chunkLockManager = chunkLockManager;
    }

    @Autowired
    public void injectCacheStatsService(CacheStatsService cacheStatsService) {
        this.cacheStatsService = cacheStatsService;
    }

    @Override
    public File getCacheFile(String fileId) {
        return new File(CACHE_DIR + fileId + ".cache");
    }

    @WithSpan
    @Override
    public InputStream getCache(String fileId, long start, long end) throws IOException {
        File file = new File(CACHE_DIR + fileId + ".cache");

        if (!file.exists()) {
            throw new FileNotFoundException("Cache not found for file: " + fileId);
        }

        long length = end - start + 1;
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek(start);

        return new LimitedInputStream(new FileInputStream(raf.getFD()), length, raf) {
        };
    }



    @Override
    @WithSpan
    public boolean hasChunk(String fileId, long start, long end) {
        File chunk = getChunkFile(fileId, start, end);
        boolean exists = chunk.exists();// && chunk.length() == (end - start + 1);
        log.info("Check chunk exists: {} [{}-{}] = {}", fileId, start, end, exists);
        if (exists) {
            cacheStatsService.recordHit(fileId);
        } else {
            cacheStatsService.recordMiss(fileId);
        }
        return exists;
    }

    @Override
    @WithSpan
    public InputStream getChunk(String fileId, long start, long end) throws IOException {
        File chunk = getChunkFile(fileId, start, end);
        if (!chunk.exists()) throw new FileNotFoundException("Chunk not found");
        log.info("🔼 Read chunk from cache: {} [{}-{}]", fileId, start, end);
        return new BufferedInputStream(new FileInputStream(chunk));
    }

    @Override
    @WithSpan
    public void saveChunk(String fileId, long start, long end, InputStream stream) throws IOException {
        if (!chunkLockManager.tryLock(fileId, start, end)) {
            log.warn("⏳ Another thread is already saving chunk {} ({} - {}), skipping.", fileId, start, end);
            throw new IOException("Chunk is being saved by another thread");
        }

        File chunk = getChunkFile(fileId, start, end);
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(chunk))) {
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;
            long total = 0;
            while ((bytesRead = stream.read(buffer)) != -1 && total <= (end - start)) {
                out.write(buffer, 0, bytesRead);
                total += bytesRead;
            }
        } finally {
            chunkLockManager.release(fileId, start, end);
            chunkIndexService.addChunk(fileId, start, end);
            log.info("✅ Released lock for chunk {} ({} - {})", fileId, start, end);
        }
    }

    @Override
    public Optional<Range> findNearestChunk(String fileId, long requestedStart, long requestedEnd, long tolerance) {

        return chunkIndexService.findNearestChunk(fileId, requestedStart, requestedEnd, tolerance);
    }

    @WithSpan
    @Override
    public InputStream getFileRangeFromDisk(String fileId, long start, long end) throws IOException {
        File file = new File(AppConstant.CACHE_DIR, fileId + ".full");
        if (!file.exists()) {
            throw new FileNotFoundException("Full file not found: " + file.getAbsolutePath());
        }
        cacheStatsService.recordHit(fileId);
        long length = end - start + 1;
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek(start);

        return new LimitedInputStream(new FileInputStream(raf.getFD()), length, raf);
    }

    @Override
    public long getFileSizeFromDisk(String fileId) throws IOException {
        File file = new File(AppConstant.CACHE_DIR, fileId + ".full");
        if (!file.exists()) {
            throw new FileNotFoundException("Full file not found: " + file.getAbsolutePath());
        }
        return file.length();
    }

    /**
     * Helper method to get the chunk file based on fileId and byte range.
     *
     * @param fileId The unique identifier for the video file.
     * @param start  The starting byte of the chunk.
     * @param end    The ending byte of the chunk.
     * @return The File object representing the chunk file.
     */

    private File getChunkFile(String fileId, long start, long end) {
        File dir = new File(CACHE_DIR, fileId);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, start + "-" + end + ".cache");
    }

}