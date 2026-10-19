package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.HasPermission;
import com.canhlabs.funnyapp.dto.suggestion.ApproveSuggestionRequest;
import com.canhlabs.funnyapp.dto.suggestion.RejectSuggestionRequest;
import com.canhlabs.funnyapp.dto.suggestion.SuggestionDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.enums.SuggestionFilter;
import com.canhlabs.funnyapp.service.VideoSuggestionService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.AppUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/admin/video-suggestions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVideoSuggestionController {

    private final VideoSuggestionService suggestionService;

    public AdminVideoSuggestionController(VideoSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<Page<SuggestionDto>>> list(
            @RequestParam(required = false) SuggestionFilter status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(suggestionService.list(status, page, size));
    }

    @GetMapping("/pending-count")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<Long>> pendingCount() {
        return ok(suggestionService.pendingCount());
    }

    @PostMapping("/{id}/approve")
    @AuditLog("approveVideoSuggestion")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<SuggestionDto>> approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApproveSuggestionRequest request) {
        String title = request == null ? null : request.getTitle();
        return ok(suggestionService.approve(id, title, adminId()));
    }

    @PostMapping("/{id}/reject")
    @AuditLog("rejectVideoSuggestion")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<SuggestionDto>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectSuggestionRequest request) {
        return ok(suggestionService.reject(id, request.getReason(), request.getNote(), adminId()));
    }

    @PostMapping("/{id}/reopen")
    @AuditLog("reopenVideoSuggestion")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<SuggestionDto>> reopen(@PathVariable Long id) {
        return ok(suggestionService.reopen(id, adminId()));
    }

    private static Long adminId() {
        UserDetailDto current = AppUtils.getCurrentUser();
        return current == null ? null : current.getId();
    }

    private static <T> ResponseEntity<ResultObjectInfo<T>> ok(T data) {
        return ResponseEntity.ok(ResultObjectInfo.<T>builder()
                .status(ResultStatus.SUCCESS)
                .data(data)
                .build());
    }
}
