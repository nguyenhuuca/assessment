package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.suggestion.SubmitSuggestionRequest;
import com.canhlabs.funnyapp.dto.suggestion.SuggestionDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.enums.SuggestionFilter;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.service.VideoSuggestionService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.AppUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logged-in users suggest videos and follow their own suggestions. Deliberately not in any public path
 * list: the JWT filter answers 401 for guests.
 */
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/suggestions")
public class VideoSuggestionController {

    private final VideoSuggestionService suggestionService;

    public VideoSuggestionController(VideoSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @PostMapping
    @RateLimited(permit = 10)
    @AuditLog("submitVideoSuggestion")
    public ResponseEntity<ResultObjectInfo<SuggestionDto>> submit(@Valid @RequestBody SubmitSuggestionRequest request) {
        SuggestionDto dto = suggestionService.submit(currentUserId(), request.getUrl(), request.getTitle(), request.getNote());
        return ResponseEntity.status(HttpStatus.CREATED).body(wrap(dto));
    }

    @GetMapping("/mine")
    @RateLimited(permit = 60)
    public ResponseEntity<ResultObjectInfo<Page<SuggestionDto>>> mine(
            @RequestParam(required = false) SuggestionFilter status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(wrap(suggestionService.mine(currentUserId(), status, page, size)));
    }

    private static Long currentUserId() {
        UserDetailDto user = AppUtils.getCurrentUser();
        if (user == null || user.getId() == null) {
            throw CustomException.of(HttpStatus.UNAUTHORIZED, 4011, "error.auth.required");
        }
        return user.getId();
    }

    private static <T> ResultObjectInfo<T> wrap(T data) {
        return ResultObjectInfo.<T>builder().status(ResultStatus.SUCCESS).data(data).build();
    }
}
