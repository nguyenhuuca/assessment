package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.service.ShareService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.dto.ShareRequestDto;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.enums.ResultStatus;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(AppConstant.API.BASE_URL)
@Validated
@AuditLog("Audit all methods in ShareLinkController class")
@Slf4j
public class ShareLinkController {
    private ShareService shareService;

    @Autowired
    public void injectUser(ShareService shareService) {
        this.shareService = shareService;
    }

    /**
     * Using to save link post by user
     * @param shareRequestDto hold share url
     * @return detail for video that user shared.
     */
    @Operation(summary = "Share a video link", description = "Allows users to share a video link by providing the URL and other details.")
    @WithSpan
    @PostMapping("/share-links")
    public ResponseEntity<ResultObjectInfo<VideoDto>> shareLink(@RequestBody ShareRequestDto shareRequestDto) {
        VideoDto rs = shareService.shareLink(shareRequestDto);
        return new ResponseEntity<>(ResultObjectInfo.<VideoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }

    /**
     * Get all shared link return to client
     * @return detail of video that user shared
     */
    @Operation(summary = "Get all shared links", description = "Fetches all shared video links.")
    @WithSpan
    @GetMapping("/share-links")
    public ResponseEntity<ResultListInfo<VideoDto>> getShareLink() {
        List<VideoDto> rs = shareService.getALLShare();
        return new ResponseEntity<>(ResultListInfo.<VideoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }


    @Operation(summary = "Get private shared links", description = "Fetches all private shared video links.")
    @WithSpan
    @GetMapping("/private-videos")
    public ResponseEntity<ResultListInfo<VideoDto>> getPrivateShareLink() {
        List<VideoDto> rs = shareService.getALLShare();
        return new ResponseEntity<>(ResultListInfo.<VideoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }

    @Operation(summary = "Delete a shared link", description = "Deletes a shared video link by its ID.")
    @WithSpan
    @DeleteMapping("/share-links/{videoId}")
    public ResponseEntity<ResultObjectInfo<String>> deleteShareLink(@PathVariable("videoId") Long videoId) {
        String rs = shareService.deleteShareLink(videoId);
        return new ResponseEntity<>(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message(rs)
                .build(), HttpStatus.OK);
    }
}
