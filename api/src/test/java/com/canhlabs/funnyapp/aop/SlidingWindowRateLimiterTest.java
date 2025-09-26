package com.canhlabs.funnyapp.aop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowRateLimiterTest {

    private SlidingWindowRateLimiter rateLimiter;
    private final String clientKey = "client1";

    @BeforeEach
    void setUp() {
        rateLimiter = new SlidingWindowRateLimiter();
    }

    @Test
    void allowRequest_withinLimit_shouldAllow() {
        boolean allowed1 = rateLimiter.allowRequest(clientKey, "/login", 3, 1000);
        boolean allowed2 = rateLimiter.allowRequest(clientKey, "/login", 3, 1000);
        boolean allowed3 = rateLimiter.allowRequest(clientKey, "/login", 3, 1000);

        assertTrue(allowed1);
        assertTrue(allowed2);
        assertTrue(allowed3);
    }

    @Test
    void allowRequest_exceedLimit_shouldBlock() {
        rateLimiter.allowRequest(clientKey, "/search", 2, 1000);
        rateLimiter.allowRequest(clientKey, "/search", 2, 1000);

        boolean blocked = rateLimiter.allowRequest(clientKey, "/search", 2, 1000);
        assertFalse(blocked, "Third request within window should be blocked");
    }

    @Test
    void allowRequest_afterWindow_shouldReset() throws InterruptedException {
        rateLimiter.allowRequest(clientKey, "/login", 1, 200);
        boolean blocked = rateLimiter.allowRequest(clientKey, "/login", 1, 200);
        assertFalse(blocked);

        Thread.sleep(250); // chờ cửa sổ hết hạn

        boolean allowedAgain = rateLimiter.allowRequest(clientKey, "/login", 1, 200);
        assertTrue(allowedAgain, "Request after window should be allowed again");
    }

    @Test
    void allowRequest_differentApiKeys_shouldHaveIndependentLimits() {
        boolean allowedLogin1 = rateLimiter.allowRequest(clientKey, "/login", 1, 1000);
        boolean blockedLogin2 = rateLimiter.allowRequest(clientKey, "/login", 1, 1000);

        boolean allowedSearch = rateLimiter.allowRequest(clientKey, "/search", 1, 1000);

        assertTrue(allowedLogin1);
        assertFalse(blockedLogin2);
        assertTrue(allowedSearch, "Different API key should not share the same limit");
    }
}