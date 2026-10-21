package com.canhlabs.funnyapp.event;

/**
 * Published when the GitHub webhook stored a closed state on an in-app report's issue (ADR-0026 D7).
 * reason is the stored state_reason (completed, not_planned, duplicate) or null, captured at publish time.
 */
public record ReportClosedEvent(int issueNumber, String reason) {
}
