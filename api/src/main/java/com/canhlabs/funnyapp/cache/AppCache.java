package com.canhlabs.funnyapp.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

public interface AppCache<K, V> {
    void put(K key, V value);

    Optional<V> get(K key);

    void invalidate(K key);

    void invalidateAll();

    V get(K key, Callable<? extends V> loader);
    Map<K, V> asMap();
}
