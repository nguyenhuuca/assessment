package com.canhlabs.funnyapp.utils;

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
    public long skip(long n) throws IOException {
        long remaining = maxBytes - bytesRead;
        long toSkip = Math.min(n, remaining);
        long skipped = in.skip(toSkip);
        bytesRead += skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(in.available(), maxBytes - bytesRead);
    }

    @Override
    public void close() throws IOException {
        try {
            in.close();
        } finally {
            raf.close(); // close the RandomAccessFile as well
        }
    }

    @Override
    public void mark(int readlimit) {
        in.mark(readlimit);
    }

    @Override
    public  void reset() throws IOException {
        in.reset();
        bytesRead = 0; // reset bytesRead to 0 when resetting
    }

    @Override
    public boolean markSupported() {
        return in.markSupported();
    }
}
