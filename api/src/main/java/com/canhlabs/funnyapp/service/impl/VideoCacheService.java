package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.share.AppConstant;
import com.canhlabs.funnyapp.share.LimitedInputStream;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import static com.canhlabs.funnyapp.share.AppConstant.CACHE_DIR;

@Service
public class VideoCacheService {

    public File getCacheFile(String fileId) {
        return new File(CACHE_DIR + fileId + ".cache");
    }

    public boolean hasCache(String fileId, long requiredBytes) {
        File file = getCacheFile(fileId);
        return file.exists();
    }

    public InputStream getCache(String fileId) throws IOException {
        return new FileInputStream(getCacheFile(fileId));
    }

    public InputStream getCache(String fileId, long start, long end) throws IOException {
        File file = new File(CACHE_DIR + fileId + ".cache");

        if (!file.exists()) {
            throw new FileNotFoundException("Cache not found for file: " + fileId);
        }

        long length = end - start + 1;
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek(start);

        return new LimitedInputStream(new FileInputStream(raf.getFD()), length, raf) {
        };
    }

    public void saveToCache(String fileId, InputStream inputStream) throws IOException {
        File cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        File file = getCacheFile(fileId);

        try (inputStream;
             BufferedInputStream in = new BufferedInputStream(inputStream, 1024 * 1024);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file), 1024 * 1024)
        ) {
            byte[] buffer = new byte[1024 * 1024];
            long remaining = AppConstant.CACHE_SIZE;
            int bytesRead;
            while (remaining > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                out.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
            out.flush();
        }
    }
}