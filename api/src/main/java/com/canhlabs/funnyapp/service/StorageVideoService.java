package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.VideoDto;
import com.google.api.services.drive.Drive;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface StorageVideoService {
    InputStream getPartialFile(String fileId, long start, long end) throws IOException;

    long getFileSize(String fileId) throws IOException;

    List<VideoDto> getVideosToStream();

    VideoDto getVideoById(Long id);

    VideoDto getVideoBySourceId(String sourceId);

    void shareFile(Drive drive, String fileId, String email) throws IOException;

    void shareFilesInFolder();
}
