package com.canhlabs.funnyapp.cache;

import java.util.Optional;

public interface AppCache<K, V> {
    void put(K key, V value);
    Optional<V> get(K key);
    void invalidate(K key);
    void invalidateAll();
}
