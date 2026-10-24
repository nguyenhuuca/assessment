package com.canhlabs.funnyapp.client;

import com.canhlabs.funnyapp.enums.HlsErrorCode;
import com.canhlabs.funnyapp.exception.HlsException;
import com.canhlabs.funnyapp.service.impl.HlsStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Runs ffprobe and ffmpeg for the HLS worker (ADR-0031). Commands are fixed argument lists, never a shell
 * string (CWE-78); only the validated source and work paths are inserted. Raw tool output is only logged
 * (bounded) and mapped to an {@link HlsErrorCode}.
 */
@Slf4j
@Component
public class FfmpegHlsClient {

    static final int TAIL_LINES = 20;
    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_PROBE_LINES = 200;
    private static final Set<String> STREAM_TYPES = Set.of("video", "audio", "subtitle", "data", "attachment");
    private static final Set<String> AUDIO_ALLOWED = Set.of("aac", "mp3");
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");

    private final ProcessFactory processFactory;
    private final String ffmpegPath;
    private final String ffprobePath;

    @Autowired
    public FfmpegHlsClient(ProcessFactory processFactory) {
        this(processFactory, "ffmpeg", "ffprobe");
    }

    /** Tests (and a future config) may point at other binaries. */
    FfmpegHlsClient(ProcessFactory processFactory, String ffmpegPath, String ffprobePath) {
        this.processFactory = processFactory;
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
    }

    /** Codecs found by ffprobe, one entry per stream. */
    public record ProbeResult(List<String> videoCodecs, List<String> audioCodecs) {

        /** BR-5: h264 video only, and aac or mp3 audio or no audio at all. */
        public boolean isSupported() {
            return !videoCodecs.isEmpty()
                    && videoCodecs.stream().allMatch("h264"::equals)
                    && audioCodecs.stream().allMatch(AUDIO_ALLOWED::contains);
        }
    }

    public ProbeResult probe(Path source) {
        List<String> cmd = List.of(ffprobePath, "-v", "error", "-show_entries", "stream=codec_type,codec_name",
                "-of", "csv=p=0", source.toAbsolutePath().toString());
        List<String> lines = new ArrayList<>();
        RunResult result = run(cmd, PROBE_TIMEOUT, line -> {
            if (lines.size() < MAX_PROBE_LINES) {
                lines.add(line);
            }
        });
        failIfNotSuccessful("ffprobe", result);
        return parseProbe(lines);
    }

    /**
     * Stream-copies {@code source} into {@code tmpDir} as an HLS VOD playlist plus {@code seg_N.ts} files.
     * Returns normally only when ffmpeg exited 0 and the playlist exists.
     */
    public void segment(Path source, Path tmpDir, Duration timeout) {
        try {
            Files.createDirectories(tmpDir);
        } catch (IOException e) {
            throw new HlsException(HlsErrorCode.FFMPEG_FAILED, e);
        }
        Path playlist = tmpDir.resolve(HlsStorage.PLAYLIST);
        List<String> cmd = List.of(ffmpegPath, "-nostdin", "-y", "-i", source.toAbsolutePath().toString(),
                "-c", "copy", "-f", "hls", "-hls_time", "6", "-hls_playlist_type", "vod",
                "-hls_segment_filename", tmpDir.resolve(HlsStorage.SEGMENT_PATTERN).toString(),
                playlist.toString());
        RunResult result = run(cmd, timeout, line -> {
        });
        failIfNotSuccessful("ffmpeg", result);
        if (!Files.isRegularFile(playlist)) {
            log.warn("ffmpeg exited 0 but wrote no playlist");
            throw new HlsException(HlsErrorCode.FFMPEG_FAILED);
        }
    }

    // ── parsing ───────────────────────────────────────────────────────────────

    /** Lines look like {@code h264,video}; the two fields are matched by value so their order does not matter. */
    static ProbeResult parseProbe(List<String> lines) {
        List<String> video = new ArrayList<>();
        List<String> audio = new ArrayList<>();
        for (String line : lines) {
            String type = null;
            String codec = null;
            for (String field : line.split(",")) {
                String f = field.trim();
                if (STREAM_TYPES.contains(f)) {
                    type = f;
                } else if (!f.isEmpty() && codec == null) {
                    codec = f;
                }
            }
            if ("video".equals(type)) {
                video.add(codec == null ? "unknown" : codec);
            } else if ("audio".equals(type)) {
                audio.add(codec == null ? "unknown" : codec);
            }
        }
        return new ProbeResult(List.copyOf(video), List.copyOf(audio));
    }

    // ── process execution ─────────────────────────────────────────────────────

    private record RunResult(int exitCode, boolean timedOut, List<String> stderrTail) {
    }

    private RunResult run(List<String> cmd, Duration timeout, Consumer<String> stdoutLine) {
        Process process;
        try {
            process = processFactory.start(cmd);
        } catch (IOException e) {
            log.warn("Cannot start {}: {}", cmd.get(0), e.getClass().getSimpleName());
            throw new HlsException(HlsErrorCode.FFMPEG_NOT_FOUND, e);
        }

        Deque<String> stderrTail = new ConcurrentLinkedDeque<>();
        AtomicBoolean timedOut = new AtomicBoolean();
        Thread errReader = Thread.ofVirtual().start(() -> drainLines(process.getErrorStream(), line -> addTail(stderrTail, line)));
        Thread watchdog = Thread.ofVirtual().start(() -> {
            try {
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    timedOut.set(true);
                    kill(process);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        int exit;
        try {
            drainLines(process.getInputStream(), stdoutLine);
            exit = process.waitFor();
            errReader.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            kill(process);
            throw new HlsException(HlsErrorCode.FFMPEG_FAILED, e);
        } finally {
            watchdog.interrupt();
        }
        return new RunResult(exit, timedOut.get(), new ArrayList<>(stderrTail));
    }

    /** Kills children too: an orphan keeps the stdout pipe open and blocks the drain. */
    private static void kill(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (UnsupportedOperationException e) {
            // Process implementations without a native handle; the parent is still killed below
        } finally {
            process.destroyForcibly();
        }
    }

    private void failIfNotSuccessful(String tool, RunResult result) {
        if (result.timedOut) {
            log.warn("{} timed out", tool);
            throw new HlsException(HlsErrorCode.TIMEOUT);
        }
        if (result.exitCode != 0) {
            log.warn("{} exited with {} (last output: {})", tool, result.exitCode, abbreviate(String.join("\n", result.stderrTail)));
            throw new HlsException(HlsErrorCode.FFMPEG_FAILED);
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

    private static String abbreviate(String text) {
        String t = text.strip();
        t = t.length() > 600 ? t.substring(t.length() - 600) : t;
        return CONTROL_CHARS.matcher(t).replaceAll(" "); // CWE-117
    }
}
