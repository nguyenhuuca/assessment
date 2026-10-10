package com.canhlabs.funnyapp.client;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Cooperative cancellation flag; a running step can register a hook (e.g. destroy the child process). */
public class CancelToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<Runnable> hook = new AtomicReference<>();

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            Runnable h = hook.get();
            if (h != null) {
                h.run();
            }
        }
    }

    /** Registers the hook run on cancel; runs immediately when already cancelled. */
    public void onCancel(Runnable newHook) {
        hook.set(newHook);
        if (cancelled.get()) {
            newHook.run();
        }
    }

    public void clearHook() {
        hook.set(null);
    }
}
