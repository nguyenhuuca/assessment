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

    /** Visible videos, newest added first. PUBLIC: anyone; PRIVATE: owner only (404 otherwise). */
    Page<VideoDto> getVideos(UUID id, int page, int size);

    void addVideo(UUID id, Long videoId);

    void removeVideo(UUID id, Long videoId);

    /** Ids of the caller's manageable collections that contain the video. */
    List<UUID> getMembership(Long videoId);
}
