package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.ReactionType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One reaction per (user, video). Writes go through VideoReactionRepository#upsert.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "video_reactions")
public class VideoReaction {

    @EmbeddedId
    private VideoReactionId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction", nullable = false, length = 10)
    private ReactionType reaction;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
