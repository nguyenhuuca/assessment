package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.Range;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface VideoStorageService {

    Optional<Range> findNearestChunk(String fileId, long requestedStart, long requestedEnd, long tolerance);

    InputStream getFileRangeFromDisk(String fileId, long start, long end) throws IOException;

    long getFileSizeFromDisk(String fileId) throws IOException;

}
