package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.service.VideoCacheService;
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
public class VideoCacheServiceImpl implements VideoCacheService {

    @Override
    public File getCacheFile(String fileId) {
        return new File(CACHE_DIR + fileId + ".cache");
    }

    @Override
    public boolean hasCache(String fileId, long requiredBytes) {
        File file = getCacheFile(fileId);
        return file.exists();
    }

    @Override
    public InputStream getCache(String fileId) throws IOException {
        return new FileInputStream(getCacheFile(fileId));
    }

    @Override
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

    @Override
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
            byte[] buffer = new byte[64 * 1024];
            long remaining = AppConstant.CACHE_SIZE;
            int bytesRead;
            while (remaining > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                out.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
            out.flush();
        }
    }

    @Override
    public boolean hasChunk(String fileId, long start, long end) {
        File chunk = getChunkFile(fileId, start, end);
        return chunk.exists() && chunk.length() == (end - start + 1);
    }

    @Override
    public InputStream getChunk(String fileId, long start, long end) throws IOException {
        File chunk = getChunkFile(fileId, start, end);
        if (!chunk.exists()) throw new FileNotFoundException("Chunk not found");
        return new BufferedInputStream(new FileInputStream(chunk));
    }

    @Override
    public void saveChunk(String fileId, long start, long end, InputStream stream) throws IOException {
        File chunk = getChunkFile(fileId, start, end);
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(chunk))) {
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;
            long total = 0;
            while ((bytesRead = stream.read(buffer)) != -1 && total <= (end - start)) {
                out.write(buffer, 0, bytesRead);
                total += bytesRead;
            }
        }
    }


    /**
     * Helper method to get the chunk file based on fileId and byte range.
     *
     * @param fileId The unique identifier for the video file.
     * @param start  The starting byte of the chunk.
     * @param end    The ending byte of the chunk.
     * @return The File object representing the chunk file.
     */

    private File getChunkFile(String fileId, long start, long end) {
        File dir = new File(CACHE_DIR, fileId);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, start + "-" + end + ".cache");
    }
}