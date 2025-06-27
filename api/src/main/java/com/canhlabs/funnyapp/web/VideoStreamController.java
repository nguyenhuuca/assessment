package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.service.StorageVideoService;
import com.canhlabs.funnyapp.share.AppConstant;
import com.canhlabs.funnyapp.share.enums.ResultStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
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
import java.util.List;

@Slf4j
@RequestMapping(AppConstant.API.BASE_URL + "/video-stream")
@RestController
public class VideoStreamController {
    private final StorageVideoService videoService;

    public VideoStreamController(StorageVideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping("/stream/{fileId}")
    public ResponseEntity<StreamingResponseBody> streamVideo(
            @PathVariable String fileId,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("StreamVideo Thread: {}, isVirtual: {}", Thread.currentThread().getName(), Thread.currentThread().isVirtual());
        log.info("Starting time: {} for fileID {}", System.currentTimeMillis() ,fileId);
        long fileSize = videoService.getFileSize(fileId);
        long start = 0;
        long end = fileSize - 1;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] ranges = rangeHeader.substring(6).split("-");
            log.info("Range header received: {}", rangeHeader);
            start = Long.parseLong(ranges[0]);
            if (ranges.length > 1 && !ranges[1].isEmpty()) {
                if(start == 0) {
                    log.info("Start range is 0, setting end to 1MB limit");
                    long endRangeRequested = Long.parseLong(ranges[1]);
                    log.info("End range requested: {}", endRangeRequested);
                    if (endRangeRequested >= 1_000_000L) {
                        log.warn("Range requested is too large: {} bytes, limiting to 1MB", endRangeRequested);
                        end =  1_000_000L; // limit
                    } else {
                        log.info("End range requested < 1_000_000L: {}", endRangeRequested);
                        end = endRangeRequested;
                    }
                } else {
                    end = Long.parseLong(ranges[1]);
                }
            } else {
                end = 1024 * 1024;
            }
        }

        InputStream stream = videoService.getPartialFile(fileId, start, end);
        StreamingResponseBody responseBody = outputStream -> {
            byte[] buffer = new byte[1024 * 1024]; // ⚠️ buffer lớn: 1MB
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();
            }
            stream.close();
        };
        // InputStreamResource inputStreamResource = new InputStreamResource(stream);
        log.info("End time: {}, Duration: {} ms for fileID {}", System.currentTimeMillis(), System.currentTimeMillis() - startTime, fileId);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(end - start + 1))
                .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize))
                .body(responseBody);
    }

    @GetMapping("/list")
    public ResponseEntity<ResultListInfo<VideoDto>> getTopVideos() {
        log.info("getTopVideos Thread: {}, isVirtual: {}", Thread.currentThread().getName(), Thread.currentThread().isVirtual());
        List<VideoDto> rs = videoService.getVideosToStream();
        return new ResponseEntity<>(ResultListInfo.<VideoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResultObjectInfo<VideoDto>> getTopVideos(@PathVariable  Long id) {
        videoService.shareFilesInFolder();
        VideoDto rs = videoService.getVideoById(id);
        return new ResponseEntity<>(ResultObjectInfo.<VideoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }
}