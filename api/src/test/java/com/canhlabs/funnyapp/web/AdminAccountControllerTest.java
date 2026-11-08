package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.admin.AdminAccountDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.service.AdminAccountService;
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
import static org.mockito.Mockito.*;

class AdminAccountControllerTest {

    @Mock AdminAccountService adminAccountService;

    @InjectMocks AdminAccountController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller.injectAdminAccountService(adminAccountService);
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
        var param = AdminAccountController.class.getMethod("getAccounts", org.springframework.data.domain.Pageable.class)
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

    // ── helpers ────────────────────────────────────────────────────────────────

    private static AdminAccountDto adminAccountDto(Long id, UserRole role) {
        AdminAccountDto dto = new AdminAccountDto();
        dto.setId(id);
        dto.setRole(role);
        return dto;
    }
}
