package com.canhlabs.funnyapp.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailCacheLimiterImplTest {

    private static final String KEY_PREFIX = "email_preview_count_";

    @Mock
    private AppCache<String, Integer> cache;

    private EmailCacheLimiterImpl limiter;

    @BeforeEach
    void setUp() {
        limiter = new EmailCacheLimiterImpl(cache);
    }

    // ── getDailyCount ─────────────────────────────────────────────────────────

    @Test
    void getDailyCount_whenCacheHasValue_returnsStoredCount() {
        LocalDate date = LocalDate.of(2026, 5, 29);
        String expectedKey = KEY_PREFIX + date;
        when(cache.get(expectedKey)).thenReturn(Optional.of(7));

        int result = limiter.getDailyCount(date);

        assertEquals(7, result);
        verify(cache).get(expectedKey);
    }

    @Test
    void getDailyCount_whenCacheIsEmpty_returnsZero() {
        LocalDate date = LocalDate.of(2026, 5, 29);
        String expectedKey = KEY_PREFIX + date;
        when(cache.get(expectedKey)).thenReturn(Optional.empty());

        int result = limiter.getDailyCount(date);

        assertEquals(0, result);
        verify(cache).get(expectedKey);
    }

    @Test
    void getDailyCount_usesDateAsPartOfKey() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        String expectedKey = "email_preview_count_2026-01-15";
        when(cache.get(expectedKey)).thenReturn(Optional.of(3));

        int result = limiter.getDailyCount(date);

        assertEquals(3, result);
        verify(cache).get(expectedKey);
    }

    @Test
    void getDailyCount_differentDates_useDifferentKeys() {
        LocalDate dateA = LocalDate.of(2026, 5, 28);
        LocalDate dateB = LocalDate.of(2026, 5, 29);
        when(cache.get(KEY_PREFIX + dateA)).thenReturn(Optional.of(5));
        when(cache.get(KEY_PREFIX + dateB)).thenReturn(Optional.of(9));

        assertEquals(5, limiter.getDailyCount(dateA));
        assertEquals(9, limiter.getDailyCount(dateB));
    }

    // ── incrementDailyCount ───────────────────────────────────────────────────

    @Test
    void incrementDailyCount_whenCacheIsEmpty_storesOne() {
        LocalDate date = LocalDate.of(2026, 5, 29);
        String expectedKey = KEY_PREFIX + date;
        when(cache.get(expectedKey)).thenReturn(Optional.empty());

        limiter.incrementDailyCount(date);

        verify(cache).get(expectedKey);
        verify(cache).put(expectedKey, 1);
    }

    @Test
    void incrementDailyCount_whenCacheHasExistingValue_incrementsByOne() {
        LocalDate date = LocalDate.of(2026, 5, 29);
        String expectedKey = KEY_PREFIX + date;
        when(cache.get(expectedKey)).thenReturn(Optional.of(4));

        limiter.incrementDailyCount(date);

        verify(cache).get(expectedKey);
        verify(cache).put(expectedKey, 5);
    }

    @Test
    void incrementDailyCount_calledMultipleTimes_accumulatesCorrectly() {
        LocalDate date = LocalDate.of(2026, 5, 29);
        String key = KEY_PREFIX + date;

        // First call: cache is empty -> stores 1
        when(cache.get(key)).thenReturn(Optional.empty());
        limiter.incrementDailyCount(date);
        verify(cache).put(key, 1);

        // Second call: cache now has 1 -> stores 2
        when(cache.get(key)).thenReturn(Optional.of(1));
        limiter.incrementDailyCount(date);
        verify(cache).put(key, 2);
    }

    @Test
    void incrementDailyCount_usesCorrectKeyFormat() {
        LocalDate date = LocalDate.of(2026, 3, 5);
        String expectedKey = "email_preview_count_2026-03-05";
        when(cache.get(expectedKey)).thenReturn(Optional.of(0));

        limiter.incrementDailyCount(date);

        verify(cache).put(eq(expectedKey), eq(1));
    }

    // ── integration-style: getDailyCount reflects increments via real cache ────

    @Test
    void integration_realCache_getAndIncrementWorkTogether() {
        CacheProperties props = new CacheProperties();
        props.setType("guava");
        AppCacheFactory factory = new AppCacheFactory(props);
        AppCache<String, Integer> realCache = factory.createDefaultCache();
        EmailCacheLimiterImpl realLimiter = new EmailCacheLimiterImpl(realCache);

        LocalDate date = LocalDate.of(2026, 5, 29);

        assertEquals(0, realLimiter.getDailyCount(date));

        realLimiter.incrementDailyCount(date);
        assertEquals(1, realLimiter.getDailyCount(date));

        realLimiter.incrementDailyCount(date);
        assertEquals(2, realLimiter.getDailyCount(date));
    }

    @Test
    void integration_realCache_differentDatesAreIndependent() {
        CacheProperties props = new CacheProperties();
        props.setType("guava");
        AppCacheFactory factory = new AppCacheFactory(props);
        AppCache<String, Integer> realCache = factory.createDefaultCache();
        EmailCacheLimiterImpl realLimiter = new EmailCacheLimiterImpl(realCache);

        LocalDate today = LocalDate.of(2026, 5, 29);
        LocalDate yesterday = LocalDate.of(2026, 5, 28);

        realLimiter.incrementDailyCount(today);
        realLimiter.incrementDailyCount(today);
        realLimiter.incrementDailyCount(yesterday);

        assertEquals(2, realLimiter.getDailyCount(today));
        assertEquals(1, realLimiter.getDailyCount(yesterday));
    }
}
