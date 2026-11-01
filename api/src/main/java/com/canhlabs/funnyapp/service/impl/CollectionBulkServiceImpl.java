package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.ResourceNotFoundException;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.CollectionBulkService;
import com.canhlabs.funnyapp.service.VideoCollectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Bulk orchestration only (ADR-0036): not transactional, each row goes through the single-row
 * {@link VideoCollectionService#addVideo}, so its guards (eligibility, 500 cap, locking) apply per video.
 * Manageability is checked once up front with the same rule as every collection write.
 */
@Service
public class CollectionBulkServiceImpl implements CollectionBulkService {
    static final String VIDEO_NOT_FOUND_KEY = "error.video.notFound";
    static final String NOT_ELIGIBLE_KEY = "error.collection.videoNotEligible";

    private VideoCollectionService collectionService;
    private VideoSourceRepository videoSourceRepository;

    @Autowired
    public void injectCollectionService(VideoCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @Autowired
    public void injectVideoSourceRepository(VideoSourceRepository videoSourceRepository) {
        this.videoSourceRepository = videoSourceRepository;
    }

    @Override
    public BulkResultDto addVideos(UUID collectionId, List<Long> videoIds) {
        collectionService.requireManageable(collectionId);
        List<Long> ids = List.copyOf(new LinkedHashSet<>(videoIds));
        BulkResultCollector collector = new BulkResultCollector("collection:addVideos");
        for (Long videoId : ids) {
            collector.run(videoId, () -> addRow(collectionId, videoId));
        }
        return collector.build(ids.size());
    }

    /**
     * {@code addVideo} answers "video not found" for a missing id and for one that is not PUBLISHED or is
     * HIDDEN. Bulk tells them apart: a missing id is reported under notFound, an existing but ineligible
     * one is skipped with {@code error.collection.videoNotEligible}.
     */
    private boolean addRow(UUID collectionId, Long videoId) {
        try {
            return collectionService.addVideo(collectionId, videoId);
        } catch (CustomException e) {
            if (!VIDEO_NOT_FOUND_KEY.equals(e.getMessageKey())) {
                throw e;
            }
            if (!videoSourceRepository.existsById(videoId)) {
                throw new ResourceNotFoundException("Video not found: " + videoId);
            }
            throw CustomException.of(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(), NOT_ELIGIBLE_KEY);
        }
    }
}
