package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.AppReport;
import com.canhlabs.funnyapp.enums.GitHubIssueState;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AppReportRepository extends JpaRepository<AppReport, UUID> {

    /** Newest first; the reporter is fetched in the same query to avoid N+1. */
    @EntityGraph(attributePaths = "reporter")
    Page<AppReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Loads the reporter with the row so it can be read outside a transaction (retry). */
    @EntityGraph(attributePaths = "reporter")
    Optional<AppReport> findWithReporterById(UUID id);

    /** Counts toward the autopilot cap (BR-3); served by idx_app_report_autopilot_cap. */
    long countByStatusAndAutopilotTrueAndReporterRoleAndCreatedAtAfter(
            ReportStatus status, UserRole reporterRole, Instant since);

    /**
     * Stores a webhook state on the CREATED report of that issue, only when the event is newer than the
     * stored one (ADR-0026 D7); returns the number of rows changed.
     */
    @Transactional
    @Modifying
    @Query("""
            UPDATE AppReport r SET r.githubState = :state, r.githubStateReason = :reason, r.githubStateAt = :at
            WHERE r.status = com.canhlabs.funnyapp.enums.ReportStatus.CREATED AND r.issueNumber = :number
              AND (r.githubStateAt IS NULL OR r.githubStateAt < :at)
            """)
    int applyGithubState(@Param("number") int number, @Param("state") GitHubIssueState state,
                         @Param("reason") String reason, @Param("at") Instant at);

    boolean existsByStatusAndIssueNumber(ReportStatus status, Integer issueNumber);
}
