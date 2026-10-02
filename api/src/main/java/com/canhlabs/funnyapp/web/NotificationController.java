package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.notification.NotificationDto;
import com.canhlabs.funnyapp.dto.notification.UnreadCountDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.service.NotificationService;
import com.canhlabs.funnyapp.service.notification.NotificationStream;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.AppUtils;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Owner-only notification inbox. Deliberately NOT listed in WHITE_LIST_PATH / OPTIONAL_AUTH_PATH /
 * ALLOW_ALL_METHOD: the JWT filter must reject every call (including the stream) without a valid token.
 */
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/notifications")
public class NotificationController {

    static final int MAX_PAGE_SIZE = 50;

    private final NotificationService notificationService;
    private final NotificationStream notificationStream;

    public NotificationController(NotificationService notificationService, NotificationStream notificationStream) {
        this.notificationService = notificationService;
        this.notificationStream = notificationStream;
    }

    @Operation(summary = "List my notifications", description = "Newest activity first; own rows only.")
    @GetMapping
    public ResponseEntity<ResultObjectInfo<Page<NotificationDto>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Fixed ordering (updated_at DESC) lives in the repository; client-supplied sort is not accepted
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return ok(notificationService.list(pageable));
    }

    @Operation(summary = "Unread notification count")
    @GetMapping("/unread-count")
    public ResponseEntity<ResultObjectInfo<UnreadCountDto>> unreadCount() {
        return ok(UnreadCountDto.builder().count(notificationService.unreadCount()).build());
    }

    @Operation(summary = "Notification stream (SSE)",
            description = "Requires the Authorization header. Emits `unread` events and `ping` comments.")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        UserDetailDto user = AppUtils.getCurrentUser();
        if (user == null || user.getId() == null) {
            throw CustomException.builder().status(HttpStatus.UNAUTHORIZED).subCode(4011)
                    .message("Authentication required").build();
        }
        SseEmitter emitter = notificationStream.register(user.getId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @Operation(summary = "Mark one notification as read", description = "404 when it is not yours.")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark all my notifications as read")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<ResultObjectInfo<T>> ok(T data) {
        return ResponseEntity.ok(ResultObjectInfo.<T>builder().status(ResultStatus.SUCCESS).data(data).build());
    }
}
