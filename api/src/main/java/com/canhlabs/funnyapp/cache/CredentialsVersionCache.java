package com.canhlabs.funnyapp.cache;

import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Short-lived cache of {@code users.credentials_version} (userId -> version), used by the JWT filter to reject
 * tokens issued before a password change (ADR-0018 option D2). Entries expire 60 s after write, so other
 * instances converge within a minute; the instance that handles the change evicts immediately.
 */
@Component
public class CredentialsVersionCache {

    static final long TTL_SECONDS = 60;
    private static final int MAX_SIZE = 50_000;

    private final UserRepo userRepo;
    private final Cache<Long, Integer> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(MAX_SIZE)
            .build();

    public CredentialsVersionCache(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    /**
     * @return the current credentials version of the user; 0 when the user cannot be found
     */
    public int currentVersion(Long userId) {
        if (userId == null) {
            return 0;
        }
        Integer cached = cache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }
        User user = userRepo.findAllById(userId);
        int version = user != null ? user.getCredentialsVersion() : 0;
        cache.put(userId, version);
        return version;
    }

    public void evict(Long userId) {
        if (userId != null) {
            cache.invalidate(userId);
        }
    }
}
