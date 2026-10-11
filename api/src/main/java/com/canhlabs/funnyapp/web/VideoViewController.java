package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.service.VideoViewService;
import com.canhlabs.funnyapp.utils.AppConstant;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/videos/{videoId}/view")
public class VideoViewController {

    private final VideoViewService videoViewService;

    public VideoViewController(VideoViewService videoViewService) {
        this.videoViewService = videoViewService;
    }

    @Operation(summary = "Record a view", description = "Public. The player calls this after 3 s of playback; "
            + "counted once per viewer (user, or client IP for guests) per video per 30 min.")
    @PostMapping
    public ResponseEntity<Void> record(@PathVariable Long videoId, HttpServletRequest request) {
        videoViewService.recordView(videoId, viewerKey(request));
        return ResponseEntity.noContent().build();
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
