package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.Range;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface VideoStorageService {

    Optional<Range> findNearestChunk(String fileId, long requestedStart, long requestedEnd, long tolerance);

    InputStream getFileRangeFromDisk(String fileId, long start, long end) throws IOException;

    long getFileSizeFromDisk(String fileId) throws IOException;

    /**
     * Downloads a file from a specified folder that was uploaded after a certain date.
     *
     * @param folderId      The ID of the googloe folder to download from.
     * @param uploadedAfter The date after which files should be downloaded (in ISO 8601 format).
     * @throws IOException If an error occurs while downloading the file.
     */
    void downloadFileFromFolder(String folderId, String uploadedAfter) throws IOException;

}
