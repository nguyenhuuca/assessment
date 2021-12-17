package com.canhlabs.assessment.web;

import com.canhlabs.assessment.service.ShareService;
import com.canhlabs.assessment.share.AppConstant;
import com.canhlabs.assessment.share.ResultListInfo;
import com.canhlabs.assessment.share.ResultObjectInfo;
import com.canhlabs.assessment.share.dto.ShareRequestDto;
import com.canhlabs.assessment.share.dto.VideoDto;
import com.canhlabs.assessment.share.enums.ResultStatus;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(AppConstant.API.BASE_URL)
@Api(tags = {AppConstant.API.TAG_API})
@Validated
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
    @GetMapping("/share-links")
    public ResponseEntity<ResultListInfo<VideoDto>> getShareLink() {
        List<VideoDto> rs = shareService.getALLShare();
        return new ResponseEntity<>(ResultListInfo.<VideoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }

    @DeleteMapping("/share-links/{id}")
    public ResponseEntity<ResultObjectInfo<String>> deleteVideos(@PathVariable("id") Long id) {
        shareService.deleteVideo(id);
        return new ResponseEntity<>(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .data("")
                .build(), HttpStatus.OK);

    }

    /**
     * Get all shared link return to client
     * @return detail of video that user shared
     */
    @PostMapping("/send-info")
    public ResponseEntity<ResultObjectInfo<String>> sendInfo(@RequestBody String message) {
        shareService.sendInfo(message);
        return new ResponseEntity<>(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .data(message)
                .build(), HttpStatus.OK);
    }
}
