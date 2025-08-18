package com.canhlabs.funnyapp.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

public interface VideoStorageService {

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


    /**
     * Deletes a file if it is eligible for deletion based on its last access time.
     *
     * @param fileId The ID of the file to delete.
     */
    void deleteIfEligible(String fileId);

}
