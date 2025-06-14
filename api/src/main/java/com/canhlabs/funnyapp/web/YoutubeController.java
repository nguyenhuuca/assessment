package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.config.aop.AuditLog;
import com.canhlabs.funnyapp.service.YouTubeVideoService;
import com.canhlabs.funnyapp.share.AppConstant;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.share.enums.ResultStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RequestMapping(AppConstant.API.BASE_URL)
@RestController
@AuditLog("Audit all methods in YoutubeController class")
public class YoutubeController {

    private final com.canhlabs.funnyapp.service.YouTubeVideoService youTubeVideoService;

    public YoutubeController(YouTubeVideoService youTubeVideoService) {
        this.youTubeVideoService = youTubeVideoService;
    }

    @GetMapping("/top-videos")
    public ResponseEntity<ResultListInfo<VideoDto>> getTopVideos() {
        List<VideoDto> rs = youTubeVideoService.getVideoIds();
        return new ResponseEntity<>(ResultListInfo.<VideoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }
}