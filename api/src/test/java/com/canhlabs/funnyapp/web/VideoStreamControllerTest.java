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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private static final long MAX_CHUNK = 1024 * 1024L;

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

    private void runBody(String fileId, String range) throws IOException {
        controller.streamVideo(fileId, range).getBody().writeTo(new ByteArrayOutputStream());
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
        verify(videoService).getPartialFileUsingRAF("video1", 0L, 262_143L);
    }

    @Test
    void streamVideo_rangeNoEnd_start0_usesFirstChunkSize() throws IOException {
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=0-");
        response.getBody().writeTo(new ByteArrayOutputStream());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        verify(videoService).getPartialFileUsingRAF("video1", 0L, 262_143L);
    }

    @Test
    void streamVideo_rangeNoEnd_nonZeroStart_usesNextChunkSize() throws IOException {
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=262144-");
        response.getBody().writeTo(new ByteArrayOutputStream());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        verify(videoService).getPartialFileUsingRAF("video1", 262_144L, 786_431L);
    }

    @Test
    void streamVideo_explicitEnd_usesMin() throws IOException {
        runBody("video1", "bytes=0-1023");

        verify(videoService).getPartialFileUsingRAF("video1", 0L, 1023L);
    }

    @Test
    void streamVideo_endExceedsFileSize_cappedAtFileSize() throws IOException {
        runBody("video1", "bytes=10-9999999");

        verify(videoService).getPartialFileUsingRAF("video1", 10L, FILE_SIZE - 1);
    }

    @Test
    void streamVideo_explicitEndBeyondMaxChunk_cappedAtOneMb() throws IOException {
        when(videoService.getFileSize(any())).thenReturn(10_000_000L);

        runBody("video1", "bytes=0-9999999");

        verify(videoService).getPartialFileUsingRAF("video1", 0L, MAX_CHUNK - 1);
    }

    @Test
    void streamVideo_suffixRange_returnsLastBytes() throws IOException {
        runBody("video1", "bytes=-500");

        verify(videoService).getPartialFileUsingRAF("video1", FILE_SIZE - 500, FILE_SIZE - 1);
    }

    @Test
    void streamVideo_suffixLargerThanMax_cappedAtOneMb() throws IOException {
        when(videoService.getFileSize(any())).thenReturn(10_000_000L);

        runBody("video1", "bytes=-5000000");

        verify(videoService).getPartialFileUsingRAF("video1", 10_000_000L - MAX_CHUNK, 9_999_999L);
    }

    @Test
    void streamVideo_suffixLargerThanFile_startsAtZero() throws IOException {
        when(videoService.getFileSize(any())).thenReturn(1000L);

        runBody("video1", "bytes=-5000");

        verify(videoService).getPartialFileUsingRAF("video1", 0L, 999L);
    }

    @Test
    void streamVideo_multiRange_servesFirstRangeOnly() throws IOException {
        runBody("video1", "bytes=0-99, 500-599");

        verify(videoService).getPartialFileUsingRAF("video1", 0L, 99L);
    }

    @Test
    void streamVideo_rangeNotStartingWithBytes_treatedAsOpenRange() throws IOException {
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "invalid-header");
        response.getBody().writeTo(new ByteArrayOutputStream());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        verify(videoService).getPartialFileUsingRAF("video1", 0L, 262_143L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes=abc-def", "bytes=5000-10", "bytes=1000000-", "bytes=2000000-2000010",
            "bytes=-0", "bytes=-", "bytes=-abc", "bytes=100"})
    void streamVideo_invalidOrUnsatisfiableRange_returns416WithoutReadingFile(String header) throws IOException {
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", header);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */" + FILE_SIZE);
        assertThat(response.getBody()).isNull();
        verify(videoService, never()).getPartialFileUsingRAF(any(), anyLong(), anyLong());
    }

    @Test
    void streamVideo_emptyFile_returns416() throws IOException {
        when(videoService.getFileSize(any())).thenReturn(0L);

        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=0-");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */0");
        verify(videoService, never()).getPartialFileUsingRAF(any(), anyLong(), anyLong());
    }

    // ── stream ownership ──────────────────────────────────────────────────────

    private AtomicBoolean stubTrackedStream() throws IOException {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream tracked = new ByteArrayInputStream("videodata".getBytes()) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
        when(videoService.getPartialFileUsingRAF(any(), anyLong(), anyLong())).thenReturn(
                StreamChunkResult.builder().stream(tracked).actualStart(0L).actualEnd(8L).build());
        return closed;
    }

    @Test
    void streamVideo_buildingResponse_doesNotOpenStream() throws IOException {
        controller.streamVideo("video1", "bytes=0-99");

        verify(videoService, never()).getPartialFileUsingRAF(any(), anyLong(), anyLong());
    }

    @Test
    void streamVideo_contentLengthComesFromParsedRange() throws IOException {
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=10-109");

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH)).isEqualTo("100");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 10-109/" + FILE_SIZE);
    }

    @Test
    void streamVideo_runningBody_readsAndClosesStream() throws IOException {
        AtomicBoolean closed = stubTrackedStream();
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=0-8");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);

        assertThat(out.toByteArray()).isEqualTo("videodata".getBytes());
        assertThat(closed).isTrue();
    }

    @Test
    void streamVideo_throwingWrite_stillClosesStream() throws IOException {
        AtomicBoolean closed = stubTrackedStream();
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=0-8");
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("client gone");
            }
        };

        assertThatThrownBy(() -> response.getBody().writeTo(failing)).isInstanceOf(IOException.class);
        assertThat(closed).isTrue();
    }

    @Test
    void streamVideo_openFailsInsideBody_propagates() throws IOException {
        when(videoService.getPartialFileUsingRAF(any(), anyLong(), anyLong())).thenThrow(new IOException("gone"));
        ResponseEntity<StreamingResponseBody> response = controller.streamVideo("video1", "bytes=0-8");

        assertThatThrownBy(() -> response.getBody().writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void streamVideo_missingFile_failsBeforeResponse() throws IOException {
        when(videoService.getFileSize(any())).thenThrow(new IOException("missing"));

        assertThatThrownBy(() -> controller.streamVideo("video1", "bytes=0-8")).isInstanceOf(IOException.class);
        verify(videoService, never()).getPartialFileUsingRAF(any(), anyLong(), anyLong());
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
