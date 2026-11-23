package com.canhlabs.funnyapp.client;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Seam for starting child processes so tests can substitute a fake. Never goes through a shell. */
@FunctionalInterface
public interface ProcessFactory {
    Process start(List<String> command) throws IOException;

    /** Starts with extra environment variables added on top of the restricted env; unsupported by default. */
    default Process start(List<String> command, Map<String, String> extraEnv) throws IOException {
        if (extraEnv == null || extraEnv.isEmpty()) {
            return start(command);
        }
        throw new UnsupportedOperationException("extraEnv not supported by this ProcessFactory");
    }
}
