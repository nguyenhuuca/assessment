package com.canhlabs.funnyapp.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppCacheFactoryTest {

    @Mock
    private CacheProperties cacheProperties;

    private AppCacheFactory factory;

    @BeforeEach
    void setUp() {
        factory = new AppCacheFactory(cacheProperties);
    }

    // ── createCache ───────────────────────────────────────────────────────────

    @Test
    void createCache_guavaType_returnsNonNullAppCache() {
        when(cacheProperties.getType()).thenReturn("guava");

        AppCache<String, String> cache = factory.createCache(10L, 500L);

        assertThat(cache).isNotNull();
    }

    @Test
    void createCache_guavaTypeIsCaseInsensitive_returnsNonNullAppCache() {
        when(cacheProperties.getType()).thenReturn("GUAVA");

        AppCache<String, byte[]> cache = factory.createCache(5L, 100L);

        assertThat(cache).isNotNull();
    }

    @Test
    void createCache_unknownType_throwsIllegalArgumentException() {
        when(cacheProperties.getType()).thenReturn("redis");

        assertThatThrownBy(() -> factory.createCache(10L, 500L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("redis");
    }

    // ── createDefaultCache ────────────────────────────────────────────────────

    @Test
    void createDefaultCache_guavaType_returnsNonNullAppCache() {
        when(cacheProperties.getType()).thenReturn("guava");

        AppCache<String, Object> cache = factory.createDefaultCache();

        assertThat(cache).isNotNull();
    }

    @Test
    void createDefaultCache_unknownType_throwsIllegalArgumentException() {
        when(cacheProperties.getType()).thenReturn("memcached");

        assertThatThrownBy(() -> factory.createDefaultCache())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memcached");
    }

    // ── createCacheByWeight ───────────────────────────────────────────────────

    @Test
    void createCacheByWeight_guavaType_returnsNonNullAppCache() {
        when(cacheProperties.getType()).thenReturn("guava");

        AppCache<String, byte[]> cache = factory.createCacheByWeight(10L, 150_000_000L);

        assertThat(cache).isNotNull();
    }

    @Test
    void createCacheByWeight_unknownType_throwsIllegalArgumentException() {
        when(cacheProperties.getType()).thenReturn("hazelcast");

        assertThatThrownBy(() -> factory.createCacheByWeight(10L, 150_000_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hazelcast");
    }

    // ── functional smoke – returned cache is actually usable ─────────────────

    @Test
    void createCache_returnedCacheIsOperational() {
        when(cacheProperties.getType()).thenReturn("guava");

        AppCache<String, String> cache = factory.createCache(1L, 10L);
        cache.put("k", "v");

        assertThat(cache.get("k")).isPresent().contains("v");
    }

    @Test
    void createDefaultCache_returnedCacheIsOperational() {
        when(cacheProperties.getType()).thenReturn("guava");

        AppCache<String, Integer> cache = factory.createDefaultCache();
        cache.put("num", 42);

        assertThat(cache.get("num")).isPresent().contains(42);
    }

    @Test
    void createCacheByWeight_returnedCacheIsOperational() {
        when(cacheProperties.getType()).thenReturn("guava");

        AppCache<String, byte[]> cache = factory.createCacheByWeight(1L, 1024L * 1024L);
        byte[] data = new byte[]{1, 2, 3};
        cache.put("chunk", data);

        assertThat(cache.get("chunk")).isPresent().contains(data);
    }
}
