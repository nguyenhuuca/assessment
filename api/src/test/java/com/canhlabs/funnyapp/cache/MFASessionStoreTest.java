 package com.canhlabs.funnyapp.cache;

 import org.junit.jupiter.api.BeforeEach;
 import org.junit.jupiter.api.Test;

 import java.util.Optional;

 import static org.junit.jupiter.api.Assertions.assertEquals;
 import static org.junit.jupiter.api.Assertions.assertTrue;

 class MFASessionStoreTest {

     private MFASessionStore store;
    private AppCache<String, String> defaultCache;

    @BeforeEach
    void setUp() {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.setType("guava");
        AppCacheFactory factory = new AppCacheFactory(cacheProperties);
        store = new MFASessionStoreImpl(factory.createDefaultCache());
        defaultCache = factory.createDefaultCache();
    }

    @Test
    void storeSession_shouldPutToCache() {
        store.storeSession("token1", "user1");
        String userId = String.valueOf(store.getUserId("token1").get());
        assertEquals("user1", userId);
    }

     @Test
     void getUserId_shouldReturnEmptyIfNotFound() {
         Optional<String> userId = store.getUserId("nonexistent");
         assertTrue(userId.isEmpty());
     }

     @Test
     void remove_shouldInvalidateSession() {
         store.storeSession("token2", "user2");
         store.remove("token2");
         Optional<String> userId = store.getUserId("token2");
         assertTrue(userId.isEmpty());
     }

     @Test
     void defaultCache_shouldInvalidateValue() {
         defaultCache.put("key2", "value2");
         defaultCache.invalidate("key2");
         Optional<String> value = defaultCache.get("key2");
         assertTrue(value.isEmpty());

         defaultCache.put("key2", "value2");
         defaultCache.invalidateAll();
         value = defaultCache.get("key2");
         assertTrue(value.isEmpty());

     }
}