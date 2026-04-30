package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.AdminAccountDto;
import com.canhlabs.funnyapp.dto.AdminStatsDto;
import com.canhlabs.funnyapp.dto.AdminVideoDto;
import com.canhlabs.funnyapp.dto.UserDetailDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.enums.VideoStatus;
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
        doNothing().when(adminVideoService).updateStatus(1L, VideoStatus.FLAGGED);

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

    // ── DELETE /videos/{id} ────────────────────────────────────────────────────

    @Test
    void deleteVideo_returnsOk() {
        doNothing().when(adminVideoService).deleteVideo(3L);

        ResponseEntity<ResultObjectInfo<String>> response = controller.deleteVideo(3L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        verify(adminVideoService).deleteVideo(3L);
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
