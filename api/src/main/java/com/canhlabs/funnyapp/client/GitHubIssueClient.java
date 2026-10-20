package com.canhlabs.funnyapp.client;

import java.util.List;

/** Creates and reads GitHub issues for in-app reports (ADR-0026). */
public interface GitHubIssueClient {

    enum IssueState { OPEN, CLOSED, UNKNOWN }

    record CreatedIssue(int number, String htmlUrl) { }

    /**
     * @throws GitHubException when GitHub is not configured or the call fails
     */
    CreatedIssue create(String title, String body, List<String> labels);

    /** Never throws: any failure is {@link IssueState#UNKNOWN}. */
    IssueState state(int number);

    /** Failure carrying a short code only; never the token or a response body. */
    class GitHubException extends RuntimeException {
        private final String code;

        public GitHubException(String code) {
            super(code);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
