package com.canhlabs.funnyapp.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "video_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoComment {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "video_id", nullable = false)
    private String videoId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "guest_name")
    private String guestName;           // nullable for logged-in users

    @Column(name = "guest_token_hash")
    private String guestTokenHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "parent_id")
    private String parentId;
}