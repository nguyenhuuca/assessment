package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class PermissionServiceImplTest {

    @Mock
    private FeatureFlagService featureFlagService;

    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PermissionServiceImpl();
        service.injectFeatureFlagService(featureFlagService);
        // default: flag ON so existing tests exercise the bitwise logic
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean()))
                .thenReturn(true);
    }

    // ── hasPermission ──────────────────────────────────────────────────────────

    @Test
    void hasPermission_userHasBit_returnsTrue() {
        assertThat(service.hasPermission(Permission.READ.getBit(), Permission.READ)).isTrue();
    }

    @Test
    void hasPermission_userMissesBit_returnsFalse() {
        assertThat(service.hasPermission(Permission.READ.getBit(), Permission.WRITE)).isFalse();
    }

    @Test
    void hasPermission_zeroPerm_returnsFalse() {
        assertThat(service.hasPermission(0, Permission.ADMIN)).isFalse();
    }

    @Test
    void hasPermission_combinedBits_detectsEachIndividually() {
        int combined = Permission.READ.getBit() | Permission.DELETE.getBit();
        assertThat(service.hasPermission(combined, Permission.READ)).isTrue();
        assertThat(service.hasPermission(combined, Permission.DELETE)).isTrue();
        assertThat(service.hasPermission(combined, Permission.WRITE)).isFalse();
        assertThat(service.hasPermission(combined, Permission.EXEC)).isFalse();
        assertThat(service.hasPermission(combined, Permission.ADMIN)).isFalse();
    }

    // ── hasAllPermissions ──────────────────────────────────────────────────────

    @Test
    void hasAllPermissions_userHasAllBits_returnsTrue() {
        int perm = Permission.READ.getBit() | Permission.WRITE.getBit();
        assertThat(service.hasAllPermissions(perm, new Permission[]{Permission.READ, Permission.WRITE})).isTrue();
    }

    @Test
    void hasAllPermissions_userMissingOneBit_returnsFalse() {
        int perm = Permission.READ.getBit();
        assertThat(service.hasAllPermissions(perm, new Permission[]{Permission.READ, Permission.WRITE})).isFalse();
    }

    @Test
    void hasAllPermissions_emptyArray_returnsTrue() {
        assertThat(service.hasAllPermissions(0, new Permission[]{})).isTrue();
    }

    @Test
    void hasAllPermissions_adminOnlyBit_adminRequiredPasses() {
        int perm = Permission.ADMIN.getBit();
        assertThat(service.hasAllPermissions(perm, new Permission[]{Permission.ADMIN})).isTrue();
    }

    @Test
    void hasAllPermissions_allPermissions_returnsTrue() {
        int allBits = 0;
        for (Permission p : Permission.values()) allBits |= p.getBit();
        assertThat(service.hasAllPermissions(allBits, Permission.values())).isTrue();
    }

    // ── hasAnyPermission ───────────────────────────────────────────────────────

    @Test
    void hasAnyPermission_userHasOneBit_returnsTrue() {
        int perm = Permission.EXEC.getBit();
        assertThat(service.hasAnyPermission(perm, new Permission[]{Permission.READ, Permission.EXEC})).isTrue();
    }

    @Test
    void hasAnyPermission_userMissingAllBits_returnsFalse() {
        int perm = Permission.READ.getBit();
        assertThat(service.hasAnyPermission(perm, new Permission[]{Permission.WRITE, Permission.DELETE})).isFalse();
    }

    @Test
    void hasAnyPermission_emptyArray_returnsFalse() {
        assertThat(service.hasAnyPermission(Permission.ADMIN.getBit(), new Permission[]{})).isFalse();
    }

    @Test
    void hasAnyPermission_zeroPerm_returnsFalse() {
        assertThat(service.hasAnyPermission(0, Permission.values())).isFalse();
    }

    // ── feature flag OFF — all methods bypass bitwise check ───────────────────

    @Test
    void hasPermission_flagOff_returnsTrueRegardlessOfBits() {
        flagOff();
        assertThat(service.hasPermission(0, Permission.ADMIN)).isTrue();
        assertThat(service.hasPermission(0, Permission.READ)).isTrue();
    }

    @Test
    void hasAllPermissions_flagOff_returnsTrueForZeroPerm() {
        flagOff();
        assertThat(service.hasAllPermissions(0, Permission.values())).isTrue();
    }

    @Test
    void hasAnyPermission_flagOff_returnsTrueForZeroPerm() {
        flagOff();
        assertThat(service.hasAnyPermission(0, Permission.values())).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void flagOff() {
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean()))
                .thenReturn(false);
    }
}
