package com.canhlabs.funnyapp.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncognitoCacheTest {

    private IncognitoCache cache;
    private AtomicInteger loads;

    @BeforeEach
    void setUp() {
        cache = new IncognitoCache();
        loads = new AtomicInteger();
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private boolean load(boolean value) {
        loads.incrementAndGet();
        return value;
    }

    @Test
    void repeatedReads_loadOnce() {
        assertThat(cache.get(1L, () -> load(true))).isTrue();
        assertThat(cache.get(1L, () -> load(false))).isTrue();

        assertThat(loads).hasValue(1);
    }

    @Test
    void evict_forcesReloadOfThatUserOnly() {
        cache.get(1L, () -> load(true));
        cache.get(2L, () -> load(true));

        cache.evict(1L);

        assertThat(cache.get(1L, () -> load(false))).isFalse();
        cache.get(2L, () -> load(false));
        assertThat(loads).hasValue(3);
    }

    @Test
    void evictNull_isIgnored() {
        cache.evict(null);
    }

    @Test
    void throwingLoader_propagatesAndIsNotCached() {
        assertThatThrownBy(() -> cache.get(1L, () -> {
            throw new IllegalStateException("db down");
        })).isInstanceOf(IllegalStateException.class).hasMessage("db down");

        assertThat(cache.get(1L, () -> load(true))).isTrue();
    }

    @Test
    void evict_insideTransaction_runsAfterCommit() {
        cache.get(1L, () -> load(true));
        TransactionSynchronizationManager.initSynchronization();

        cache.evict(1L);
        assertThat(cache.get(1L, () -> load(false))).as("still cached before commit").isTrue();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(cache.get(1L, () -> load(false))).isFalse();
    }
}
