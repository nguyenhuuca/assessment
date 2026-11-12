package com.canhlabs.funnyapp.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserPremiumTest {
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    @Test
    void isPremium_falseWhenNeverBought() {
        assertThat(User.builder().build().isPremium(NOW)).isFalse();
    }

    @Test
    void isPremium_falseWhenExpired() {
        User user = User.builder().premiumUntil(NOW.minusSeconds(1)).build();
        assertThat(user.isPremium(NOW)).isFalse();
    }

    @Test
    void isPremium_falseExactlyAtExpiry() {
        User user = User.builder().premiumUntil(NOW).build();
        assertThat(user.isPremium(NOW)).isFalse();
    }

    @Test
    void isPremium_trueWhenInFuture() {
        User user = User.builder().premiumUntil(NOW.plusSeconds(1)).build();
        assertThat(user.isPremium(NOW)).isTrue();
    }
}
