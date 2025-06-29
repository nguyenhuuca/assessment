package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.Range;

import java.util.Optional;
import java.util.Set;

public interface ChunkIndexService {
    void addChunk(String fileId, long start, long end);
    boolean hasChunk(String fileId, long start, long end);
    Optional<Range> findNearestChunk(String fileId, long start, long end, long tolerance);
    void clear(String fileId);
    void preload(String fileId, Set<Range> ranges);

}
