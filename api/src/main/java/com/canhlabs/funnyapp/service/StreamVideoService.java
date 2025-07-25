package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.StreamChunkResult;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.google.api.services.drive.Drive;

import java.io.IOException;
import java.util.List;

public interface StreamVideoService {


    /**
     * Retrieves a file from storage using Random Access File (RAF) for partial reads.
     *
     * @param fileId The ID of the file to retrieve.
     * @param start  The starting byte position.
     * @param end    The ending byte position.
     * @return A StreamChunkResult containing the file content and metadata.
     * @throws IOException If an error occurs while accessing the file.
     */
    StreamChunkResult getPartialFileUsingRAF(String fileId, long start, long end) throws IOException;

    /**
     * Gets the size of a file in bytes.
     *
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


    void updateDesc();


}
