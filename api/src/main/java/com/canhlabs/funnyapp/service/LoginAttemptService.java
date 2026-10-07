package com.canhlabs.funnyapp.service;

/**
 * Persists failed-login counters and lockouts in their own transaction, so the counters survive the exception
 * (401/400) that the caller throws right afterwards.
 */
public interface LoginAttemptService {
    int MAX_FAILURES = 5;
    long LOCK_MINUTES = 15;

    /** Increments the failure counter; at {@link #MAX_FAILURES} locks the account for {@link #LOCK_MINUTES} and resets the counter. */
    void recordFailure(Long userId);

    /** Clears the failure counter and any lock. */
    void recordSuccess(Long userId);
}
