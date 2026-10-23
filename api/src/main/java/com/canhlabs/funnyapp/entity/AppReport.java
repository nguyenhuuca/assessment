package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.GitHubIssueState;
import com.canhlabs.funnyapp.enums.ReportAutoLabel;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.ReportType;
import com.canhlabs.funnyapp.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/** An in-app report and the GitHub issue it produced (ADR-0026). */
@Entity
@Table(name = "app_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppReport {
    @Id
    @GeneratedValue
    private UUID id;

    /** Null once the reporter's account is deleted (ON DELETE SET NULL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reporter_role", nullable = false, length = 20)
    private UserRole reporterRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ReportType type;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "page", length = 32)
    private String page;

    @Column(name = "page_path", length = 300)
    private String pagePath;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(name = "api_version", length = 40)
    private String apiVersion;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "screen_width")
    private Integer screenWidth;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_label", length = 16)
    private ReportAutoLabel autoLabel;

    @Column(name = "autopilot", nullable = false)
    private boolean autopilot;

    @Column(name = "labels", nullable = false, length = 200)
    @Builder.Default
    private String labels = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ReportStatus status;

    @Column(name = "issue_number")
    private Integer issueNumber;

    @Column(name = "issue_url", length = 300)
    private String issueUrl;

    @Column(name = "error", length = 64)
    private String error;

    /** Last state pushed by the webhook; null until the first event (ADR-0026 D7). */
    @Enumerated(EnumType.STRING)
    @Column(name = "github_state", length = 10)
    private GitHubIssueState githubState;

    @Column(name = "github_state_reason", length = 16)
    private String githubStateReason;

    /** The issue's updated_at from the event that set the state; orders late deliveries. */
    @Column(name = "github_state_at")
    private Instant githubStateAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
