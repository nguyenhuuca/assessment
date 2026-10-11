package com.canhlabs.funnyapp.service;

import java.io.IOException;
import java.util.function.LongConsumer;

/** Downloads one Drive video into the local video cache (used by the sync cron and the admin force sync). */
public interface DriveVideoDownloader {

    /**
     * Downloads into a temporary {@code .part} file and moves it over {@code video-cache/{fileId}.full}
     * only on success, so a failed run never leaves a partial file. Evicts cached chunks of the file
     * and regenerates the thumbnail.
     *
     * @param expectedSize Drive size in bytes, used for the free disk check (null = unknown)
     * @param progress     receives the number of bytes written so far
     * @return public thumbnail URL
     */
    String download(String fileId, Long expectedSize, LongConsumer progress) throws IOException;

    /** Video title from a Drive file name: the name without its extension. */
    static String titleOf(String fileName) {
        return fileName == null ? null : fileName.replaceFirst("[.][^.]+$", "");
    }
}
