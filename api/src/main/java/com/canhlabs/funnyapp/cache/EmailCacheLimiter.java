package com.canhlabs.funnyapp.cache;

import java.time.LocalDate;

public interface EmailCacheLimiter {
    int getDailyCount(LocalDate date);
    void incrementDailyCount(LocalDate date);
}
