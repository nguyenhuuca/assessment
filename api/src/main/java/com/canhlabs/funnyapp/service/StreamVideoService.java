package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.StreamChunkResult;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.google.api.services.drive.Drive;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface StreamVideoService {


    /**
     * Retrieves a file from storage by its ID.
     *
     * @param fileId The ID of the file to retrieve.
     * @return An InputStream to read the file content.
     * @throws IOException If an error occurs while accessing the file.
     */
    InputStream getPartialFile(String fileId, long start, long end) throws IOException;

    /**
     * Retrieves a file from storage by its ID and specified byte range.
     *
     * @param fileId The ID of the file to retrieve.
     * @param start The starting byte position.
     * @param end The ending byte position.
     * @return An InputStream to read the specified range of the file content.
     * @throws IOException If an error occurs while accessing the file.
     */
    StreamChunkResult getPartialFileByChunk(String fileId, long start, long end) throws IOException;

    /**
     * Retrieves a file from storage using Random Access File (RAF) for partial reads.
     *
     * @param fileId The ID of the file to retrieve.
     * @param start The starting byte position.
     * @param end The ending byte position.
     * @return A StreamChunkResult containing the file content and metadata.
     * @throws IOException If an error occurs while accessing the file.
     */
    StreamChunkResult getPartialFileUsingRAF(String fileId, long start, long end) throws IOException;

    /**
     * Gets the size of a file in bytes.
     * @param fileId The ID of the file to check.
     * @return The size of the file in bytes.
     * @throws IOException If an error occurs while accessing the file.
     */
    long getFileSize(String fileId) throws IOException;


    /**
     * Retrieves a list of videos available for streaming.
     *
     * @return A list of VideoDto objects representing the videos.
     */
    List<VideoDto> getVideosToStream();

    /**
     * Retrieves a video by its ID.
     *
     * @param id The ID of the video to retrieve.
     * @return A VideoDto object representing the video.
     */
    VideoDto getVideoById(Long id);

    /**
     * Retrieves a video by its source ID.
     *
     * @param sourceId The source ID of the video to retrieve.
     * @return A VideoDto object representing the video.
     */
    VideoDto getVideoBySourceId(String sourceId);

    /**
     * Shares a file with a specified email address.
     *
     * @param drive The Google Drive service instance.
     * @param fileId The ID of the file to share.
     * @param email The email address to share the file with.
     * @throws IOException If an error occurs while sharing the file.
     */
    void shareFile(Drive drive, String fileId, String email) throws IOException;

    /**
     * Shares all files in a specified folder.
     *
     */
    void shareFilesInFolder();
    void updateDesc();

}
