package com.canhlabs.funnyapp.service;

public interface FfmpegService {
    /**
     * Generates a thumbnail from the specified video file.
     *
     * @param videoPath The path to the video file.
     * @param thumbnailPath The path where the thumbnail will be saved.
     */
    void generateThumbnail(String videoPath, String thumbnailPath);
}
