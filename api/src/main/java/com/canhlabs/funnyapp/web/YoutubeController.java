package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.service.YouTubeVideoService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.enums.ResultStatus;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RequestMapping(AppConstant.API.BASE_URL)
@RestController
public class YoutubeController {

    private final com.canhlabs.funnyapp.service.YouTubeVideoService youTubeVideoService;

    public YoutubeController(YouTubeVideoService youTubeVideoService) {
        this.youTubeVideoService = youTubeVideoService;
    }

    @Operation(summary = "Get top videos from YouTube", description = "Fetches a list of top videos from YouTube.")
    @WithSpan
    @GetMapping("/top-videos")
    public ResponseEntity<ResultListInfo<VideoDto>> getTopVideos() {
        List<VideoDto> rs = youTubeVideoService.getVideoIds();
        return new ResponseEntity<>(ResultListInfo.<VideoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }
}