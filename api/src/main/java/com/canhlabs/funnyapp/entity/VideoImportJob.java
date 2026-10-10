package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.enums.ImportStatus;
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

/** One admin-requested import of a YouTube / Facebook video into the Drive folder (ADR-0019). */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(name = "video_import_jobs")
public class VideoImportJob extends BaseDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Column(name = "normalized_url", nullable = false)
    private String normalizedUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private ImportPlatform platform;

    @Column(name = "requested_title", length = 200)
    private String requestedTitle;

    @Column(name = "resolved_title", length = 200)
    private String resolvedTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default // builder ignores field initialisers
    private ImportStatus status = ImportStatus.PENDING;

    @Column(name = "progress_pct", nullable = false)
    @Builder.Default
    private int progressPct = 0;

    @Column(name = "downloaded_bytes", nullable = false)
    @Builder.Default
    private long downloadedBytes = 0;

    @Column(name = "total_bytes", nullable = false)
    @Builder.Default
    private long totalBytes = 0;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "drive_file_id", length = 128)
    private String driveFileId;

    @Column(name = "error_code", length = 48)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "created_by")
    private Long createdBy;
}
