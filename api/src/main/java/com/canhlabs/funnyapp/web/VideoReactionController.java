package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.reaction.ReactionRequest;
import com.canhlabs.funnyapp.dto.reaction.ReactionSummaryDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.VideoReactionService;
import com.canhlabs.funnyapp.utils.AppConstant;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/videos/{videoId}/reaction")
public class VideoReactionController {

    private final VideoReactionService reactionService;

    public VideoReactionController(VideoReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @Operation(summary = "Get reaction summary", description = "Public. Returns like/dislike counts; myReaction is set only when a valid JWT is supplied.")
    @GetMapping
    public ResponseEntity<ResultObjectInfo<ReactionSummaryDto>> get(@PathVariable Long videoId) {
        return ok(reactionService.getSummary(videoId));
    }

    @Operation(summary = "Like or dislike a video", description = "Sets (or switches) the authenticated user's reaction.")
    @PutMapping
    @RateLimited(permit = 30)
    public ResponseEntity<ResultObjectInfo<ReactionSummaryDto>> set(@PathVariable Long videoId,
                                                                    @Valid @RequestBody ReactionRequest request) {
        return ok(reactionService.react(videoId, request.getReaction()));
    }

    @Operation(summary = "Remove reaction", description = "Clears the authenticated user's reaction (idempotent).")
    @DeleteMapping
    @RateLimited(permit = 30)
    public ResponseEntity<ResultObjectInfo<ReactionSummaryDto>> remove(@PathVariable Long videoId) {
        return ok(reactionService.removeReaction(videoId));
    }

    private static ResponseEntity<ResultObjectInfo<ReactionSummaryDto>> ok(ReactionSummaryDto dto) {
        return new ResponseEntity<>(ResultObjectInfo.<ReactionSummaryDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(dto)
                .build(), HttpStatus.OK);
    }
}
