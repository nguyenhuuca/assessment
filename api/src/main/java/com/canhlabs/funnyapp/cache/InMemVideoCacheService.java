package com.canhlabs.funnyapp.cache;

import com.canhlabs.funnyapp.share.AppConstant;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class InMemVideoCacheService {
    private final AppCache<String, byte[]> previewCache;

    public InMemVideoCacheService(AppCacheFactory appCacheFactory) {

        this.previewCache = appCacheFactory.createCache(30, 500);
    }

    public boolean hasCache(String fileId) {
        return previewCache.get(fileId).isPresent();
    }

    public InputStream getCache(String fileId) {
        return previewCache.get(fileId)
                .map(ByteArrayInputStream::new)
                .orElseThrow(() -> new RuntimeException("Cache not found for " + fileId));
    }

    public void saveToCache(String fileId, InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long remaining = AppConstant.CACHE_SIZE;
        int bytesRead;

        while (remaining > 0 && (bytesRead = inputStream.read(buffer, 0, (int)Math.min(buffer.length, remaining))) != -1) {
            output.write(buffer, 0, bytesRead);
            remaining -= bytesRead;
        }

        inputStream.close();
        previewCache.put(fileId, output.toByteArray());
    }
}
