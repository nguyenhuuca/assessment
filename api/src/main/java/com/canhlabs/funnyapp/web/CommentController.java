package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.CommentNode;
import com.canhlabs.funnyapp.dto.CreateCommentRequest;
import com.canhlabs.funnyapp.dto.CreateCommentResponse;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.impl.VideoCommentServiceImpl;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(AppConstant.API.BASE_URL)
public class CommentController {

    private final VideoCommentServiceImpl service;

    public CommentController(VideoCommentServiceImpl service) {
        this.service = service;
    }

    @GetMapping("/videos/{videoId}/comments")
    public ResponseEntity<ResultListInfo<CommentNode>> getComments(@PathVariable String videoId) {
        return new ResponseEntity<>(ResultListInfo.<CommentNode>builder()
                .status(ResultStatus.SUCCESS)
                .data(service.getNestedComments(videoId))
                .build(), HttpStatus.OK);
    }

    @RateLimited(permit = 5) // 5 requests per minute
    @PostMapping("/videos/{videoId}/comments")
    public ResponseEntity<ResultObjectInfo<CreateCommentResponse>> create(
            @PathVariable String videoId,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken,
            @Valid @RequestBody CreateCommentRequest req
    ) {
            return new ResponseEntity<>(ResultObjectInfo.<CreateCommentResponse>builder()
                .status(ResultStatus.SUCCESS)
                .data(service.createComment(videoId, req, guestToken))
                .build(), HttpStatus.OK);
    }

    /**
     * DELETE is publicly reachable; service enforces auth/guest token policy.
     * Guests must send header: X-Guest-Token: <token>
     */
    @DeleteMapping("/videos/{videoId}/comments/{id}")
    public ResponseEntity<ResultObjectInfo<String>> delete(
            @PathVariable String videoId,
            @PathVariable String id,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken
    ) {
        service.deleteComment(UUID.fromString(id), guestToken);
        return new ResponseEntity<>(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .data("Comment deleted")
                .build(), HttpStatus.OK);
    }
}