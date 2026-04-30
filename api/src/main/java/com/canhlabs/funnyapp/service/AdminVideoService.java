package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.AdminStatsDto;
import com.canhlabs.funnyapp.dto.AdminVideoDto;
import com.canhlabs.funnyapp.enums.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminVideoService {
    Page<AdminVideoDto> getVideos(Pageable pageable, VideoStatus status);
    void updateStatus(Long id, VideoStatus status);
    void deleteVideo(Long id);
    AdminStatsDto getStats();
}
