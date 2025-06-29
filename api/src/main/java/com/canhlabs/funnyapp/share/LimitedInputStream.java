package com.canhlabs.funnyapp.share;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class LimitedInputStream extends InputStream {
    private final InputStream in;
    private final long maxBytes;
    private long bytesRead = 0;
    private final RandomAccessFile raf;

    public LimitedInputStream(InputStream in, long maxBytes, RandomAccessFile raf) {
        this.in = in;
        this.maxBytes = maxBytes;
        this.raf = raf;
    }

    @Override
    public int read() throws IOException {
        if (bytesRead >= maxBytes) return -1;
        int result = in.read();
        if (result != -1) bytesRead++;
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (bytesRead >= maxBytes) return -1;
        long remaining = maxBytes - bytesRead;
        int toRead = (int) Math.min(len, remaining);
        int result = in.read(b, off, toRead);
        if (result != -1) bytesRead += result;
        return result;
    }

    @Override
    public void close() throws IOException {
        in.close();
        raf.close(); // important: close RandomAccessFile
    }
}