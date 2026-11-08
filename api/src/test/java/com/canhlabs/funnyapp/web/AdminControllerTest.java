package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.admin.AdminAccountDto;
import com.canhlabs.funnyapp.dto.admin.AdminHlsStatusDto;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.dto.admin.AdminStatsDto;
import com.canhlabs.funnyapp.dto.admin.AdminVideoDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.enums.VideoVisibility;
import com.canhlabs.funnyapp.service.AdminAccountService;
import com.canhlabs.funnyapp.service.AdminVideoService;
import com.canhlabs.funnyapp.utils.AppUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminControllerTest {

    @Mock AdminVideoService adminVideoService;
    @Mock AdminAccountService adminAccountService;

    @InjectMocks AdminController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller.injectAdminVideoService(adminVideoService);
        controller.injectAdminAccountService(adminAccountService);
    }

    // ── GET /videos ────────────────────────────────────────────────────────────

    @Test
    void getVideos_noStatusFilter_returnsOkWithPage() {
        Page<AdminVideoDto> page = new PageImpl<>(List.of(adminVideoDto(1L, VideoStatus.PUBLISHED)));
        when(adminVideoService.getVideos(any(), eq(null))).thenReturn(page);

        ResponseEntity<ResultObjectInfo<Page<AdminVideoDto>>> response =
                controller.getVideos(PageRequest.of(0, 20), null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(1, response.getBody().getData().getContent().size());
    }

    @Test
    void getVideos_withStatusFilter_passesStatusToService() {
        when(adminVideoService.getVideos(any(), eq(VideoStatus.FLAGGED))).thenReturn(Page.empty());

        controller.getVideos(PageRequest.of(0, 20), VideoStatus.FLAGGED);

        verify(adminVideoService).getVideos(any(), eq(VideoStatus.FLAGGED));
    }

    // ── PATCH /videos/{id}/status ─────────────────────────────────────────────

    @Test
    void updateVideoStatus_validStatus_returnsOk() {
        when(adminVideoService.updateStatus(1L, VideoStatus.FLAGGED)).thenReturn(true);

        ResponseEntity<ResultObjectInfo<String>> response =
                controller.updateVideoStatus(1L, Map.of("status", "FLAGGED"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        verify(adminVideoService).updateStatus(1L, VideoStatus.FLAGGED);
    }

    @Test
    void updateVideoStatus_invalidStatus_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.updateVideoStatus(1L, Map.of("status", "INVALID_STATUS")));
    }

    // ── PUT / DELETE /videos/{id}/hls ─────────────────────────────────────────

    @Test
    void enableVideoHls_returnsStatusFromService() {
        AdminHlsStatusDto dto = AdminHlsStatusDto.builder().id(42L).hlsStatus(HlsStatus.PENDING).build();
        when(adminVideoService.enableHls(42L)).thenReturn(dto);

        ResponseEntity<ResultObjectInfo<AdminHlsStatusDto>> response = controller.enableVideoHls(42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertSame(dto, response.getBody().getData());
    }

    @Test
    void disableVideoHls_returnsStatusFromService() {
        AdminHlsStatusDto dto = AdminHlsStatusDto.builder().id(42L).hlsStatus(HlsStatus.NONE).build();
        when(adminVideoService.disableHls(42L)).thenReturn(dto);

        ResponseEntity<ResultObjectInfo<AdminHlsStatusDto>> response = controller.disableVideoHls(42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody().getData());
    }

    // ── PATCH /videos/{id}/priority ───────────────────────────────────────────

    @Test
    void updateVideoPriority_validValue_returnsOk() {
        doNothing().when(adminVideoService).updatePriority(1L, 50);

        ResponseEntity<ResultObjectInfo<String>> response =
                controller.updateVideoPriority(1L, Map.of("priority", 50));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        verify(adminVideoService).updatePriority(1L, 50);
    }

    // ── PATCH /videos/{id}/details ────────────────────────────────────────────

    @Test
    void updateVideoDetails_valid_delegatesAndReturnsOk() {
        ResponseEntity<ResultObjectInfo<String>> response =
                controller.updateVideoDetails(1L, Map.of("title", "T", "description", "D"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Details updated", response.getBody().getMessage());
        verify(adminVideoService).updateDetails(1L, "T", "D");
    }

    @Test
    void updateVideoDetails_missingDescription_passesNull() {
        controller.updateVideoDetails(1L, Map.of("title", "T"));

        verify(adminVideoService).updateDetails(1L, "T", null);
    }

    @Test
    void updateVideoDetails_nullBodyOrBadTypes_rejectedWithoutService() {
        assertThrows(RuntimeException.class, () -> controller.updateVideoDetails(1L, null));
        assertThrows(RuntimeException.class, () -> controller.updateVideoDetails(1L, Map.of()));
        assertThrows(RuntimeException.class, () -> controller.updateVideoDetails(1L, Map.of("title", 5)));
        assertThrows(RuntimeException.class,
                () -> controller.updateVideoDetails(1L, Map.of("title", "T", "description", 7)));
        verify(adminVideoService, never()).updateDetails(any(), any(), any());
    }

    // ── PATCH /videos/{id}/visibility ─────────────────────────────────────────

    @Test
    void updateVideoVisibility_eachValue_callsService() {
        for (VideoVisibility v : VideoVisibility.values()) {
            ResponseEntity<ResultObjectInfo<String>> response =
                    controller.updateVideoVisibility(1L, Map.of("visibility", v.name()));

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(adminVideoService).updateVisibility(1L, v);
        }
    }

    // CO-F1: the legacy {hidden} body is no longer accepted
    @Test
    void updateVideoVisibility_legacyHiddenBody_isRejected() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> controller.updateVideoVisibility(1L, Map.of("hidden", true)));

        assertEquals("visibility is required", e.getMessage());
        verifyNoInteractions(adminVideoService);
    }

    @Test
    void updateVideoVisibility_nullOrEmptyBody_isRejected() {
        RuntimeException nullBody = assertThrows(RuntimeException.class, () -> controller.updateVideoVisibility(1L, null));
        RuntimeException empty = assertThrows(RuntimeException.class, () -> controller.updateVideoVisibility(1L, Map.of()));

        assertEquals("visibility is required", nullBody.getMessage());
        assertEquals("visibility is required", empty.getMessage());
        verifyNoInteractions(adminVideoService);
    }

    @Test
    void updateVideoVisibility_unknownValue_isRejected() {
        RuntimeException unknown = assertThrows(RuntimeException.class,
                () -> controller.updateVideoVisibility(1L, Map.of("visibility", "feed")));
        RuntimeException notString = assertThrows(RuntimeException.class,
                () -> controller.updateVideoVisibility(1L, Map.of("visibility", true)));

        assertEquals("invalid visibility", unknown.getMessage());
        assertEquals("invalid visibility", notString.getMessage());
        verifyNoInteractions(adminVideoService);
    }

    // ── DELETE /videos/{id} ────────────────────────────────────────────────────

    @Test
    void deleteVideo_returnsOk() {
        doNothing().when(adminVideoService).deleteVideo(3L, false, false);

        ResponseEntity<ResultObjectInfo<String>> response = controller.deleteVideo(3L, false, false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        verify(adminVideoService).deleteVideo(3L, false, false);
    }

    @Test
    void deleteVideo_passesFileFlags() {
        controller.deleteVideo(3L, true, true);

        verify(adminVideoService).deleteVideo(3L, true, true);
    }

    // ── GET /accounts ──────────────────────────────────────────────────────────

    @Test
    void getAccounts_returnsOkWithPage() {
        Page<AdminAccountDto> page = new PageImpl<>(List.of(adminAccountDto(1L, UserRole.USER)));
        when(adminAccountService.getAccounts(any())).thenReturn(page);

        ResponseEntity<ResultObjectInfo<Page<AdminAccountDto>>> response =
                controller.getAccounts(PageRequest.of(0, 20));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().getContent().size());
    }

    @Test
    void getAccounts_defaultsToStableIdSort() throws NoSuchMethodException {
        // Regression: no default sort meant no ORDER BY, so an account moved after its role/permissions changed
        var param = AdminController.class.getMethod("getAccounts", org.springframework.data.domain.Pageable.class)
                .getParameters()[0];
        var defaults = param.getAnnotation(org.springframework.data.web.PageableDefault.class);
        assertNotNull(defaults);
        assertArrayEquals(new String[]{"id"}, defaults.sort());
        assertEquals(org.springframework.data.domain.Sort.Direction.ASC, defaults.direction());
    }

    // ── PATCH /accounts/{id}/role ──────────────────────────────────────────────

    @Test
    void updateAccountRole_returnsOk() {
        UserDetailDto currentUser = UserDetailDto.builder().id(99L).email("admin@example.com").build();
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getCurrentUser).thenReturn(currentUser);
            doNothing().when(adminAccountService).updateRole(10L, 99L, UserRole.ADMIN);

            ResponseEntity<ResultObjectInfo<String>> response =
                    controller.updateAccountRole(10L, Map.of("role", "ADMIN"));

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
            verify(adminAccountService).updateRole(10L, 99L, UserRole.ADMIN);
        }
    }

    @Test
    void updateAccountPermissions_returnsUpdatedAccount() {
        UserDetailDto currentUser = UserDetailDto.builder().id(99L).email("admin@example.com").build();
        AdminAccountDto updated = AdminAccountDto.builder().id(10L).permissions(17).build();
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getCurrentUser).thenReturn(currentUser);
            when(adminAccountService.updatePermissions(10L, 99L, 17)).thenReturn(updated);

            ResponseEntity<ResultObjectInfo<AdminAccountDto>> response =
                    controller.updateAccountPermissions(10L, Map.of("permissions", 17));

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(17, response.getBody().getData().getPermissions());
        }
    }

    @Test
    void updateAccountPermissions_missingValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.updateAccountPermissions(10L, Map.of()));
        verifyNoInteractions(adminAccountService);
    }

    @Test
    void updateAccountRole_invalidRole_throwsIllegalArgumentException() {
        UserDetailDto currentUser = UserDetailDto.builder().id(99L).email("admin@example.com").build();
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getCurrentUser).thenReturn(currentUser);

            assertThrows(IllegalArgumentException.class,
                    () -> controller.updateAccountRole(10L, Map.of("role", "SUPERUSER")));
        }
    }

    // ── DELETE /accounts/{id} ──────────────────────────────────────────────────

    @Test
    void deleteAccount_returnsOk() {
        UserDetailDto currentUser = UserDetailDto.builder().id(99L).email("admin@example.com").build();
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getCurrentUser).thenReturn(currentUser);
            doNothing().when(adminAccountService).deleteAccount(10L, 99L);

            ResponseEntity<ResultObjectInfo<String>> response = controller.deleteAccount(10L);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
            verify(adminAccountService).deleteAccount(10L, 99L);
        }
    }

    // ── GET /stats ─────────────────────────────────────────────────────────────

    @Test
    void getStats_returnsOkWithStats() {
        AdminStatsDto stats = AdminStatsDto.builder()
                .totalVideos(100L).totalUsers(50L).pendingCount(5L).flaggedCount(3L).build();
        when(adminVideoService.getStats()).thenReturn(stats);

        ResponseEntity<ResultObjectInfo<AdminStatsDto>> response = controller.getStats();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(100L, response.getBody().getData().getTotalVideos());
        assertEquals(50L,  response.getBody().getData().getTotalUsers());
        assertEquals(5L,   response.getBody().getData().getPendingCount());
        assertEquals(3L,   response.getBody().getData().getFlaggedCount());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static AdminVideoDto adminVideoDto(Long id, VideoStatus status) {
        AdminVideoDto dto = new AdminVideoDto();
        dto.setId(id);
        dto.setStatus(status);
        return dto;
    }

    private static AdminAccountDto adminAccountDto(Long id, UserRole role) {
        AdminAccountDto dto = new AdminAccountDto();
        dto.setId(id);
        dto.setRole(role);
        return dto;
    }
}
