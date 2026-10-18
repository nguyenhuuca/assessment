package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.collection.CollectionDto;
import com.canhlabs.funnyapp.dto.collection.CollectionRequest;
import com.canhlabs.funnyapp.dto.video.VideoDto;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface VideoCollectionService {

    /** Caller's own collections (default "Xem sau" created on first call); admins also get every PUBLIC one. */
    List<CollectionDto> getMine();

    /** PUBLIC collections; guests allowed. Empty ones are only listed for admins. */
    List<CollectionDto> getPublic();

    CollectionDto create(CollectionRequest request);

    CollectionDto update(UUID id, CollectionRequest request);

    void delete(UUID id);

    /** Visible videos in collection order (newest added first until reordered). PUBLIC: anyone; PRIVATE: owner only (404 otherwise). */
    Page<VideoDto> getVideos(UUID id, int page, int size);

    /** Every video id in a collection the caller manages (404 otherwise). */
    List<Long> getVideoIds(UUID id);

    void addVideo(UUID id, Long videoId);

    void removeVideo(UUID id, Long videoId);

    /**
     * Places the video right after {@code afterVideoId} (null = top). Both must be in the collection (404);
     * moving a video after itself is 400. Does not bump updatedAt; a move to where it already is is a no-op.
     */
    void moveVideo(UUID id, Long videoId, Long afterVideoId);

    /** Ids of the caller's manageable collections that contain the video. */
    List<UUID> getMembership(Long videoId);
}
