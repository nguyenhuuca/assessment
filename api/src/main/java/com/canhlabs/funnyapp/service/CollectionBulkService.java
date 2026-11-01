package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.BulkResultDto;

import java.util.List;
import java.util.UUID;

public interface CollectionBulkService {
    /**
     * Adds each distinct video on its own. A collection the caller cannot manage fails the whole request
     * (404) before any video is touched; afterwards one failing video never stops the rest.
     */
    BulkResultDto addVideos(UUID collectionId, List<Long> videoIds);
}
