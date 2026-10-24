package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.streaming.Range;
import com.canhlabs.funnyapp.streaming.StreamChunkResult;
import com.canhlabs.funnyapp.dto.video.VideoDto;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.HlsPlaybackService;
import com.canhlabs.funnyapp.service.StreamVideoService;
import com.canhlabs.funnyapp.service.impl.HlsStorage;
import com.canhlabs.funnyapp.utils.AppConstant;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@RequestMapping(AppConstant.API.BASE_URL + "/video-stream")
@RestController
public class VideoStreamController {
    private static final long FIRST_CHUNK_SIZE = 256 * 1024L; // 256KB
    private static final long NEXT_CHUNK_SIZE = 512 * 1024L;  // 512KB
    private static final long MAX_CHUNK_SIZE = 1024 * 1024L;  // 1MB, hard cap for any Range form
    private static final String HLS_PLAYLIST_TYPE = "application/vnd.apple.mpegurl";
    private static final String HLS_SEGMENT_TYPE = "video/mp2t";
    private static final String HLS_SEGMENT_CACHE = "public, max-age=31536000, immutable";
    private final StreamVideoService videoService;
    private final HlsPlaybackService hlsPlaybackService;

    public VideoStreamController(StreamVideoService videoService, HlsPlaybackService hlsPlaybackService) {
        this.videoService = videoService;
        this.hlsPlaybackService = hlsPlaybackService;
    }

    @Operation(summary = "Stream video by fileId", description = "Streams a video file in chunks based on the Range header provided by the client.")
    @WithSpan
    @GetMapping("/stream/{fileId}")
    public ResponseEntity<StreamingResponseBody> streamVideo(
            @PathVariable String fileId,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) throws IOException {

        long startTime = System.currentTimeMillis();
        log.info("Thread: {}, Virtual: {}", Thread.currentThread().getName(), Thread.currentThread().isVirtual());
        log.info("Incoming stream request for fileId: {}", fileId);

        long fileSize = videoService.getFileSize(fileId);

        Range range = parseRangeHeader(rangeHeader, fileSize);
        if (range == null) {
            log.warn("Unsatisfiable range {} for fileId: {}", rangeHeader, fileId);
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                    .build();
        }
        long start = range.start();
        long end = range.end();
        log.info("Streaming bytes {} to {}", start, end);
        StreamChunkResult streamRs = videoService.getPartialFileUsingRAF(fileId, start, end);
        InputStream stream = streamRs.getStream();
        StreamingResponseBody responseBody = outputStream -> {
            try (stream) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = stream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        };

        long contentLength = streamRs.getActualEnd() - streamRs.getActualStart() + 1;
        log.info("Finished preparing response. Duration: {} ms", System.currentTimeMillis() - startTime);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", streamRs.getActualStart(), streamRs.getActualEnd(), fileSize))
                .body(responseBody);
    }

    @Operation(summary = "Get list of videos to stream", description = "Returns a list of video metadata for streaming.")
    @WithSpan
    @GetMapping("/list")
    public ResponseEntity<ResultListInfo<VideoDto>> getListVideoStream() {
        log.info("getTopVideos Thread: {}, isVirtual: {}", Thread.currentThread().getName(), Thread.currentThread().isVirtual());
        List<VideoDto> rs = videoService.getVideosToStream();
        return new ResponseEntity<>(ResultListInfo.<VideoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }

    @Operation(summary = "Get video by ID", description = "Retrieves video metadata by its ID.")
    @WithSpan
    @GetMapping("/{id}")
    public ResponseEntity<ResultObjectInfo<VideoDto>> getVideoStream(@PathVariable Long id) {
        VideoDto rs = videoService.getVideoById(id);
        return new ResponseEntity<>(ResultObjectInfo.<VideoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }

    @Operation(summary = "HLS playlist", description = "Serves the HLS playlist of a READY, published, visible video.")
    @GetMapping("/hls/{fileId}/playlist.m3u8")
    public ResponseEntity<Resource> getHlsPlaylist(@PathVariable String fileId) {
        if (!HlsStorage.isValidFileId(fileId)) {
            return ResponseEntity.badRequest().build();
        }
        return hlsPlaybackService.findPlaylist(fileId)
                .map(path -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, HLS_PLAYLIST_TYPE)
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                        .<Resource>body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "HLS segment", description = "Serves one HLS segment; no database lookup.")
    @GetMapping("/hls/{fileId}/{segment}")
    public ResponseEntity<Resource> getHlsSegment(@PathVariable String fileId, @PathVariable String segment) {
        if (!HlsStorage.isValidFileId(fileId) || !HlsStorage.isValidSegment(segment)) {
            return ResponseEntity.badRequest().build();
        }
        return hlsPlaybackService.findSegment(fileId, segment)
                .map(VideoStreamController::segmentResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<Resource> segmentResponse(Path path) {
        FileSystemResource resource = new FileSystemResource(path);
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, HLS_SEGMENT_TYPE)
                    .header(HttpHeaders.CACHE_CONTROL, HLS_SEGMENT_CACHE)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(resource.contentLength()))
                    .body(resource);
        } catch (IOException e) {
            log.warn("HLS segment vanished: {}", path.getFileName());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Parses the Range header into a range of at most {@link #MAX_CHUNK_SIZE} bytes.
     * A missing header or one not starting with "bytes=" is treated as "bytes=0-";
     * a multi-range header serves its first range only. Returns null when the range
     * is malformed or cannot be satisfied (the caller answers 416).
     */
    private Range parseRangeHeader(String rangeHeader, long fileSize) {
        String spec = (rangeHeader != null && rangeHeader.startsWith("bytes="))
                ? rangeHeader.substring(6).split(",")[0].trim()
                : "0-";
        int dash = spec.indexOf('-');
        if (fileSize <= 0 || dash < 0) {
            return null;
        }
        String startPart = spec.substring(0, dash).trim();
        String endPart = spec.substring(dash + 1).trim();
        try {
            if (startPart.isEmpty()) {
                // suffix form bytes=-K: the last K bytes
                long suffix = Long.parseLong(endPart);
                if (suffix <= 0) {
                    return null;
                }
                long length = Math.min(Math.min(suffix, MAX_CHUNK_SIZE), fileSize);
                return new Range(fileSize - length, fileSize - 1);
            }
            long start = Long.parseLong(startPart);
            if (start < 0 || start >= fileSize) {
                return null;
            }
            if (endPart.isEmpty()) {
                long chunkSize = (start == 0) ? FIRST_CHUNK_SIZE : NEXT_CHUNK_SIZE;
                return new Range(start, Math.min(start + chunkSize - 1, fileSize - 1));
            }
            long end = Long.parseLong(endPart);
            if (end < start) {
                return null;
            }
            return new Range(start, Math.min(Math.min(end, start + MAX_CHUNK_SIZE - 1), fileSize - 1));
        } catch (NumberFormatException numE) {
            log.warn("Invalid range format: {}", rangeHeader);
            return null;
        }
    }
}
