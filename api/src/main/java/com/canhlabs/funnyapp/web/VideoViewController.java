package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.service.VideoViewService;
import com.canhlabs.funnyapp.service.WatchHistoryService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.AppUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/videos/{videoId}/view")
public class VideoViewController {

    private final VideoViewService videoViewService;

    private final WatchHistoryService watchHistoryService;

    public VideoViewController(VideoViewService videoViewService, WatchHistoryService watchHistoryService) {
        this.videoViewService = videoViewService;
        this.watchHistoryService = watchHistoryService;
    }

    @Operation(summary = "Record a view", description = "Public. The player calls this after 3 s of playback; "
            + "counted once per viewer (user, or client IP for guests) per video per 30 min.")
    @PostMapping
    public ResponseEntity<Void> record(@PathVariable Long videoId, HttpServletRequest request) {
        videoViewService.recordView(videoId, viewerKey(request));
        recordHistory(videoId);
        return ResponseEntity.noContent().build();
    }

    /** Logged-in viewers also get a history entry (not gated by the view dedupe); never fails the view. */
    private void recordHistory(Long videoId) {
        UserDetailDto user = AppUtils.getCurrentUser();
        if (user == null || user.getId() == null) {
            return;
        }
        try {
            watchHistoryService.recordWatch(user.getId(), videoId);
        } catch (RuntimeException e) {
            log.warn("Watch history not recorded for video {}: {}", videoId, e.getClass().getSimpleName());
        }
    }

    static String viewerKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "USER:" + auth.getName();
        }
        return "IP:" + clientIp(request);
    }

    /** Behind Cloudflare → nginx the socket address is the proxy, so prefer the forwarded client IP. */
    static String clientIp(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
