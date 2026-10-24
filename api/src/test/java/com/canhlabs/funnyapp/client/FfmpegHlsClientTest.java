package com.canhlabs.funnyapp.client;

import com.canhlabs.funnyapp.enums.HlsErrorCode;
import com.canhlabs.funnyapp.exception.HlsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegHlsClientTest {

    @TempDir
    Path temp;

    private final List<List<String>> started = new ArrayList<>();

    private FfmpegHlsClient clientReturning(Process process) {
        return new FfmpegHlsClient(cmd -> {
            started.add(List.copyOf(cmd));
            return process;
        }, "ffmpeg", "ffprobe");
    }

    private HlsErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (HlsException e) {
            return e.getErrorCode();
        }
        return null;
    }

    @Test
    void probe_runsFfprobeWithTheFixedArgumentList() {
        Path src = temp.resolve("a.full");

        clientReturning(FakeProcess.finished("h264,video\naac,audio\n", "", 0)).probe(src);

        assertThat(started).containsExactly(List.of("ffprobe", "-v", "error", "-show_entries",
                "stream=codec_type,codec_name", "-of", "csv=p=0", src.toAbsolutePath().toString()));
    }

    @Test
    void probe_h264WithAac_isSupported() {
        FfmpegHlsClient.ProbeResult r = clientReturning(FakeProcess.finished("h264,video\naac,audio\n", "", 0)).probe(temp);

        assertThat(r.videoCodecs()).containsExactly("h264");
        assertThat(r.audioCodecs()).containsExactly("aac");
        assertThat(r.isSupported()).isTrue();
    }

    @Test
    void probe_h264WithMp3_isSupported() {
        assertThat(clientReturning(FakeProcess.finished("video,h264\nmp3,audio\n", "", 0)).probe(temp).isSupported()).isTrue();
    }

    @Test
    void probe_h264WithoutAudio_isSupported() {
        FfmpegHlsClient.ProbeResult r = clientReturning(FakeProcess.finished("h264,video\n", "", 0)).probe(temp);

        assertThat(r.audioCodecs()).isEmpty();
        assertThat(r.isSupported()).isTrue();
    }

    @Test
    void probe_hevc_isUnsupported() {
        assertThat(clientReturning(FakeProcess.finished("hevc,video\naac,audio\n", "", 0)).probe(temp).isSupported()).isFalse();
    }

    @Test
    void probe_unsupportedAudio_isUnsupported() {
        assertThat(clientReturning(FakeProcess.finished("h264,video\nopus,audio\n", "", 0)).probe(temp).isSupported()).isFalse();
        assertThat(clientReturning(FakeProcess.finished("h264,video\naac,audio\nac3,audio\n", "", 0)).probe(temp).isSupported())
                .isFalse();
    }

    @Test
    void probe_noVideoStream_isUnsupported() {
        assertThat(clientReturning(FakeProcess.finished("aac,audio\n", "", 0)).probe(temp).isSupported()).isFalse();
        assertThat(clientReturning(FakeProcess.finished("", "", 0)).probe(temp).isSupported()).isFalse();
    }

    @Test
    void probe_ignoresSubtitleAndDataStreams() {
        String out = "h264,video\naac,audio\nmov_text,subtitle\nbin_data,data\n";

        assertThat(clientReturning(FakeProcess.finished(out, "", 0)).probe(temp).isSupported()).isTrue();
    }

    @Test
    void probe_streamWithoutCodecName_isNotH264() {
        FfmpegHlsClient.ProbeResult r = clientReturning(FakeProcess.finished("video\n", "", 0)).probe(temp);

        assertThat(r.videoCodecs()).containsExactly("unknown");
        assertThat(r.isSupported()).isFalse();
    }

    @Test
    void probe_nonZeroExit_isFfmpegFailed() {
        FfmpegHlsClient c = clientReturning(FakeProcess.finished("", "moov atom not found\n", 1));

        assertThat(codeOf(() -> c.probe(temp))).isEqualTo(HlsErrorCode.FFMPEG_FAILED);
    }

    @Test
    void probe_startFailure_isFfmpegNotFound() {
        FfmpegHlsClient c = new FfmpegHlsClient(cmd -> {
            throw new IOException("Cannot run program \"ffprobe\"");
        }, "ffmpeg", "ffprobe");

        assertThat(codeOf(() -> c.probe(temp))).isEqualTo(HlsErrorCode.FFMPEG_NOT_FOUND);
    }

    @Test
    void segment_runsFfmpegWithTheFixedArgumentList() throws IOException {
        Path src = temp.resolve("a.full");
        Path tmp = temp.resolve("hls").resolve("a.tmp");
        Files.createDirectories(tmp);
        Files.writeString(tmp.resolve("playlist.m3u8"), "#EXTM3U");

        clientReturning(FakeProcess.finished("", "frame= 1\n", 0)).segment(src, tmp, Duration.ofSeconds(5));

        assertThat(started).containsExactly(List.of("ffmpeg", "-nostdin", "-y", "-i", src.toAbsolutePath().toString(),
                "-c", "copy", "-f", "hls", "-hls_time", "6", "-hls_playlist_type", "vod",
                "-hls_segment_filename", tmp.resolve("seg_%d.ts").toString(), tmp.resolve("playlist.m3u8").toString()));
    }

    @Test
    void segment_createsTheWorkDirectory() throws IOException {
        Path tmp = temp.resolve("new.tmp");
        FfmpegHlsClient c = clientReturning(FakeProcess.finished("", "", 0));

        assertThat(codeOf(() -> c.segment(temp.resolve("a.full"), tmp, Duration.ofSeconds(5))))
                .isEqualTo(HlsErrorCode.FFMPEG_FAILED);
        assertThat(tmp).isDirectory();
    }

    @Test
    void segment_exitZeroWithoutPlaylist_isFfmpegFailed() throws IOException {
        Path tmp = temp.resolve("a.tmp");
        FfmpegHlsClient c = clientReturning(FakeProcess.finished("", "", 0));

        assertThat(codeOf(() -> c.segment(temp.resolve("a.full"), tmp, Duration.ofSeconds(5))))
                .isEqualTo(HlsErrorCode.FFMPEG_FAILED);
    }

    @Test
    void segment_nonZeroExit_isFfmpegFailed() {
        FfmpegHlsClient c = clientReturning(FakeProcess.finished("", "Invalid data\nline\u0007two\n", 1));

        assertThat(codeOf(() -> c.segment(temp.resolve("a.full"), temp.resolve("a.tmp"), Duration.ofSeconds(5))))
                .isEqualTo(HlsErrorCode.FFMPEG_FAILED);
    }

    @Test
    void segment_startFailure_isFfmpegNotFound() {
        FfmpegHlsClient c = new FfmpegHlsClient(cmd -> {
            throw new IOException("not found");
        }, "ffmpeg", "ffprobe");

        assertThat(codeOf(() -> c.segment(temp.resolve("a.full"), temp.resolve("a.tmp"), Duration.ofSeconds(5))))
                .isEqualTo(HlsErrorCode.FFMPEG_NOT_FOUND);
    }

    @Test
    void segment_timeout_killsTheProcessAndReportsTimeout() {
        FakeProcess hanging = FakeProcess.hanging("");
        FfmpegHlsClient c = clientReturning(hanging);

        HlsErrorCode code = codeOf(() -> c.segment(temp.resolve("a.full"), temp.resolve("a.tmp"), Duration.ofMillis(150)));

        assertThat(code).isEqualTo(HlsErrorCode.TIMEOUT);
        assertThat(hanging.wasDestroyed()).isTrue();
    }

    @Test
    void publicConstructor_usesPlainToolNames() {
        FfmpegHlsClient c = new FfmpegHlsClient(cmd -> {
            started.add(List.copyOf(cmd));
            return FakeProcess.finished("", "", 0);
        });

        c.probe(temp);

        assertThat(started.get(0).get(0)).isEqualTo("ffprobe");
    }
}
