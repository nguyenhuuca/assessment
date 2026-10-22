package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.AdminStatsDto;
import com.canhlabs.funnyapp.dto.admin.AdminVideoDto;
import com.canhlabs.funnyapp.enums.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminVideoService {
    Page<AdminVideoDto> getVideos(Pageable pageable, VideoStatus status);
    void updateStatus(Long id, VideoStatus status);
    void updatePriority(Long id, int priority);
    void updateHidden(Long id, boolean hidden);
    /** Drive file first (a failure keeps the row), then the local file, then the row. */
    void deleteVideo(Long id, boolean deleteLocalFile, boolean deleteDriveFile);
    AdminStatsDto getStats();
}
