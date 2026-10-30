package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.DriveFileLocks;
import com.canhlabs.funnyapp.cache.FeedCache;
import com.canhlabs.funnyapp.dto.admin.AdminHlsStatusDto;
import com.canhlabs.funnyapp.dto.admin.AdminStatsDto;
import com.canhlabs.funnyapp.dto.admin.AdminVideoDto;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.CommentStatus;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.enums.VideoVisibility;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoCommentRepository;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.AdminVideoService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import com.canhlabs.funnyapp.utils.Contract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminVideoServiceImpl implements AdminVideoService {

    private static final String DRIVE_SOURCE = "google_drive";

    private VideoSourceRepository videoSourceRepository;
    private UserRepo userRepo;
    private VideoCommentRepository commentRepository;
    private VideoStorageService videoStorageService;
    private HlsStorage hlsStorage;
    private HlsTranscodeWorker hlsWorker;
    private DriveFileLocks driveFileLocks;
    private FeedCache feedCache;

    @Autowired
    public void injectFeedCache(FeedCache feedCache) {
        this.feedCache = feedCache;
    }

    @Autowired
    public void injectHlsStorage(HlsStorage hlsStorage) {
        this.hlsStorage = hlsStorage;
    }

    @Autowired
    public void injectHlsWorker(HlsTranscodeWorker hlsWorker) {
        this.hlsWorker = hlsWorker;
    }

    @Autowired
    public void injectDriveFileLocks(DriveFileLocks driveFileLocks) {
        this.driveFileLocks = driveFileLocks;
    }

    @Autowired
    public void injectVideoStorageService(VideoStorageService videoStorageService) {
        this.videoStorageService = videoStorageService;
    }

    @Autowired
    public void injectVideoSourceRepository(VideoSourceRepository videoSourceRepository) {
        this.videoSourceRepository = videoSourceRepository;
    }

    @Autowired
    public void injectUserRepo(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Autowired
    public void injectVideoCommentRepository(VideoCommentRepository commentRepository) {
        this.commentRepository = commentRepository;
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
        feedCache.invalidate();
    }

    @Override
    public void updatePriority(Long id, int priority) {
        Contract.require(priority >= 0 && priority <= 9999, "Priority must be between 0 and 9999");
        VideoSource source = videoSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found: " + id));
        source.setPriority(priority);
        videoSourceRepository.save(source);
        feedCache.invalidate();
    }

    @Override
    public void updateVisibility(Long id, VideoVisibility visibility) {
        VideoSource source = videoSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found: " + id));
        source.setVisibility(visibility);
        videoSourceRepository.save(source);
        feedCache.invalidate();
    }

    @Override
    public void deleteVideo(Long id, boolean deleteLocalFile, boolean deleteDriveFile) {
        VideoSource source = videoSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found: " + id));
        if (deleteDriveFile && DRIVE_SOURCE.equals(source.getSourceType())) {
            videoStorageService.deleteDriveFile(source.getSourceId());
        }
        if (deleteLocalFile) {
            videoStorageService.deleteIfEligible(source.getSourceId());
        }
        deleteHlsDirectory(source.getSourceId());
        videoSourceRepository.deleteById(id);
        feedCache.invalidate();
    }

    @Override
    public AdminHlsStatusDto enableHls(Long id) {
        VideoSource source = findDriveSource(id);
        HlsStatus current = source.getHlsStatus();
        if (current == HlsStatus.NONE || current == HlsStatus.ERROR) {
            source.setHlsStatus(HlsStatus.PENDING);
            source.setHlsError(null);
            videoSourceRepository.save(source);
            hlsWorker.wakeUp();
        }
        feedCache.invalidate();
        return toHlsDto(source);
    }

    @Override
    public AdminHlsStatusDto disableHls(Long id) {
        VideoSource source = findDriveSource(id);
        source.setHlsStatus(HlsStatus.NONE);
        source.setHlsError(null);
        videoSourceRepository.save(source);
        deleteHlsDirectory(source.getSourceId());
        feedCache.invalidate();
        return toHlsDto(source);
    }

    private VideoSource findDriveSource(Long id) {
        VideoSource source = videoSourceRepository.findById(id)
                .orElseThrow(() -> CustomException.of(HttpStatus.NOT_FOUND, 404, "error.video.notFound"));
        if (!DRIVE_SOURCE.equals(source.getSourceType())) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.hls.notDriveVideo");
        }
        return source;
    }

    /**
     * Removes the rendition directory under the file lock. When a download or a transcode holds the lock,
     * that holder cleans up (BR-10, FR-4) and the startup sweep removes anything left (BR-11).
     */
    private void deleteHlsDirectory(String fileId) {
        if (!HlsStorage.isValidFileId(fileId) || !driveFileLocks.tryClaim(fileId)) {
            return;
        }
        try {
            hlsStorage.delete(fileId);
        } finally {
            driveFileLocks.release(fileId);
        }
    }

    private static AdminHlsStatusDto toHlsDto(VideoSource source) {
        return AdminHlsStatusDto.builder()
                .id(source.getId())
                .hlsStatus(source.getHlsStatus())
                .hlsError(source.getHlsError())
                .build();
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
                .totalComments(commentRepository.count())
                .removedComments(commentRepository.countByStatus(CommentStatus.REMOVED))
                .build();
    }

    private AdminVideoDto toDto(VideoSource v) {
        return AdminVideoDto.builder()
                .id(v.getId())
                .title(v.getTitle())
                .thumbnailPath(v.getThumbnailPath())
                .status(v.getStatus())
                .visibility(v.getVisibility())
                .viewCount(v.getViewCount())
                .priority(v.getPriority())
                .createdAt(v.getCreatedAt())
                .hlsStatus(DRIVE_SOURCE.equals(v.getSourceType()) ? v.getHlsStatus() : null)
                .hlsError(DRIVE_SOURCE.equals(v.getSourceType()) ? v.getHlsError() : null)
                .build();
    }
}
