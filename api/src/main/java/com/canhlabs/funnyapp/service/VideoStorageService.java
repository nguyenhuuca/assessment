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

    /**
     * Permanently deletes the file from Google Drive. A file that is already gone (404) is not an error.
     *
     * @throws IllegalStateException when Drive refuses or fails (e.g. 403: the service account does not own the file)
     */
    void deleteDriveFile(String fileId);

    /**
     * Inserts the Drive video row, or refreshes title and thumbnail of the existing one (force sync).
     * A ChatGPT description is generated only when the row has none.
     */
    void upsertInfo(String fileId, String title, String thumbnailPath);

}
