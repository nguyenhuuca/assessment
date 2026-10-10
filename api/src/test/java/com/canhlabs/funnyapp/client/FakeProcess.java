package com.canhlabs.funnyapp.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Scriptable child process for tests: either finishes immediately or hangs until destroyed. */
public class FakeProcess extends Process {

    private final InputStream stdout;
    private final InputStream stderr;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile int exit;
    private volatile boolean destroyed;

    private FakeProcess(InputStream stdout, InputStream stderr, int exit, boolean finished) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exit = exit;
        if (finished) {
            done.countDown();
        }
    }

    public static FakeProcess finished(String out, String err, int exit) {
        return new FakeProcess(new ByteArrayInputStream(out.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(err.getBytes(StandardCharsets.UTF_8)), exit, true);
    }

    /** Emits {@code firstOut} then blocks until destroyForcibly() is called. */
    public static FakeProcess hanging(String firstOut) {
        FakeProcess[] self = new FakeProcess[1];
        InputStream blocking = new InputStream() {
            private final ByteArrayInputStream head = new ByteArrayInputStream(firstOut.getBytes(StandardCharsets.UTF_8));

            @Override
            public int read() throws IOException {
                int b = head.read();
                if (b >= 0) {
                    return b;
                }
                try {
                    self[0].done.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return -1;
            }
        };
        self[0] = new FakeProcess(blocking, new ByteArrayInputStream(new byte[0]), 0, false);
        return self[0];
    }

    public boolean wasDestroyed() {
        return destroyed;
    }

    @Override
    public OutputStream getOutputStream() {
        return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return stdout;
    }

    @Override
    public InputStream getErrorStream() {
        return stderr;
    }

    @Override
    public int waitFor() throws InterruptedException {
        done.await();
        return exit;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        return done.await(timeout, unit);
    }

    @Override
    public int exitValue() {
        return exit;
    }

    @Override
    public void destroy() {
        destroyForcibly();
    }

    @Override
    public Process destroyForcibly() {
        destroyed = true;
        exit = 137;
        done.countDown();
        return this;
    }
}
