package com.canhlabs.funnyapp.client;

import java.io.IOException;
import java.util.List;

/** Seam for starting child processes so tests can substitute a fake. Never goes through a shell. */
@FunctionalInterface
public interface ProcessFactory {
    Process start(List<String> command) throws IOException;
}
