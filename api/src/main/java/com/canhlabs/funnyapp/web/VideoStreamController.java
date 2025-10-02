package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.Range;
import com.canhlabs.funnyapp.dto.StreamChunkResult;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.StreamVideoService;
import com.canhlabs.funnyapp.utils.AppConstant;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;

@Slf4j
@RequestMapping(AppConstant.API.BASE_URL + "/video-stream")
@RestController
public class VideoStreamController {
    private static final long FIRST_CHUNK_SIZE = 256 * 1024L; // 256KB
    private static final long NEXT_CHUNK_SIZE = 512 * 1024L;  // 512KB
    private final StreamVideoService videoService;

    public VideoStreamController(StreamVideoService videoService) {
        this.videoService = videoService;
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
        long start = 0;
        long end = fileSize - 1;

        Range range = parseRangeHeader(rangeHeader, fileSize);
        if (range != null) {
            start = range.start();
            end = range.end();
            log.info("Streaming bytes {} to {}", start, end);
        }
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

    private Range parseRangeHeader(String rangeHeader, long fileSize) {
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            log.info("Range header: {}", rangeHeader);
            String[] ranges = rangeHeader.substring(6).split("-");
            try {
                long start = Long.parseLong(ranges[0]);
                long end;
                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    end = Math.min(Long.parseLong(ranges[1]), fileSize - 1);
                } else {
                    long chunkSize = (start == 0) ? FIRST_CHUNK_SIZE : NEXT_CHUNK_SIZE;
                    end = Math.min(start + chunkSize - 1, fileSize - 1);
                }
                return new Range(start, end);
            } catch (NumberFormatException numE) {
                log.error("NumberFormatException", numE);
                log.warn("Invalid range format: {}, fallback to full stream", rangeHeader);
            }
        }
        return null;
    }
}