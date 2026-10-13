package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.CollectionVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A named list of videos owned by {@code userId}. The default ("Xem sau") collection is always PRIVATE (DB CHECK).
 * Items live in video_collection_items and are written through VideoCollectionItemRepository.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "video_collections")
public class VideoCollection {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 10)
    private CollectionVisibility visibility = CollectionVisibility.PRIVATE;

    @Column(name = "is_default", nullable = false, updatable = false)
    private boolean isDefault;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
