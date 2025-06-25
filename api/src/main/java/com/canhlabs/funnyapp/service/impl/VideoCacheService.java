package com.canhlabs.funnyapp.service.impl;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.canhlabs.funnyapp.share.AppConstant.CACHE_DIR;
import static com.canhlabs.funnyapp.share.AppConstant.CACHE_SIZE;

@Service
public class VideoCacheService {

    public File getCacheFile(String fileId) {
        return new File(CACHE_DIR + fileId + ".cache");
    }

    public boolean hasCache(String fileId) {
        File file = getCacheFile(fileId);
        return file.exists() && file.length() == CACHE_SIZE;
    }

    public InputStream getCache(String fileId) throws IOException {
        return new FileInputStream(getCacheFile(fileId));
    }

    public void saveToCache(String fileId, InputStream inputStream) throws IOException {
        File cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        File file = getCacheFile(fileId);
        try (OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            long remaining = CACHE_SIZE;
            int bytesRead;

            while (remaining > 0 && (bytesRead = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                out.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
        } finally {
            inputStream.close();
        }
    }
}