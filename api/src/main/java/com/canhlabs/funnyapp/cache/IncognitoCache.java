package com.canhlabs.funnyapp.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Short-lived cache of {@code user_settings.incognito_mode} (userId -> flag), read on every watch ping. Entries
 * expire 60 s after write; every settings save and account delete evicts the user, after commit when a
 * transaction is active.
 */
@Component
public class IncognitoCache {

    static final long TTL_SECONDS = 60;
    private static final int MAX_SIZE = 10_000;

    private final Cache<Long, Boolean> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(MAX_SIZE)
            .build();

    public boolean get(Long userId, Supplier<Boolean> loader) {
        try {
            return cache.get(userId, loader::get);
        } catch (UncheckedExecutionException | ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    public void evict(Long userId) {
        if (userId != null) {
            AfterCommit.run(() -> cache.invalidate(userId));
        }
    }
}
