package com.canhlabs.funnyapp.client;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class DefaultProcessFactory implements ProcessFactory {

    /** Only what the tool needs: the app env holds DB_PASS, JWT_SECRET, OAuth secrets (CWE-526). */
    static final Set<String> ALLOWED_ENV = Set.of("PATH", "HOME", "LANG", "LC_ALL", "TMPDIR", "TZ", "SYSTEMROOT", "TEMP", "TMP");

    @Override
    public Process start(List<String> command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        restrictEnvironment(pb.environment());
        pb.redirectInput(ProcessBuilder.Redirect.from(new File(isWindows() ? "NUL" : "/dev/null")));
        return pb.start();
    }

    static void restrictEnvironment(Map<String, String> env) {
        env.keySet().removeIf(k -> !ALLOWED_ENV.contains(k.toUpperCase(Locale.ROOT)));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
