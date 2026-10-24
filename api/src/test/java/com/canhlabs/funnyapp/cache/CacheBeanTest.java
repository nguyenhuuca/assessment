package com.canhlabs.funnyapp.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheBeanTest {

    @Test
    void videoCache_evictsByBytesNotByEntryCount() {
        CacheProperties props = mock(CacheProperties.class);
        when(props.getType()).thenReturn("guava");
        AppCache<String, byte[]> videoCache = new CacheBean().videoCache(new AppCacheFactory(props));

        int entries = 48; // 48 MB in total, above the 32 MB limit and far below the old 2000 entries
        for (int i = 0; i < entries; i++) {
            videoCache.put("k" + i, new byte[1024 * 1024]);
        }

        long totalBytes = videoCache.asMap().values().stream().mapToLong(b -> b.length).sum();
        assertThat(totalBytes).isLessThanOrEqualTo(CacheBean.VIDEO_CACHE_MAX_BYTES);
        assertThat(videoCache.asMap()).hasSizeLessThan(entries);
    }
}
