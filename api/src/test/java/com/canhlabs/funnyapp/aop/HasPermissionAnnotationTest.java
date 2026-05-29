package com.canhlabs.funnyapp.aop;

import com.canhlabs.funnyapp.config.AppUserDetails;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.impl.PermissionServiceImpl;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.web.AdminController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class HasPermissionAnnotationTest {

    @Mock
    private FeatureFlagService featureFlagService;

    private PermissionServiceImpl permissionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        permissionService = new PermissionServiceImpl();
        permissionService.injectFeatureFlagService(featureFlagService);
        // flag ON so all tests exercise real bitwise logic
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean()))
                .thenReturn(true);
    }

    // ── @HasPermission annotation metadata ────────────────────────────────────

    @Test
    void annotation_hasPreAuthorize() {
        PreAuthorize preAuth = HasPermission.class.getAnnotation(PreAuthorize.class);
        assertThat(preAuth).isNotNull();
    }

    @Test
    void annotation_spel_referencesPermissionServiceImpl() {
        String spel = HasPermission.class.getAnnotation(PreAuthorize.class).value();
        assertThat(spel).contains("@permissionServiceImpl");
    }

    @Test
    void annotation_spel_callsHasPermission() {
        String spel = HasPermission.class.getAnnotation(PreAuthorize.class).value();
        assertThat(spel).contains("hasPermission");
    }

    @Test
    void annotation_spel_usesPrincipalPermissions() {
        String spel = HasPermission.class.getAnnotation(PreAuthorize.class).value();
        assertThat(spel).contains("authentication.principal.permissions");
    }

    @Test
    void annotation_spel_usesEnumPermTemplate() {
        // {perm} = annotation-attribute template; expands to the enum name e.g. "ADMIN".
        // PermissionEnumPropertyAccessor (in WebSecurityConfig) makes that name resolvable
        // in SpEL so the call hasPermission(bits, ADMIN) works correctly.
        String spel = HasPermission.class.getAnnotation(PreAuthorize.class).value();
        assertThat(spel).contains("{perm}");
        assertThat(spel).doesNotContain("#perm");
    }

    // ── AdminController.getStats() uses @HasPermission(perm = Permission.ADMIN) ─

    @Test
    void getStats_hasPermissionAnnotation_withAdminEnum() throws NoSuchMethodException {
        Method getStats = AdminController.class.getDeclaredMethod("getStats");
        HasPermission annotation = getStats.getAnnotation(HasPermission.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.perm()).isEqualTo(Permission.ADMIN);
    }

    // ── full chain: AppUserDetails → PermissionService ────────────────────────
    // Direct service tests (flag ON via setUp mock).

    @Test
    void chain_adminBit_grantedForAdminPermission() {
        AppUserDetails principal = buildPrincipal(UserRole.ADMIN, Permission.ADMIN.getBit());
        assertThat(permissionService.hasAllPermissions(
                principal.getPermissions(), new Permission[]{Permission.ADMIN})).isTrue();
    }

    @Test
    void chain_noAdminBit_deniedForAdminPermission() {
        AppUserDetails principal = buildPrincipal(UserRole.USER, Permission.READ.getBit());
        assertThat(permissionService.hasAllPermissions(
                principal.getPermissions(), new Permission[]{Permission.ADMIN})).isFalse();
    }

    @Test
    void chain_zeroBits_deniedForEveryPermission() {
        AppUserDetails principal = buildPrincipal(UserRole.USER, 0);
        for (Permission perm : Permission.values()) {
            assertThat(permissionService.hasAllPermissions(
                    principal.getPermissions(), new Permission[]{perm}))
                    .as("should deny %s when bits=0", perm)
                    .isFalse();
        }
    }

    @Test
    void chain_allBitsSet_grantedForAllPermissions() {
        int allBits = 0;
        for (Permission p : Permission.values()) allBits |= p.getBit();
        AppUserDetails principal = buildPrincipal(UserRole.ADMIN, allBits);
        assertThat(permissionService.hasAllPermissions(
                principal.getPermissions(), Permission.values())).isTrue();
    }

    @Test
    void chain_partialBits_deniedWhenMissingRequiredPerm() {
        int bits = Permission.READ.getBit() | Permission.WRITE.getBit();
        AppUserDetails principal = buildPrincipal(UserRole.USER, bits);
        assertThat(permissionService.hasAllPermissions(
                principal.getPermissions(),
                new Permission[]{Permission.READ, Permission.WRITE, Permission.ADMIN})).isFalse();
    }

    @Test
    void chain_execBitOnly_detectedCorrectly() {
        AppUserDetails principal = buildPrincipal(UserRole.USER, Permission.EXEC.getBit());
        assertThat(permissionService.hasPermission(principal.getPermissions(), Permission.EXEC)).isTrue();
        assertThat(permissionService.hasPermission(principal.getPermissions(), Permission.READ)).isFalse();
        assertThat(permissionService.hasPermission(principal.getPermissions(), Permission.ADMIN)).isFalse();
    }

    @Test
    void chain_deleteBitOnly_hasAnyGranted() {
        AppUserDetails principal = buildPrincipal(UserRole.USER, Permission.DELETE.getBit());
        assertThat(permissionService.hasAnyPermission(
                principal.getPermissions(),
                new Permission[]{Permission.WRITE, Permission.DELETE})).isTrue();
        assertThat(permissionService.hasAnyPermission(
                principal.getPermissions(),
                new Permission[]{Permission.READ, Permission.EXEC})).isFalse();
    }

    @Test
    void chain_flagOff_bypasses_bitwise_check() {
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean()))
                .thenReturn(false);
        AppUserDetails principal = buildPrincipal(UserRole.USER, 0); // no bits at all
        // flag OFF → all checks pass regardless of bits
        assertThat(permissionService.hasAllPermissions(
                principal.getPermissions(), Permission.values())).isTrue();
        assertThat(permissionService.hasPermission(
                principal.getPermissions(), Permission.ADMIN)).isTrue();
        assertThat(permissionService.hasAnyPermission(
                principal.getPermissions(), Permission.values())).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AppUserDetails buildPrincipal(UserRole role, int permissions) {
        User user = User.builder()
                .id(1L)
                .userName("user@test.com")
                .password("pw")
                .role(role)
                .permissions(permissions)
                .build();
        return new AppUserDetails(user);
    }
}
