package com.canhlabs.funnyapp.cache;

import com.canhlabs.funnyapp.dto.Range;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

@Service
@Slf4j
public class ChunkIndexCacheImpl implements ChunkIndexCache {

    private final AppCache<String, Set<Range>> rangeCache;

    public ChunkIndexCacheImpl( @Qualifier("chunkIdxCache") AppCache<String, Set<Range>> rangeCache) {
        this.rangeCache = rangeCache;
    }

    @Override
    @WithSpan
    public void addChunk(String fileId, long start, long end) {
        if (fileId == null) throw new NullPointerException("fileId must not be null");
        if (start > end) throw new IllegalArgumentException("start must be <= end");
        log.info("Adding chunk to cache for fileId: {}, start: {}, end: {}", fileId, start, end);
        rangeCache.get(fileId, HashSet::new).add(new Range(start, end));
    }

    @Override
    @WithSpan
    public boolean hasChunk(String fileId, long start, long end) {
        if (fileId == null) throw new NullPointerException("fileId must not be null");
        boolean isExisted =  rangeCache.get(fileId)
                .map(set -> set.stream().anyMatch(r -> r.start() <= start && r.end() >= end))
                .orElse(false);

        log.info("Checking chunk for fileId: {}, start: {}, end: {} - Exists: {}", fileId, start, end, isExisted);
        return isExisted;
    }

    @Override
    @WithSpan
    public Optional<Range> findNearestChunk(String fileId, long start, long end, long tolerance) {
        if (fileId == null) throw new NullPointerException("fileId must not be null");
        if (tolerance < 0) throw new IllegalArgumentException("tolerance must be >= 0");
        return rangeCache.get(fileId)
                .flatMap(set -> set.stream()
                        .filter(r -> Math.abs(r.start() - start) <= tolerance || Math.abs(r.end() - end) <= tolerance)
                        .findFirst());
    }

    @Override
    @WithSpan
    public void clear(String fileId) {
        rangeCache.invalidate(fileId);
    }

    @Override
    @WithSpan
    public void preload(String fileId, Set<Range> ranges) {
        rangeCache.put(fileId, ranges);
    }
}