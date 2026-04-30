package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.AdminAccountDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.UserRole;
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
                () -> service.updateRole(42L, 42L, UserRole.ADMIN));
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateRole_userNotFound_throwsIllegalArgumentException() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.updateRole(99L, 1L, UserRole.ADMIN));
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateRole_success_updatesRoleAndSaves() {
        User user = user(10L, "bob@example.com", UserRole.USER, false);
        when(userRepo.findById(10L)).thenReturn(Optional.of(user));

        service.updateRole(10L, 1L, UserRole.ADMIN);

        assertEquals(UserRole.ADMIN, user.getRole());
        verify(userRepo).save(user);
    }

    @Test
    void updateRole_demoteFromAdmin_updatesRoleAndSaves() {
        User user = user(10L, "bob@example.com", UserRole.ADMIN, false);
        when(userRepo.findById(10L)).thenReturn(Optional.of(user));

        service.updateRole(10L, 1L, UserRole.USER);

        assertEquals(UserRole.USER, user.getRole());
        verify(userRepo).save(user);
    }

    // ── deleteAccount ─────────────────────────────────────────────────────────

    @Test
    void deleteAccount_selfTarget_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteAccount(42L, 42L));
        verify(userRepo, never()).deleteById(any());
    }

    @Test
    void deleteAccount_differentUser_callsDeleteById() {
        service.deleteAccount(10L, 1L);
        verify(userRepo).deleteById(10L);
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
