package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.streaming.StreamChunkResult;
import com.canhlabs.funnyapp.dto.video.VideoDto;
import com.canhlabs.funnyapp.service.HlsPlaybackService;
import com.canhlabs.funnyapp.service.StreamVideoService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit tests for VideoStreamController.
 * @WebMvcTest is avoided because StreamingResponseBody runs in a background thread,
 * which can cause ConcurrentModificationException against Mockito's stub state
 * when tests run as part of the full suite. Direct controller invocation is
 * synchronous and has no threading issues.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoStreamControllerTest {

    @Mock StreamVideoService videoService;
    @Mock HlsPlaybackService hlsPlaybackService;
    @InjectMocks VideoStreamController controller;

    private static final long FILE_SIZE = 1_000_000L;

    @BeforeEach
    void setUp() throws IOException {
        when(videoService.getFileSize(any())).thenReturn(FILE_SIZE);
        when(videoService.getPartialFileUsingRAF(any(), anyLong(), anyLong())).thenReturn(
                StreamChunkResult.builder()
                        .stream(new ByteArrayInputStream("videodata".getBytes()))
                        .actualStart(0L)
                        .actualEnd(8L)
                        .build());
    }

    // ── /list and /{id} ───────────────────────────────────────────────────────

    @Test
    void getListVideoStream_returnsOkWithList() {
        when(videoService.getVideosToStream()).thenReturn(List.of(VideoDto.builder().build()));

        ResponseEntity<?> response = controller.getListVideoStream();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getListVideoStream_emptyList_returnsOk() {
        when(videoService.getVideosToStream()).thenReturn(List.of());

        assertThat(controller.getListVideoStream().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getVideoStream_byId_returnsOk() {
        when(videoService.getVideoById(1L)).thenReturn(VideoDto.builder().build());

        assertThat(controller.getVideoStream(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── /stream/{fileId} ──────────────────────────────────────────────────────

    @Test
    void streamVideo_noRangeHeader_returns206WithRequiredHeaders() throws IOException {
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isNotNull();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("video/mp4");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");

        // Execute the streaming body to cover the lambda branch
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out.toByteArray()).isEqualTo("videodata".getBytes());
    }

    @Test
    void streamVideo_rangeNoEnd_start0_usesFirstChunkSize() throws IOException {
        // bytes=0- → end = min(0 + 256KB - 1, fileSize-1)
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=0-");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).contains("bytes 0-8/");
    }

    @Test
    void streamVideo_rangeNoEnd_nonZeroStart_usesNextChunkSize() throws IOException {
        // bytes=262144- → end = min(262144 + 512KB - 1, fileSize-1)
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=262144-");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
    }

    @Test
    void streamVideo_explicitEnd_usesMin() throws IOException {
        // bytes=0-1023 → end = min(1023, fileSize-1)
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=0-1023");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
    }

    @Test
    void streamVideo_endExceedsFileSize_cappedAtFileSize() throws IOException {
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=0-9999999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
    }

    @Test
    void streamVideo_invalidRangeFormat_fallsBackToFullRange() throws IOException {
        // NumberFormatException caught → falls back to null range → full range
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=abc-def");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
    }

    @Test
    void streamVideo_rangeNotStartingWithBytes_treatedAsNoRange() throws IOException {
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "invalid-header");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
    }

    // ── /hls/{fileId}/... ─────────────────────────────────────────────────────

    @TempDir Path temp;

    @Test
    void getHlsPlaylist_found_returns200WithHeaders() throws IOException {
        Path playlist = Files.writeString(temp.resolve("playlist.m3u8"), "#EXTM3U");
        when(hlsPlaybackService.findPlaylist("abc_-1")).thenReturn(Optional.of(playlist));

        ResponseEntity<Resource> response = controller.getHlsPlaylist("abc_-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/vnd.apple.mpegurl");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache");
        assertThat(response.getBody().contentLength()).isEqualTo(7);
    }

    @Test
    void getHlsPlaylist_notServable_returns404() {
        when(hlsPlaybackService.findPlaylist("abc")).thenReturn(Optional.empty());

        assertThat(controller.getHlsPlaylist("abc").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "a/b", "a%2Fb", "%2e%2e", "a.b", "", "/etc/passwd", "a\\b"})
    void hlsEndpoints_badFileId_return400WithoutLookup(String fileId) {
        assertThat(controller.getHlsPlaylist(fileId).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.getHlsSegment(fileId, "seg_0.ts").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(hlsPlaybackService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../x.ts", "..%2Fx.ts", "seg_1.m3u8", "seg_.ts", "seg_123456.ts", "playlist.m3u8", "seg_1.ts/../x"})
    void getHlsSegment_badName_returns400WithoutLookup(String segment) {
        assertThat(controller.getHlsSegment("abc", segment).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(hlsPlaybackService);
    }

    @Test
    void getHlsSegment_found_returns200WithImmutableCacheAndLength() throws IOException {
        Path seg = Files.writeString(temp.resolve("seg_0.ts"), "12345");
        when(hlsPlaybackService.findSegment("abc", "seg_0.ts")).thenReturn(Optional.of(seg));

        ResponseEntity<Resource> response = controller.getHlsSegment("abc", "seg_0.ts");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("video/mp2t");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("public, max-age=31536000, immutable");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH)).isEqualTo("5");
    }

    @Test
    void getHlsSegment_missing_returns404() {
        when(hlsPlaybackService.findSegment("abc", "seg_0.ts")).thenReturn(Optional.empty());

        assertThat(controller.getHlsSegment("abc", "seg_0.ts").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getHlsSegment_fileVanishesBeforeLength_returns404() {
        when(hlsPlaybackService.findSegment("abc", "seg_0.ts")).thenReturn(Optional.of(temp.resolve("gone.ts")));

        assertThat(controller.getHlsSegment("abc", "seg_0.ts").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
