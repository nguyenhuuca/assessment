package com.canhlabs.funnyapp.cache;

import com.canhlabs.funnyapp.dto.video.VideoDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedCacheTest {

    private FeedCache cache;
    private AtomicInteger loads;

    @BeforeEach
    void setUp() {
        cache = new FeedCache();
        loads = new AtomicInteger();
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private List<VideoDto> load() {
        loads.incrementAndGet();
        return new ArrayList<>(List.of(VideoDto.builder().id(1L).build()));
    }

    @Test
    void twoCallsWithinTtl_loadOnce() {
        cache.get(false, this::load);
        cache.get(false, this::load);

        assertThat(loads).hasValue(1);
    }

    @Test
    void invalidate_forcesReload() {
        cache.get(false, this::load);
        cache.invalidate();
        cache.get(false, this::load);

        assertThat(loads).hasValue(2);
    }

    @Test
    void flagValuesUseSeparateEntries() {
        cache.get(true, this::load);
        cache.get(false, this::load);
        cache.get(true, this::load);

        assertThat(loads).hasValue(2);
    }

    @Test
    void throwingLoader_isNotCached() {
        assertThatThrownBy(() -> cache.get(false, () -> {
            loads.incrementAndGet();
            throw new IllegalStateException("db down");
        })).isInstanceOf(IllegalStateException.class).hasMessage("db down");

        cache.get(false, this::load);

        assertThat(loads).hasValue(2);
    }

    @Test
    void cachedValue_isUnmodifiable() {
        List<VideoDto> value = cache.get(false, this::load);

        assertThatThrownBy(() -> value.add(VideoDto.builder().build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidate_insideTransaction_runsAfterCommit() {
        cache.get(false, this::load);
        TransactionSynchronizationManager.initSynchronization();

        cache.invalidate();
        cache.get(false, this::load);
        assertThat(loads).as("still cached before commit").hasValue(1);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        cache.get(false, this::load);

        assertThat(loads).hasValue(2);
    }
}
