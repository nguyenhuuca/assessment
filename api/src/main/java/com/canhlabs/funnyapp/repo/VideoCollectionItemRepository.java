package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.VideoSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * video_collection_items has no entity: rows are (collection, video) pairs written with native SQL.
 * "Visible" everywhere below means is_hide = false AND status = 'PUBLISHED'.
 */
public interface VideoCollectionItemRepository extends Repository<VideoSource, Long> {

    String VISIBLE = "v.is_hide = false AND v.status = 'PUBLISHED'";

    /** Returns 1 when inserted, 0 when the pair already existed. */
    @Modifying
    @Query(value = "INSERT INTO video_collection_items (collection_id, video_id) VALUES (:collectionId, :videoId) "
            + "ON CONFLICT (collection_id, video_id) DO NOTHING", nativeQuery = true)
    int addItem(@Param("collectionId") UUID collectionId, @Param("videoId") Long videoId);

    @Modifying
    @Query(value = "DELETE FROM video_collection_items WHERE collection_id = :collectionId AND video_id = :videoId",
            nativeQuery = true)
    int removeItem(@Param("collectionId") UUID collectionId, @Param("videoId") Long videoId);

    @Query(value = "SELECT COUNT(*) FROM video_collection_items WHERE collection_id = :collectionId", nativeQuery = true)
    long countItems(@Param("collectionId") UUID collectionId);

    @Query(value = "SELECT v.* FROM video_collection_items i JOIN video_sources v ON v.id = i.video_id "
            + "WHERE i.collection_id = :collectionId AND " + VISIBLE + " ORDER BY i.added_at DESC, v.id DESC",
            countQuery = "SELECT COUNT(*) FROM video_collection_items i JOIN video_sources v ON v.id = i.video_id "
                    + "WHERE i.collection_id = :collectionId AND " + VISIBLE,
            nativeQuery = true)
    Page<VideoSource> findVisibleVideos(@Param("collectionId") UUID collectionId, Pageable pageable);

    @Query(value = "SELECT CAST(i.collection_id AS varchar) AS \"collectionId\", "
            + "COUNT(*) FILTER (WHERE " + VISIBLE + ") AS \"visibleCount\", "
            + "COUNT(*) FILTER (WHERE NOT (" + VISIBLE + ")) AS \"unavailableCount\" "
            + "FROM video_collection_items i JOIN video_sources v ON v.id = i.video_id "
            + "WHERE i.collection_id IN (:collectionIds) GROUP BY i.collection_id", nativeQuery = true)
    List<ItemCounts> countByCollection(@Param("collectionIds") Collection<UUID> collectionIds);

    /** Newest visible video per collection, used as the cover. */
    @Query(value = "SELECT DISTINCT ON (i.collection_id) CAST(i.collection_id AS varchar) AS \"collectionId\", "
            + "v.id AS \"videoId\", v.source_id AS \"fileId\" "
            + "FROM video_collection_items i JOIN video_sources v ON v.id = i.video_id "
            + "WHERE i.collection_id IN (:collectionIds) AND " + VISIBLE + " "
            + "ORDER BY i.collection_id, i.added_at DESC, v.id DESC", nativeQuery = true)
    List<Cover> findCovers(@Param("collectionIds") Collection<UUID> collectionIds);

    @Query(value = "SELECT CAST(collection_id AS varchar) FROM video_collection_items "
            + "WHERE video_id = :videoId AND collection_id IN (:collectionIds)", nativeQuery = true)
    List<String> findCollectionIdsContaining(@Param("videoId") Long videoId,
                                             @Param("collectionIds") Collection<UUID> collectionIds);

    interface ItemCounts {
        String getCollectionId();
        Long getVisibleCount();
        Long getUnavailableCount();
    }

    interface Cover {
        String getCollectionId();
        Long getVideoId();
        String getFileId();
    }
}
