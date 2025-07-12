package com.canhlabs.funnyapp.cache;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class EmailCacheLimiterImpl implements EmailCacheLimiter {

    private final AppCache<String, Integer> cache;
    private static final String KEY_PREFIX = "email_preview_count_";

    public EmailCacheLimiterImpl(@Qualifier("emailLimiterCache") AppCache<String, Integer> cache) {
        this.cache = cache;
    }

    @Override
    public int getDailyCount(LocalDate date) {
        String key = KEY_PREFIX + date;
        return cache.get(key).orElse(0);
    }

    @Override
    public void incrementDailyCount(LocalDate date) {
        String key = KEY_PREFIX + date;
        int newCount = cache.get(key).orElse(0) + 1;
        cache.put(key, newCount);
    }
}
