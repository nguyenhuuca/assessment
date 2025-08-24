package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.CommentNode;
import com.canhlabs.funnyapp.dto.CreateCommentRequest;
import com.canhlabs.funnyapp.dto.CreateCommentResponse;
import com.canhlabs.funnyapp.service.impl.VideoCommentServiceImpl;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(AppConstant.API.BASE_URL)
public class CommentController {

    private final VideoCommentServiceImpl service;

    public CommentController(VideoCommentServiceImpl service) {
        this.service = service;
    }

    @GetMapping("/videos/{videoId}/comments")
    public ResponseEntity<List<CommentNode>> getComments(@PathVariable String videoId) {
        return ResponseEntity.ok(service.getNestedComments(videoId));
    }

    @RateLimited(permit = 5, windowInSeconds = 60) // 5 requests per minute
    @PostMapping("/videos/{videoId}/comments")
    public ResponseEntity<CreateCommentResponse> create(
            @PathVariable String videoId,
            @Valid @RequestBody CreateCommentRequest req
    ) {
        return ResponseEntity.ok(service.createComment(videoId, req));
    }

    /**
     * DELETE is publicly reachable; service enforces auth/guest token policy.
     * Guests must send header: X-Guest-Token: <token>
     */
    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken
    ) {
        service.deleteComment(UUID.fromString(id), guestToken);
        return ResponseEntity.noContent().build();
    }
}