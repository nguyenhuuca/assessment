package com.canhlabs.funnyapp.client;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.exception.ImportErrorCode;
import com.canhlabs.funnyapp.exception.ImportException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs yt-dlp as a child process (argument list, never a shell) with the hardened options of ADR-0019 D4.
 * Raw tool output is only logged (bounded) and mapped to an error code; it never reaches the client.
 */
@Slf4j
@Component
public class YtDlpClientImpl implements YtDlpClient {

    static final String PROGRESS_TEMPLATE =
            "download:%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s";
    static final String FORMAT = "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720]/b";
    static final int TAIL_LINES = 20;
    static final String EXTRACTORS = "youtube,youtube:.*,facebook,facebook:.*";
    /** "242K views · 5.4K reactions | " prefix Facebook puts in front of reel titles. */
    private static final Pattern FB_STATS_PREFIX = Pattern.compile(
            "^\\s*[\\d.,]+\\s*[KMB]?\\s+views?\\s*·\\s*[\\d.,]+\\s*[KMB]?\\s+reactions?\\s*\\|\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HASHTAG = Pattern.compile("(?U)#[\\w\\p{So}]+");
    static final int MAX_JSON_CHARS = 16 * 1024 * 1024;
    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern PROGRESS =
            Pattern.compile("^(?:download:)?(\\d+|NA|None)\\|(\\d+|NA|None)\\|(\\d+(?:\\.\\d+)?|NA|None)$");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration SIZE_POLL = Duration.ofSeconds(2);
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final Pattern SIZE = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*([KMGT]?)i?B?$", Pattern.CASE_INSENSITIVE);
    /** Preview calls are outside the worker semaphore: cap concurrent metadata processes (CWE-770). */
    private final Semaphore metadataPermits = new Semaphore(2);

    private final AppProperties props;
    private final ProcessFactory processFactory;

    public YtDlpClientImpl(AppProperties props, ProcessFactory processFactory) {
        this.props = props;
        this.processFactory = processFactory;
    }

    @Override
    public VideoMetadata fetchMetadata(String url) {
        List<String> cmd = baseArgs();
        cmd.add("-J");
        cmd.add("--skip-download");
        addCookies(cmd);
        cmd.add("--");
        cmd.add(url);

        StringBuilder json = new StringBuilder();
        Duration timeout = props.getVideoImport().getMetadataTimeout();
        try {
            if (!metadataPermits.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ImportException(ImportErrorCode.TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImportException(ImportErrorCode.CANCELLED, e);
        }
        RunResult result;
        try {
            result = run(cmd, timeout, new CancelToken(), line -> {
                if (json.length() < MAX_JSON_CHARS) {
                    json.append(line);
                }
            }, null, 0);
        } finally {
            metadataPermits.release();
        }
        failIfNotSuccessful(result, "metadata");
        JsonNode node;
        try {
            node = MAPPER.readTree(json.toString());
        } catch (IOException e) {
            log.warn("yt-dlp metadata output was not valid JSON");
            throw new ImportException(ImportErrorCode.EXTRACTOR_ERROR, e);
        }
        if (node == null || !node.isObject() || !node.hasNonNull("id")) {
            // yt-dlp prints "null" and exits 0 when no extractor accepts the URL
            String code = mapError(String.join("\n", result.stderrTail()));
            log.warn("yt-dlp returned no metadata -> {}", code);
            throw new ImportException(code);
        }
        {
            String id = node.path("id").asText(null);
            String title = cleanTitle(node.path("extractor_key").asText(""), node.path("title").asText(null));
            Long duration = node.hasNonNull("duration") ? (long) node.get("duration").asDouble() : null;
            return new VideoMetadata(id, title, duration);
        }
    }

    @Override
    public Path download(String url, Path jobDir, ProgressListener listener, CancelToken cancel) {
        try {
            Files.createDirectories(jobDir);
        } catch (IOException e) {
            throw new ImportException(ImportErrorCode.INTERNAL_ERROR, e);
        }
        AppProperties.VideoImport cfg = props.getVideoImport();
        List<String> cmd = baseArgs();
        cmd.add("--newline");
        cmd.add("--progress-template");
        cmd.add(PROGRESS_TEMPLATE);
        cmd.add("-f");
        cmd.add(FORMAT);
        cmd.add("--merge-output-format");
        cmd.add("mp4");
        cmd.add("--max-filesize");
        cmd.add(cfg.getMaxFileSize());
        cmd.add("--match-filter");
        cmd.add("!is_live");
        cmd.add("-o");
        cmd.add(jobDir.resolve("video.%(ext)s").toString());
        addCookies(cmd);
        cmd.add("--");
        cmd.add(url);

        Deque<String> outTail = new ConcurrentLinkedDeque<>();
        RunResult result = run(cmd, cfg.getDownloadTimeout(), cancel, line -> {
            long[] p = parseProgress(line);
            if (p != null) {
                if (listener != null) {
                    listener.onProgress(p[0], p[1]);
                }
            } else if (!line.isBlank()) {
                addTail(outTail, line);
            }
        }, jobDir, parseSize(cfg.getMaxFileSize()));
        String diagnostics = String.join("\n", result.stderrTail) + "\n" + String.join("\n", outTail);
        failIfNotSuccessful(result, diagnostics);

        Path file = findMedia(jobDir);
        if (file == null) {
            // yt-dlp exits 0 when --max-filesize skips the video
            String code = mapError(diagnostics);
            throw new ImportException(code);
        }
        return file;
    }

    @Override
    public Optional<String> version() {
        List<String> cmd = new ArrayList<>();
        cmd.add(props.getVideoImport().getYtDlpPath());
        cmd.add("--version");
        StringBuilder out = new StringBuilder();
        try {
            RunResult r = run(cmd, VERSION_TIMEOUT, new CancelToken(), out::append, null, 0);
            if (r.exitCode == 0 && !out.isEmpty()) {
                return Optional.of(out.toString().trim());
            }
        } catch (ImportException e) {
            log.debug("yt-dlp version check failed: {}", e.getErrorCode());
        }
        return Optional.empty();
    }

    // ── argument building ─────────────────────────────────────────────────────

    private List<String> baseArgs() {
        List<String> cmd = new ArrayList<>();
        cmd.add(props.getVideoImport().getYtDlpPath());
        cmd.add("--ignore-config");
        cmd.add("--no-playlist");
        cmd.add("--use-extractors");
        // sub-extractors (facebook:reel, youtube:tab…) are separate names; regexes match the full name
        cmd.add(EXTRACTORS);
        cmd.add("--no-cache-dir");
        cmd.add("--socket-timeout");
        cmd.add("30");
        return cmd;
    }

    private void addCookies(List<String> cmd) {
        String cookies = props.getVideoImport().getCookiesPath();
        if (cookies != null && !cookies.isBlank()) {
            cmd.add("--cookies");
            cmd.add(cookies);
        }
    }

    // ── process execution ─────────────────────────────────────────────────────

    private record RunResult(int exitCode, boolean timedOut, boolean cancelled, boolean tooLarge, List<String> stderrTail) {
    }

    /**
     * @param watchDir when non-null, its total size is polled and the process killed above {@code maxBytes}
     *                 (--max-filesize is per format and not enforced when the size is unknown)
     */
    private RunResult run(List<String> cmd, Duration timeout, CancelToken cancel, Consumer<String> stdoutLine,
                          Path watchDir, long maxBytes) {
        if (cancel.isCancelled()) {
            throw new ImportException(ImportErrorCode.CANCELLED);
        }
        Process process;
        try {
            process = processFactory.start(cmd);
        } catch (IOException e) {
            log.warn("Cannot start yt-dlp: {}", e.getClass().getSimpleName());
            throw new ImportException(ImportErrorCode.NOT_INSTALLED, e);
        }

        Deque<String> stderrTail = new ConcurrentLinkedDeque<>();
        AtomicBoolean timedOut = new AtomicBoolean();
        AtomicBoolean tooLarge = new AtomicBoolean();
        Thread errReader = Thread.ofVirtual().start(() -> drainLines(process.getErrorStream(), line -> addTail(stderrTail, line)));
        Thread watchdog = Thread.ofVirtual().start(() -> {
            try {
                long deadline = System.nanoTime() + timeout.toNanos();
                boolean watchSize = watchDir != null && maxBytes > 0;
                long step = watchSize ? SIZE_POLL.toMillis() : timeout.toMillis();
                while (true) {
                    long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                    if (left <= 0) {
                        timedOut.set(true);
                        kill(process);
                        return;
                    }
                    if (process.waitFor(Math.min(step, left), TimeUnit.MILLISECONDS)) {
                        return;
                    }
                    if (watchSize && dirSize(watchDir) > maxBytes) {
                        tooLarge.set(true);
                        kill(process);
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        cancel.onCancel(() -> kill(process));

        int exit;
        try {
            drainLines(process.getInputStream(), stdoutLine);
            exit = process.waitFor();
            errReader.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            kill(process);
            throw new ImportException(ImportErrorCode.CANCELLED, e);
        } finally {
            cancel.clearHook();
            watchdog.interrupt();
        }
        return new RunResult(exit, timedOut.get(), cancel.isCancelled(), tooLarge.get(), new ArrayList<>(stderrTail));
    }

    /** Kills ffmpeg and other children too: an orphan keeps the stdout pipe open and blocks drainLines. */
    private static void kill(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (UnsupportedOperationException e) {
            // Process implementations without a native handle; the parent is still killed below
        } finally {
            process.destroyForcibly();
        }
    }

    static long dirSize(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).mapToLong(f -> {
                try {
                    return Files.size(f);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Parses yt-dlp size strings like "1G", "500M", "1024K", "123". Returns 0 when unparseable (no cap). */
    static long parseSize(String size) {
        if (size == null || size.isBlank()) {
            return 0;
        }
        Matcher m = SIZE.matcher(size.trim());
        if (!m.matches()) {
            return 0;
        }
        double n = Double.parseDouble(m.group(1));
        int pow = switch (m.group(2).toUpperCase(Locale.ROOT)) {
            case "K" -> 1;
            case "M" -> 2;
            case "G" -> 3;
            case "T" -> 4;
            default -> 0;
        };
        return (long) (n * Math.pow(1024, pow));
    }

    private void failIfNotSuccessful(RunResult result, String diagnostics) {
        if (result.cancelled) {
            throw new ImportException(ImportErrorCode.CANCELLED);
        }
        if (result.tooLarge) {
            throw new ImportException(ImportErrorCode.TOO_LARGE);
        }
        if (result.timedOut) {
            throw new ImportException(ImportErrorCode.TIMEOUT);
        }
        if (result.exitCode != 0) {
            String text = result.stderrTail.isEmpty() ? diagnostics : String.join("\n", result.stderrTail) + "\n" + diagnostics;
            String code = mapError(text);
            log.warn("yt-dlp exited with {} -> {} (last output: {})", result.exitCode, code, abbreviate(text));
            throw new ImportException(code);
        }
    }

    private static void drainLines(InputStream in, Consumer<String> sink) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sink.accept(line);
            }
        } catch (IOException e) {
            // stream closed because the process was destroyed; nothing left to read
        }
    }

    private static void addTail(Deque<String> tail, String line) {
        tail.addLast(line.length() > 500 ? line.substring(0, 500) : line);
        while (tail.size() > TAIL_LINES) {
            tail.removeFirst();
        }
    }

    private String abbreviate(String text) {
        String t = text.strip();
        t = t.length() > 600 ? t.substring(t.length() - 600) : t;
        String cookies = props.getVideoImport().getCookiesPath();
        if (cookies != null && !cookies.isBlank()) {
            t = t.replace(cookies, "<cookies>");
        }
        return CONTROL_CHARS.matcher(t).replaceAll(" "); // CWE-117
    }

    // ── parsing / mapping ─────────────────────────────────────────────────────

    /**
     * Facebook reel titles look like "242K views · 5.4K reactions | caption #tags | Author":
     * keep the caption without stats, author and hashtags. Other sources are returned unchanged.
     */
    static String cleanTitle(String extractorKey, String title) {
        if (title == null || !extractorKey.toLowerCase(Locale.ROOT).startsWith("facebook")) {
            return title;
        }
        String t = FB_STATS_PREFIX.matcher(title).replaceFirst("");
        int lastBar = t.lastIndexOf(" | ");
        if (lastBar > 0 && !t.equals(title)) {
            t = t.substring(0, lastBar);
        }
        String noTags = HASHTAG.matcher(t).replaceAll("").replaceAll("\\s+", " ").strip();
        String result = noTags.isEmpty() ? t.strip() : noTags;
        return result.isEmpty() ? title : result;
    }

    /** Returns {downloaded, total} or null when the line is not a progress line. */
    static long[] parseProgress(String line) {
        Matcher m = PROGRESS.matcher(line.trim());
        if (!m.matches()) {
            return null;
        }
        long downloaded = parseLong(m.group(1));
        long total = parseLong(m.group(2));
        if (total <= 0) {
            total = parseLong(m.group(3));
        }
        return new long[]{downloaded, total};
    }

    private static long parseLong(String s) {
        if (s == null || s.equals("NA") || s.equals("None")) {
            return 0;
        }
        try {
            return (long) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String mapError(String output) {
        String t = output == null ? "" : output.toLowerCase(Locale.ROOT);
        if (t.contains("larger than max-filesize") || t.contains("max-filesize")) {
            return ImportErrorCode.TOO_LARGE;
        }
        if (t.contains("unsupported url") || t.contains("is not a valid url") || t.contains("no suitable extractor")) {
            return ImportErrorCode.UNSUPPORTED_URL;
        }
        if (t.contains("login required") || t.contains("log in") || t.contains("sign in")
                || t.contains("private video") || t.contains("cookies") || t.contains("members-only")
                || t.contains("requires authentication")) {
            return ImportErrorCode.LOGIN_REQUIRED;
        }
        return ImportErrorCode.EXTRACTOR_ERROR;
    }

    private static Path findMedia(Path jobDir) {
        Path mp4 = jobDir.resolve("video.mp4");
        if (Files.isRegularFile(mp4)) {
            return mp4;
        }
        try (Stream<Path> files = Files.list(jobDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("video.") && !n.endsWith(".part") && !n.endsWith(".ytdl")
                                && !n.endsWith(".temp");
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
