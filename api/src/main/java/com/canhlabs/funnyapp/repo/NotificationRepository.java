package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every read/update is scoped by user id so a user can only ever touch their own rows.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(UUID id, Long userId);

    List<Notification> findByUserIdAndReadAtIsNullAndEmailedAtIsNullOrderByUpdatedAtDesc(Long userId);

    /**
     * Collapses unread notifications of the same group into one row. The ON CONFLICT target and
     * predicate must match the partial unique index uq_notifications_unread_group exactly.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO notifications (user_id, type, group_key, video_id, comment_id, actor_display, payload)
            VALUES (:userId, :type, :groupKey, CAST(:videoId AS varchar), CAST(:commentId AS uuid),
                    CAST(:actorDisplay AS varchar), CAST(:payload AS jsonb))
            ON CONFLICT (user_id, group_key) WHERE read_at IS NULL
            DO UPDATE SET actor_count = notifications.actor_count + 1,
                          actor_display = EXCLUDED.actor_display,
                          payload = EXCLUDED.payload,
                          comment_id = EXCLUDED.comment_id,
                          updated_at = now(),
                          emailed_at = NULL
            """, nativeQuery = true)
    int upsertGrouped(@Param("userId") Long userId,
                      @Param("type") String type,
                      @Param("groupKey") String groupKey,
                      @Param("videoId") String videoId,
                      @Param("commentId") String commentId,
                      @Param("actorDisplay") String actorDisplay,
                      @Param("payload") String payloadJson);

    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL")
    int markRead(@Param("id") UUID id, @Param("userId") Long userId, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.emailedAt = :now WHERE n.id IN :ids")
    int markEmailed(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    /**
     * Users that opted in to email and still have unread, not-yet-emailed notifications.
     */
    @Query(value = """
            SELECT DISTINCT n.user_id FROM notifications n
            JOIN user_settings s ON s.user_id = n.user_id
            WHERE n.read_at IS NULL AND n.emailed_at IS NULL AND s.notify_email = true
            ORDER BY n.user_id
            """, nativeQuery = true)
    List<Long> findUserIdsForDigest();

    @Transactional
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.readAt IS NOT NULL AND n.readAt < :cutoff")
    int deleteReadOlderThan(@Param("cutoff") Instant cutoff);
}
