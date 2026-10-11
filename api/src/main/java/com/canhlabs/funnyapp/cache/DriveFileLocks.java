package com.canhlabs.funnyapp.cache;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per Drive file claim shared by the sync cron and the admin force sync,
 * so the same file is never downloaded twice at the same time.
 */
@Component
public class DriveFileLocks {

    private final Set<String> held = ConcurrentHashMap.newKeySet();

    /** @return true if the caller now holds the file and must {@link #release} it. */
    public boolean tryClaim(String fileId) {
        return held.add(fileId);
    }

    public void release(String fileId) {
        held.remove(fileId);
    }

    public boolean isHeld(String fileId) {
        return held.contains(fileId);
    }
}
