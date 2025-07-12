package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.LockManager;
import com.canhlabs.funnyapp.dto.Range;
import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.cache.ChunkIndexCache;
import com.canhlabs.funnyapp.service.VideoStorageService;
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
import java.util.Optional;

import static com.canhlabs.funnyapp.share.AppConstant.CACHE_DIR;


@Slf4j
@Service
public class VideoStorageServiceImpl implements VideoStorageService {
    private static final int MAX_CACHE_FILES = 6000;
    private static final long MAX_CACHE_SIZE_BYTES = 1024 * 1024 * 1024L; //1G

    private LockManager lockManager;
    private StatsCache statsCache;
    private ChunkIndexCache chunkIndexCache;

    @Autowired
    public void injectChunkIndexService(ChunkIndexCache chunkIndexCache) {
        this.chunkIndexCache = chunkIndexCache;
    }

    @Autowired
    public void injectChunkLockManager(LockManager lockManager) {
        this.lockManager = lockManager;
    }

    @Autowired
    public void injectCacheStatsService(StatsCache statsCache) {
        this.statsCache = statsCache;
    }
    
    @Override
    @WithSpan
    public boolean hasChunk(String fileId, long start, long end) {
        File chunk = getChunkFile(fileId, start, end);
        boolean exists = chunk.exists();// && chunk.length() == (end - start + 1);
        log.info("Check chunk exists: {} [{}-{}] = {}", fileId, start, end, exists);
        if (exists) {
            statsCache.recordHit(fileId);
        } else {
            statsCache.recordMiss(fileId);
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
        if (!lockManager.tryLock(fileId, start, end)) {
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
            lockManager.release(fileId, start, end);
            chunkIndexCache.addChunk(fileId, start, end);
            log.info("✅ Released lock for chunk {} ({} - {})", fileId, start, end);
        }
    }

    @Override
    public Optional<Range> findNearestChunk(String fileId, long requestedStart, long requestedEnd, long tolerance) {

        return chunkIndexCache.findNearestChunk(fileId, requestedStart, requestedEnd, tolerance);
    }

    @WithSpan
    @Override
    public InputStream getFileRangeFromDisk(String fileId, long start, long end) throws IOException {
        File file = new File(AppConstant.CACHE_DIR, fileId + ".full");
        if (!file.exists()) {
            throw new FileNotFoundException("Full file not found: " + file.getAbsolutePath());
        }
        statsCache.recordHit(fileId);
        long length = end - start + 1;
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek(start);

        return new LimitedInputStream(new FileInputStream(raf.getFD()), length, raf);
    }

    @WithSpan
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