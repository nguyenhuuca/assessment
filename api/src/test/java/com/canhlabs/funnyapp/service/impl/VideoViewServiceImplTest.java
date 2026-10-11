package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.AppCache;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoViewServiceImplTest {

    private VideoSourceRepository repo;
    private VideoViewServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(VideoSourceRepository.class);
        service = new VideoViewServiceImpl(repo, new MapCache());
    }

    @Test
    void firstView_isCounted() {
        when(repo.incrementViewCount(7L)).thenReturn(1);

        assertThat(service.recordView(7L, "IP:1.2.3.4")).isTrue();
        verify(repo).incrementViewCount(7L);
    }

    @Test
    void sameViewerSameVideo_countedOnceInWindow() {
        when(repo.incrementViewCount(7L)).thenReturn(1);

        service.recordView(7L, "USER:a@test.com");
        assertThat(service.recordView(7L, "USER:a@test.com")).isFalse();

        verify(repo, times(1)).incrementViewCount(7L);
    }

    @Test
    void otherViewerOrOtherVideo_countedSeparately() {
        when(repo.incrementViewCount(7L)).thenReturn(1);
        when(repo.incrementViewCount(8L)).thenReturn(1);

        service.recordView(7L, "USER:a@test.com");
        assertThat(service.recordView(7L, "USER:b@test.com")).isTrue();
        assertThat(service.recordView(8L, "USER:a@test.com")).isTrue();

        verify(repo, times(2)).incrementViewCount(7L);
        verify(repo, times(1)).incrementViewCount(8L);
    }

    @Test
    void unknownOrHiddenVideo_notCountedAndNotRemembered() {
        when(repo.incrementViewCount(9L)).thenReturn(0);

        assertThat(service.recordView(9L, "IP:1.2.3.4")).isFalse();
        assertThat(service.recordView(9L, "IP:1.2.3.4")).isFalse();

        // key was dropped, so the DB is asked again rather than silently deduped
        verify(repo, times(2)).incrementViewCount(9L);
    }

    /** Minimal AppCache over a ConcurrentHashMap (the Guava impl is package-private). */
    private static final class MapCache implements AppCache<String, Boolean> {
        private final ConcurrentHashMap<String, Boolean> map = new ConcurrentHashMap<>();
        @Override public void put(String k, Boolean v) { map.put(k, v); }
        @Override public Optional<Boolean> get(String k) { return Optional.ofNullable(map.get(k)); }
        @Override public void invalidate(String k) { map.remove(k); }
        @Override public void invalidateAll() { map.clear(); }
        @Override public Boolean get(String k, Callable<? extends Boolean> loader) { return map.get(k); }
        @Override public Map<String, Boolean> asMap() { return map; }
    }
}
