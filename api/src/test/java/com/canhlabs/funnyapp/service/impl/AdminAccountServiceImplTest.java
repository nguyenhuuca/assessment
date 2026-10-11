package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.AdminAccountDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminAccountServiceImplTest {

    @Mock UserRepo userRepo;

    @InjectMocks AdminAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service.injectUserRepo(userRepo);
    }

    // ── getAccounts ───────────────────────────────────────────────────────────

    @Test
    void getAccounts_mapsUserToDto() {
        User user = user(1L, "alice@example.com", UserRole.USER, false);
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepo.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user)));

        Page<AdminAccountDto> result = service.getAccounts(pageable);

        assertEquals(1, result.getContent().size());
        AdminAccountDto dto = result.getContent().get(0);
        assertEquals(1L, dto.getId());
        assertEquals("alice@example.com", dto.getEmail());
        assertEquals(UserRole.USER, dto.getRole());
        assertFalse(dto.isMfaEnabled());
    }

    @Test
    void getAccounts_emptyPage_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepo.findAll(pageable)).thenReturn(Page.empty());

        Page<AdminAccountDto> result = service.getAccounts(pageable);

        assertTrue(result.getContent().isEmpty());
    }

    // ── updateRole ────────────────────────────────────────────────────────────

    @Test
    void updateRole_selfTarget_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateRole(42L, 42L, UserRole.USER));
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateRole_userNotFound_throwsIllegalArgumentException() {
        actor(1L, UserRole.SUPER_ADMIN);
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.updateRole(99L, 1L, UserRole.ADMIN));
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateRole_superAdminPromotesUserToAdmin() {
        actor(1L, UserRole.SUPER_ADMIN);
        User user = target(10L, UserRole.USER);

        service.updateRole(10L, 1L, UserRole.ADMIN);

        assertEquals(UserRole.ADMIN, user.getRole());
        verify(userRepo).save(user);
    }

    @Test
    void updateRole_superAdminDemotesAdminToUser() {
        actor(1L, UserRole.SUPER_ADMIN);
        User user = target(10L, UserRole.ADMIN);

        service.updateRole(10L, 1L, UserRole.USER);

        assertEquals(UserRole.USER, user.getRole());
        verify(userRepo).save(user);
    }

    @Test
    void updateRole_adminCannotAssignAdmin() {
        actor(1L, UserRole.ADMIN);
        target(10L, UserRole.USER);

        assertForbidden(() -> service.updateRole(10L, 1L, UserRole.ADMIN));
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateRole_adminCannotChangeAnotherAdmin() {
        actor(1L, UserRole.ADMIN);
        target(10L, UserRole.ADMIN);

        assertForbidden(() -> service.updateRole(10L, 1L, UserRole.USER));
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateRole_nobodyCanAssignSuperAdmin() {
        actor(1L, UserRole.SUPER_ADMIN);
        target(10L, UserRole.ADMIN);

        assertForbidden(() -> service.updateRole(10L, 1L, UserRole.SUPER_ADMIN));
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateRole_adminCannotTouchSuperAdmin() {
        actor(1L, UserRole.ADMIN);
        target(10L, UserRole.SUPER_ADMIN);

        assertForbidden(() -> service.updateRole(10L, 1L, UserRole.USER));
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateRole_userActorIsForbidden() {
        actor(1L, UserRole.USER);

        assertForbidden(() -> service.updateRole(10L, 1L, UserRole.USER));
        verify(userRepo, never()).save(any());
    }

    // ── deleteAccount ─────────────────────────────────────────────────────────

    @Test
    void deleteAccount_selfTarget_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteAccount(42L, 42L));
        verify(userRepo, never()).deleteById(any());
    }

    @Test
    void deleteAccount_adminDeletesUser() {
        actor(1L, UserRole.ADMIN);
        target(10L, UserRole.USER);

        service.deleteAccount(10L, 1L);

        verify(userRepo).deleteById(10L);
    }

    @Test
    void deleteAccount_adminCannotDeleteAdmin() {
        actor(1L, UserRole.ADMIN);
        target(10L, UserRole.ADMIN);

        assertForbidden(() -> service.deleteAccount(10L, 1L));
        verify(userRepo, never()).deleteById(any());
    }

    @Test
    void deleteAccount_superAdminDeletesAdmin() {
        actor(1L, UserRole.SUPER_ADMIN);
        target(10L, UserRole.ADMIN);

        service.deleteAccount(10L, 1L);

        verify(userRepo).deleteById(10L);
    }

    @Test
    void deleteAccount_adminCannotDeleteSuperAdmin() {
        actor(1L, UserRole.ADMIN);
        target(10L, UserRole.SUPER_ADMIN);

        assertForbidden(() -> service.deleteAccount(10L, 1L));
        verify(userRepo, never()).deleteById(any());
    }

    @Test
    void outranks_followsDeclarationOrder() {
        assertTrue(UserRole.SUPER_ADMIN.outranks(UserRole.ADMIN));
        assertTrue(UserRole.ADMIN.outranks(UserRole.USER));
        assertFalse(UserRole.ADMIN.outranks(UserRole.ADMIN));
        assertFalse(UserRole.USER.outranks(UserRole.ADMIN));
        assertFalse(UserRole.SUPER_ADMIN.outranks(null));
    }

    private void actor(Long id, UserRole role) {
        when(userRepo.findById(id)).thenReturn(Optional.of(user(id, "actor@example.com", role, false)));
    }

    private User target(Long id, UserRole role) {
        User u = user(id, "target@example.com", role, false);
        when(userRepo.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private static void assertForbidden(org.junit.jupiter.api.function.Executable call) {
        CustomException e = assertThrows(CustomException.class, call);
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static User user(Long id, String email, UserRole role, boolean mfaEnabled) {
        User u = new User();
        u.setId(id);
        u.setUserName(email);
        u.setRole(role);
        u.setMfaEnabled(mfaEnabled);
        return u;
    }
}
