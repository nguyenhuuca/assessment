package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.watchhistory.WatchHistoryItemDto;
import com.canhlabs.funnyapp.dto.watchhistory.WatchProgressRequest;
import com.canhlabs.funnyapp.dto.watchhistory.WatchSummaryDto;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.service.WatchHistoryService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.AppUtils;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The caller's watch history. Authenticated only; the user id always comes from the JWT. */
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/watch-history")
public class WatchHistoryController {

    private final WatchHistoryService watchHistoryService;

    public WatchHistoryController(WatchHistoryService watchHistoryService) {
        this.watchHistoryService = watchHistoryService;
    }

    @Operation(summary = "My watch history", description = "Newest first; q = case-insensitive title contains; size clamped to 1..50.")
    @GetMapping
    @RateLimited(permit = 60)
    public ResponseEntity<ResultObjectInfo<Page<WatchHistoryItemDto>>> list(@RequestParam(required = false) String q,
                                                                           @RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "20") int size) {
        Page<WatchHistoryItemDto> result = watchHistoryService.list(currentUserId(), q, page, size);
        return ResponseEntity.ok(ResultObjectInfo.<Page<WatchHistoryItemDto>>builder()
                .status(ResultStatus.SUCCESS).data(result).build());
    }

    @Operation(summary = "Progress of recent videos", description = "Up to 500 entries, newest first; drives the watched badges.")
    @GetMapping("/summary")
    @RateLimited(permit = 30)
    public ResponseEntity<ResultListInfo<WatchSummaryDto>> summary() {
        List<WatchSummaryDto> data = watchHistoryService.summary(currentUserId());
        return ResponseEntity.ok(ResultListInfo.<WatchSummaryDto>builder().status(ResultStatus.SUCCESS).data(data).build());
    }

    @Operation(summary = "Save playback position", description = "Update-only; invalid values are ignored; no-op in incognito.")
    @PutMapping("/{videoId}/progress")
    @RateLimited(permit = 120)
    public ResponseEntity<Void> progress(@PathVariable Long videoId, @RequestBody WatchProgressRequest request) {
        watchHistoryService.saveProgress(currentUserId(), videoId, request.getPositionSec(), request.getDurationSec());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove one entry", description = "Idempotent.")
    @DeleteMapping("/{videoId}")
    @RateLimited(permit = 20)
    public ResponseEntity<Void> remove(@PathVariable Long videoId) {
        watchHistoryService.remove(currentUserId(), videoId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Clear all history")
    @DeleteMapping
    @RateLimited(permit = 20)
    public ResponseEntity<Void> clear() {
        watchHistoryService.clear(currentUserId());
        return ResponseEntity.noContent().build();
    }

    private static Long currentUserId() {
        UserDetailDto user = AppUtils.getCurrentUser();
        if (user == null || user.getId() == null) {
            throw CustomException.of(HttpStatus.UNAUTHORIZED, 401, "error.auth.required");
        }
        return user.getId();
    }
}
