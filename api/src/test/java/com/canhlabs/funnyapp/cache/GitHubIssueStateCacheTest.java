package com.canhlabs.funnyapp.cache;

import com.canhlabs.funnyapp.client.GitHubIssueClient.IssueState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubIssueStateCacheTest {

    private final GitHubIssueStateCache cache = new GitHubIssueStateCache();

    @Test
    void storesOpenAndClosed() {
        cache.put(1, IssueState.OPEN);
        cache.put(2, IssueState.CLOSED);

        assertThat(cache.get(1)).contains(IssueState.OPEN);
        assertThat(cache.get(2)).contains(IssueState.CLOSED);
        assertThat(cache.get(3)).isEmpty();
    }

    @Test
    void unknownIsNotStored() {
        cache.put(1, IssueState.UNKNOWN);
        cache.put(2, null);

        assertThat(cache.get(1)).isEmpty();
        assertThat(cache.get(2)).isEmpty();
    }

    @Test
    void unknownDoesNotOverwriteAKnownState() {
        cache.put(1, IssueState.OPEN);
        cache.put(1, IssueState.UNKNOWN);

        assertThat(cache.get(1)).contains(IssueState.OPEN);
    }
}
