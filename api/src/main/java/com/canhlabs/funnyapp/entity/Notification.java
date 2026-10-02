package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-app notification inbox row. Rows are created/collapsed only through
 * NotificationRepository#upsertGrouped (the DB generates id and timestamps).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "group_key", nullable = false, length = 120)
    private String groupKey;

    @Column(name = "video_id", length = 100)
    private String videoId;

    @Column(name = "comment_id")
    private UUID commentId;

    @Column(name = "actor_display", length = 100)
    private String actorDisplay;

    @Column(name = "actor_count", nullable = false)
    private int actorCount = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "emailed_at")
    private Instant emailedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
