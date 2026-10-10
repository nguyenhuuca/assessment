package com.canhlabs.funnyapp.client;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.exception.ImportErrorCode;
import com.canhlabs.funnyapp.exception.ImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YtDlpClientImplTest {

    private static final String URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @TempDir
    Path tmp;

    private AppProperties props;
    private final List<List<String>> commands = new CopyOnWriteArrayList<>();
    private Function<List<String>, Process> behaviour;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getVideoImport().setYtDlpPath("yt-dlp");
        props.getVideoImport().setWorkDir(tmp.toString());
    }

    private YtDlpClientImpl client() {
        return new YtDlpClientImpl(props, cmd -> {
            commands.add(List.copyOf(cmd));
            return behaviour.apply(cmd);
        });
    }

    // ── arguments ─────────────────────────────────────────────────────────────

    @Test
    void download_usesExactHardenedArguments() throws IOException {
        Path jobDir = tmp.resolve("7");
        behaviour = cmd -> {
            touch(jobDir.resolve("video.mp4"));
            return FakeProcess.finished("", "", 0);
        };

        Path result = client().download(URL, jobDir, (d, t) -> { }, new CancelToken());

        assertThat(result).isEqualTo(jobDir.resolve("video.mp4"));
        assertThat(commands).hasSize(1);
        assertThat(commands.get(0)).containsExactly(
                "yt-dlp", "--ignore-config", "--no-playlist", "--use-extractors", "youtube,youtube:.*,facebook,facebook:.*",
                "--no-cache-dir", "--socket-timeout", "30",
                "--newline", "--progress-template",
                "download:%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s",
                "-f", "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720]/b",
                "--merge-output-format", "mp4",
                "--max-filesize", "1G", "--match-filter", "!is_live",
                "-o", jobDir.resolve("video.%(ext)s").toString(),
                "--", URL);
    }

    @Test
    void download_cookiesAddedOnlyWhenConfigured_andBeforeSeparator() throws IOException {
        props.getVideoImport().setCookiesPath("/secure/cookies.txt");
        props.getVideoImport().setMaxFileSize("500M");
        Path jobDir = tmp.resolve("8");
        behaviour = cmd -> {
            touch(jobDir.resolve("video.mp4"));
            return FakeProcess.finished("", "", 0);
        };

        client().download(URL, jobDir, null, new CancelToken());

        List<String> cmd = commands.get(0);
        assertThat(cmd).containsSubsequence("--cookies", "/secure/cookies.txt", "--", URL);
        assertThat(cmd.indexOf("--cookies")).isLessThan(cmd.indexOf("--"));
        assertThat(cmd).contains("500M");
        assertThat(cmd.get(cmd.size() - 1)).isEqualTo(URL);
    }

    @Test
    void download_urlLooksLikeAnOption_stillAfterSeparator() throws IOException {
        Path jobDir = tmp.resolve("9");
        behaviour = cmd -> {
            touch(jobDir.resolve("video.mp4"));
            return FakeProcess.finished("", "", 0);
        };

        client().download("--exec=rm", jobDir, null, new CancelToken());

        List<String> cmd = commands.get(0);
        assertThat(cmd.indexOf("--")).isEqualTo(cmd.size() - 2);
        assertThat(cmd.get(cmd.size() - 1)).isEqualTo("--exec=rm");
    }

    // ── progress ──────────────────────────────────────────────────────────────

    @Test
    void download_parsesProgressLines() throws IOException {
        Path jobDir = tmp.resolve("10");
        String out = String.join("\n",
                "[youtube] Extracting URL",
                "download:100|1000|NA",
                "500|NA|2000.5",
                "NA|NA|NA",
                "garbage|1|2",
                "") ;
        behaviour = cmd -> {
            touch(jobDir.resolve("video.mp4"));
            return FakeProcess.finished(out, "", 0);
        };
        List<long[]> seen = new ArrayList<>();

        client().download(URL, jobDir, (d, t) -> seen.add(new long[]{d, t}), new CancelToken());

        assertThat(seen).hasSize(3);
        assertThat(seen.get(0)).containsExactly(100, 1000);
        assertThat(seen.get(1)).containsExactly(500, 2000);
        assertThat(seen.get(2)).containsExactly(0, 0);
    }

    @Test
    void parseProgress_nonProgressLine_returnsNull() {
        assertThat(YtDlpClientImpl.parseProgress("[download] Destination: x.mp4")).isNull();
        assertThat(YtDlpClientImpl.parseProgress("")).isNull();
    }

    // ── errors ────────────────────────────────────────────────────────────────

    @Test
    void download_mapsStderrToErrorCodes() {
        assertDownloadFails("ERROR: Unsupported URL: https://x", ImportErrorCode.UNSUPPORTED_URL);
        assertDownloadFails("ERROR: [facebook] This video requires login. Use --cookies", ImportErrorCode.LOGIN_REQUIRED);
        assertDownloadFails("ERROR: Sign in to confirm you are not a bot", ImportErrorCode.LOGIN_REQUIRED);
        assertDownloadFails("ERROR: Private video. Sign in if you have access", ImportErrorCode.LOGIN_REQUIRED);
        assertDownloadFails("File is larger than max-filesize (2000000000 bytes > 1000000000 bytes)", ImportErrorCode.TOO_LARGE);
        assertDownloadFails("ERROR: something exploded", ImportErrorCode.EXTRACTOR_ERROR);
        assertDownloadFails("", ImportErrorCode.EXTRACTOR_ERROR);
    }

    private void assertDownloadFails(String stderr, String expectedCode) {
        behaviour = cmd -> FakeProcess.finished("", stderr, 1);
        assertThatThrownBy(() -> client().download(URL, tmp.resolve("e"), null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(expectedCode);
                    // client-safe: the raw stderr is never part of the message
                    assertThat(e.getMessage()).doesNotContain(stderr.isEmpty() ? "\u0000" : stderr);
                });
    }

    @Test
    void download_exitZeroButNoFile_largeFileSkipped_isTooLarge() {
        behaviour = cmd -> FakeProcess.finished("[download] File is larger than max-filesize (5 bytes > 1 bytes). Aborting.\n", "", 0);

        assertThatThrownBy(() -> client().download(URL, tmp.resolve("big"), null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.TOO_LARGE));
    }

    @Test
    void download_exitZeroNoFileNoHint_isExtractorError() {
        behaviour = cmd -> FakeProcess.finished("", "", 0);

        assertThatThrownBy(() -> client().download(URL, tmp.resolve("none"), null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.EXTRACTOR_ERROR));
    }

    @Test
    void download_ignoresPartialFiles() throws IOException {
        Path jobDir = tmp.resolve("partial");
        behaviour = cmd -> {
            touch(jobDir.resolve("video.f137.mp4.part"));
            return FakeProcess.finished("", "", 0);
        };

        assertThatThrownBy(() -> client().download(URL, jobDir, null, new CancelToken()))
                .isInstanceOf(ImportException.class);
    }

    @Test
    void download_nonMp4Output_isFound() throws IOException {
        Path jobDir = tmp.resolve("webm");
        behaviour = cmd -> {
            touch(jobDir.resolve("video.webm"));
            return FakeProcess.finished("", "", 0);
        };

        assertThat(client().download(URL, jobDir, null, new CancelToken())).isEqualTo(jobDir.resolve("video.webm"));
    }

    @Test
    void binaryMissing_isNotInstalled() {
        YtDlpClientImpl c = new YtDlpClientImpl(props, cmd -> {
            throw new IOException("Cannot run program");
        });

        assertThatThrownBy(() -> c.download(URL, tmp.resolve("x"), null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.NOT_INSTALLED));
        assertThatThrownBy(() -> c.fetchMetadata(URL))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.NOT_INSTALLED));
        assertThat(c.version()).isEmpty();
    }

    // ── timeout / cancel ──────────────────────────────────────────────────────

    @Test
    void download_timeout_destroysProcess() {
        props.getVideoImport().setDownloadTimeout(Duration.ofMillis(150));
        FakeProcess hanging = FakeProcess.hanging("10|100|NA\n");
        behaviour = cmd -> hanging;

        assertThatThrownBy(() -> client().download(URL, tmp.resolve("t"), null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.TIMEOUT));
        assertThat(hanging.wasDestroyed()).isTrue();
    }

    @Test
    void download_cancelWhileRunning_destroysProcess() throws Exception {
        FakeProcess hanging = FakeProcess.hanging("10|100|NA\n");
        behaviour = cmd -> hanging;
        CancelToken token = new CancelToken();
        Thread canceller = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            token.cancel();
        });

        assertThatThrownBy(() -> client().download(URL, tmp.resolve("c"), null, token))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.CANCELLED));
        canceller.join();
        assertThat(hanging.wasDestroyed()).isTrue();
    }

    @Test
    void download_alreadyCancelled_doesNotStartProcess() {
        CancelToken token = new CancelToken();
        token.cancel();
        behaviour = cmd -> FakeProcess.finished("", "", 0);

        assertThatThrownBy(() -> client().download(URL, tmp.resolve("pre"), null, token))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.CANCELLED));
        assertThat(commands).isEmpty();
    }

    // ── metadata / version ────────────────────────────────────────────────────

    @Test
    void fetchMetadata_nullOutputWithExitZero_isUnsupportedUrl() {
        behaviour = cmd -> FakeProcess.finished("null",
                "ERROR: No suitable extractor found for URL https://www.facebook.com/reel/1", 0);

        assertThatThrownBy(() -> client().fetchMetadata(URL))
                .isInstanceOf(ImportException.class)
                .extracting(e -> ((ImportException) e).getErrorCode())
                .isEqualTo(ImportErrorCode.UNSUPPORTED_URL);
    }

    @Test
    void fetchMetadata_facebookReel_titleIsCleaned() {
        behaviour = cmd -> FakeProcess.finished("{\"id\":\"1101383289299158\",\"extractor_key\":\"Facebook\","
                + "\"title\":\"242K views \u00b7 5.4K reactions | T\u1ef1 nhi\u00ean mu\u1ed1n \u0111\u00e0n l\u1ea1i b\u00e0i n\u00e0y... "
                + "#guitar #Boulevard #reelsvideo\u30b7 | Ho\u00e0ng Chi\",\"duration\":58.5}", "", 0);

        VideoMetadata meta = client().fetchMetadata(URL);

        assertThat(meta.title()).isEqualTo("T\u1ef1 nhi\u00ean mu\u1ed1n \u0111\u00e0n l\u1ea1i b\u00e0i n\u00e0y...");
    }

    @Test
    void cleanTitle_leavesYoutubeAndPlainFacebookTitlesAlone() {
        assertThat(YtDlpClientImpl.cleanTitle("Youtube", "Song | Artist")).isEqualTo("Song | Artist");
        assertThat(YtDlpClientImpl.cleanTitle("Facebook", "Funny cat | Page")).isEqualTo("Funny cat | Page");
        assertThat(YtDlpClientImpl.cleanTitle("Facebook", "1K views \u00b7 20 reactions | #only #tags | X"))
                .isEqualTo("#only #tags");
        assertThat(YtDlpClientImpl.cleanTitle("Facebook", null)).isNull();
    }

    @Test
    void fetchMetadata_parsesJsonAndUsesMetadataArgs() {
        behaviour = cmd -> FakeProcess.finished(
                "{\"id\":\"dQw4w9WgXcQ\",\"title\":\"Never Gonna\",\"duration\":212.0,\"formats\":[]}", "", 0);

        VideoMetadata meta = client().fetchMetadata(URL);

        assertThat(meta).isEqualTo(new VideoMetadata("dQw4w9WgXcQ", "Never Gonna", 212L));
        assertThat(commands.get(0)).containsExactly(
                "yt-dlp", "--ignore-config", "--no-playlist", "--use-extractors", "youtube,youtube:.*,facebook,facebook:.*",
                "--no-cache-dir", "--socket-timeout", "30",
                "-J", "--skip-download", "--", URL);
    }

    @Test
    void fetchMetadata_missingDuration_isNull() {
        behaviour = cmd -> FakeProcess.finished("{\"id\":\"a\",\"title\":\"t\"}", "", 0);

        assertThat(client().fetchMetadata(URL).durationSec()).isNull();
    }

    @Test
    void fetchMetadata_invalidJson_isExtractorError() {
        behaviour = cmd -> FakeProcess.finished("not json", "", 0);

        assertThatThrownBy(() -> client().fetchMetadata(URL))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.EXTRACTOR_ERROR));
    }

    @Test
    void fetchMetadata_failure_mapsError() {
        behaviour = cmd -> FakeProcess.finished("", "ERROR: Unsupported URL: x", 1);

        assertThatThrownBy(() -> client().fetchMetadata(URL))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.UNSUPPORTED_URL));
    }

    @Test
    void fetchMetadata_timeout() {
        props.getVideoImport().setMetadataTimeout(Duration.ofMillis(100));
        FakeProcess hanging = FakeProcess.hanging("");
        behaviour = cmd -> hanging;

        assertThatThrownBy(() -> client().fetchMetadata(URL))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.TIMEOUT));
        assertThat(hanging.wasDestroyed()).isTrue();
    }

    @Test
    void version_returnsTrimmedOutput() {
        behaviour = cmd -> FakeProcess.finished("2026.10.01\n", "", 0);

        assertThat(client().version()).contains("2026.10.01");
        assertThat(commands.get(0)).containsExactly("yt-dlp", "--version");
    }

    @Test
    void version_failure_isEmpty() {
        behaviour = cmd -> FakeProcess.finished("", "boom", 2);

        assertThat(client().version()).isEmpty();
    }

    // ── error mapping ─────────────────────────────────────────────────────────

    @Test
    void mapError_nullIsExtractorError() {
        assertThat(YtDlpClientImpl.mapError(null)).isEqualTo(ImportErrorCode.EXTRACTOR_ERROR);
    }

    @Test
    void cancelToken_hookRunsOnceAndImmediatelyWhenAlreadyCancelled() {
        CancelToken token = new CancelToken();
        int[] runs = {0};
        token.onCancel(() -> runs[0]++);
        token.cancel();
        token.cancel();
        token.onCancel(() -> runs[0]++);

        assertThat(runs[0]).isEqualTo(2);
        assertThat(token.isCancelled()).isTrue();
    }

    private static void touch(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "x");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
