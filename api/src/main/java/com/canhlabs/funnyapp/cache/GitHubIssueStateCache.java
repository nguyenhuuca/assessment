package com.canhlabs.funnyapp.cache;

import com.canhlabs.funnyapp.client.GitHubIssueClient.IssueState;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Short-lived cache of GitHub issue states (issue number -> state); UNKNOWN is never stored. */
@Component
public class GitHubIssueStateCache {

    static final long TTL_MINUTES = 5;
    private static final int MAX_SIZE = 500;

    private final Cache<Integer, IssueState> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(MAX_SIZE)
            .build();

    public Optional<IssueState> get(int number) {
        return Optional.ofNullable(cache.getIfPresent(number));
    }

    public void put(int number, IssueState state) {
        if (state != null && state != IssueState.UNKNOWN) {
            cache.put(number, state);
        }
    }
}
