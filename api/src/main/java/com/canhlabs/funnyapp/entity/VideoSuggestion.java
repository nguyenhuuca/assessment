package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.enums.SuggestionRejectReason;
import com.canhlabs.funnyapp.enums.SuggestionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/** A user's proposal to add a YouTube / Facebook video; an admin approves it into the import pipeline. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(name = "video_suggestions")
public class VideoSuggestion extends BaseDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Column(name = "normalized_url", nullable = false)
    private String normalizedUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private ImportPlatform platform;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "note", length = 300)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default // builder ignores field initialisers
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason", length = 32)
    private SuggestionRejectReason rejectReason;

    @Column(name = "reject_note", length = 300)
    private String rejectNote;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "import_job_id")
    private Long importJobId;

    /** User re-review requests so far; admin reopens do not count. */
    @Column(name = "rereview_count", nullable = false)
    @Builder.Default
    private int rereviewCount = 0;
}
