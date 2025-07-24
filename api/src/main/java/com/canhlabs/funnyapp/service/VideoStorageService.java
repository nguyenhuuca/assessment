package com.canhlabs.funnyapp.service;

import java.io.IOException;
import java.io.InputStream;

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

}
