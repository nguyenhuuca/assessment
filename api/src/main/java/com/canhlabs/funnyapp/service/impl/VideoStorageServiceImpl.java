package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.ChunkIndexCache;
import com.canhlabs.funnyapp.cache.LockManager;
import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.dto.Range;
import com.canhlabs.funnyapp.service.VideoAccessService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import com.canhlabs.funnyapp.share.AppConstant;
import com.canhlabs.funnyapp.share.LimitedInputStream;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Optional;


@Slf4j
@Service
public class VideoStorageServiceImpl implements VideoStorageService {

    private StatsCache statsCache;
    private ChunkIndexCache chunkIndexCache;
    private VideoAccessService videoAccessService;

    @Autowired
    public void injectVideoAccessService(VideoAccessService videoAccessService) {
        this.videoAccessService = videoAccessService;
    }

    @Autowired
    public void injectChunkIndexService(ChunkIndexCache chunkIndexCache) {
        this.chunkIndexCache = chunkIndexCache;
    }


    @Autowired
    public void injectCacheStatsService(StatsCache statsCache) {
        this.statsCache = statsCache;
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
        videoAccessService.recordAccess(fileId);
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


}