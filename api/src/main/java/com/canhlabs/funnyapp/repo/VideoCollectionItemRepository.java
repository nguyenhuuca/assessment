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
 * "Visible" everywhere below means visibility <> HIDDEN AND status = 'PUBLISHED'.
 */
public interface VideoCollectionItemRepository extends Repository<VideoSource, Long> {

    String VISIBLE = "v.visibility <> 'HIDDEN' AND v.status = 'PUBLISHED'";

    /** Inserts at the top (MIN(position) - 1). Returns 1 when inserted, 0 when the pair already existed. */
    @Modifying
    @Query(value = "INSERT INTO video_collection_items (collection_id, video_id, position) "
            + "SELECT :collectionId, :videoId, COALESCE((SELECT MIN(position) FROM video_collection_items "
            + "WHERE collection_id = :collectionId), 0) - 1 "
            + "ON CONFLICT (collection_id, video_id) DO NOTHING", nativeQuery = true)
    int addItem(@Param("collectionId") UUID collectionId, @Param("videoId") Long videoId);

    @Modifying
    @Query(value = "DELETE FROM video_collection_items WHERE collection_id = :collectionId AND video_id = :videoId",
            nativeQuery = true)
    int removeItem(@Param("collectionId") UUID collectionId, @Param("videoId") Long videoId);

    @Query(value = "SELECT COUNT(*) FROM video_collection_items WHERE collection_id = :collectionId", nativeQuery = true)
    long countItems(@Param("collectionId") UUID collectionId);

    @Query(value = "SELECT v.* FROM video_collection_items i JOIN video_sources v ON v.id = i.video_id "
            + "WHERE i.collection_id = :collectionId AND " + VISIBLE + " ORDER BY i.position, i.video_id",
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

    /** First visible video (in collection order) per collection, used as the cover. LATERAL + LIMIT 1 walks idx_video_collection_items_position. */
    @Query(value = "SELECT CAST(c.id AS varchar) AS \"collectionId\", x.id AS \"videoId\", x.source_id AS \"fileId\" "
            + "FROM video_collections c CROSS JOIN LATERAL ("
            + "SELECT v.id, v.source_id FROM video_collection_items i JOIN video_sources v ON v.id = i.video_id "
            + "WHERE i.collection_id = c.id AND " + VISIBLE + " ORDER BY i.position, i.video_id LIMIT 1) x "
            + "WHERE c.id IN (:collectionIds)", nativeQuery = true)
    List<Cover> findCovers(@Param("collectionIds") Collection<UUID> collectionIds);

    /** Every video id in the collection (at most the item cap), visible or not. */
    @Query(value = "SELECT video_id FROM video_collection_items WHERE collection_id = :collectionId", nativeQuery = true)
    List<Long> findVideoIds(@Param("collectionId") UUID collectionId);

    /** Every video id in collection order, visible or not. */
    @Query(value = "SELECT video_id FROM video_collection_items WHERE collection_id = :collectionId "
            + "ORDER BY position, video_id", nativeQuery = true)
    List<Long> findOrderedVideoIds(@Param("collectionId") UUID collectionId);

    /** Rewrites positions to 0..n-1 following {@code videoIds}. */
    @Modifying
    @Query(value = "UPDATE video_collection_items i SET position = o.ord - 1 "
            + "FROM unnest(CAST(:videoIds AS bigint[])) WITH ORDINALITY o(video_id, ord) "
            + "WHERE i.collection_id = :collectionId AND i.video_id = o.video_id", nativeQuery = true)
    int renumber(@Param("collectionId") UUID collectionId, @Param("videoIds") Long[] videoIds);

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
