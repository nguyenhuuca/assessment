package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.AdminStatsDto;
import com.canhlabs.funnyapp.dto.AdminVideoDto;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.AdminVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminVideoServiceImpl implements AdminVideoService {

    private VideoSourceRepository videoSourceRepository;
    private UserRepo userRepo;

    @Autowired
    public void injectVideoSourceRepository(VideoSourceRepository videoSourceRepository) {
        this.videoSourceRepository = videoSourceRepository;
    }

    @Autowired
    public void injectUserRepo(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public Page<AdminVideoDto> getVideos(Pageable pageable, VideoStatus status) {
        Page<VideoSource> page = (status != null)
                ? videoSourceRepository.findAllByStatus(status, pageable)
                : videoSourceRepository.findAll(pageable);
        return page.map(this::toDto);
    }

    @Override
    public void updateStatus(Long id, VideoStatus status) {
        VideoSource source = videoSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found: " + id));
        source.setStatus(status);
        videoSourceRepository.save(source);
    }

    @Override
    public void deleteVideo(Long id) {
        videoSourceRepository.deleteById(id);
    }

    @Override
    public AdminStatsDto getStats() {
        long total = videoSourceRepository.count();
        long pending = videoSourceRepository.countByStatus(VideoStatus.PENDING);
        long flagged = videoSourceRepository.countByStatus(VideoStatus.FLAGGED);
        long totalUsers = userRepo.count();
        return AdminStatsDto.builder()
                .totalVideos(total)
                .totalUsers(totalUsers)
                .pendingCount(pending)
                .flaggedCount(flagged)
                .build();
    }

    private AdminVideoDto toDto(VideoSource v) {
        return AdminVideoDto.builder()
                .id(v.getId())
                .title(v.getTitle())
                .thumbnailPath(v.getThumbnailPath())
                .status(v.getStatus())
                .viewCount(0L)
                .createdAt(v.getCreatedAt())
                .build();
    }
}
