package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.config.AppUserDetails;
import com.canhlabs.funnyapp.dto.AdminStatsDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.filter.WebSecurityConfig;
import com.canhlabs.funnyapp.service.AdminAccountService;
import com.canhlabs.funnyapp.service.AdminVideoService;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.PermissionService;
import com.canhlabs.funnyapp.service.impl.PermissionServiceImpl;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying that @HasPermission enforces bitwise permission at the HTTP layer.
 * AdminControllerTest uses @InjectMocks which bypasses Spring Security — these tests fill that gap.
 *
 * Uses the REAL PermissionServiceImpl (imported via @Import) so that:
 * - SpEL @permissionServiceImpl resolves to the actual production bean
 * - PermissionServiceImpl.hasBits() is called on every request — breakpoints work
 * - Only FeatureFlagService is mocked (its only external dependency)
 *
 * Flag behaviour: setUp configures the flag ON so real bitwise checks run.
 * The flagOff test overrides the mock to return false, triggering the bypass path.
 */
@WebMvcTest(AdminController.class)
@Import(AdminControllerPermissionTest.TestSecurity.class)
class AdminControllerPermissionTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurity {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/v1/funny-app/admin/**").hasRole("ADMIN")
                            .anyRequest().permitAll())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .build();
        }

        @Bean
        static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
            return new AnnotationTemplateExpressionDefaults();
        }

        @Bean
        static MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
            return WebSecurityConfig.permissionAwareExpressionHandler();
        }

        // Real PermissionServiceImpl wired with the FeatureFlagService mock from the test.
        // Declaring it here ensures the bean name is "permissionServiceImpl" (method name)
        // so SpEL @permissionServiceImpl resolves to the actual production code — not a mock.
        @Bean
        PermissionService permissionServiceImpl(FeatureFlagService featureFlagService) {
            PermissionServiceImpl svc = new PermissionServiceImpl();
            svc.injectFeatureFlagService(featureFlagService);
            return svc;
        }
    }

    private static final String STATS_URL = AppConstant.API.BASE_URL + "/admin/stats";

    // Only FeatureFlagService is mocked; PermissionServiceImpl itself is the real bean.
    @MockitoBean FeatureFlagService featureFlagService;
    @MockitoBean JWTAuthenticationFilter jwtFilter;
    @MockitoBean AdminVideoService adminVideoService;
    @MockitoBean AdminAccountService adminAccountService;

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        // JWT filter: transparent pass-through so Spring Security filters run normally
        doAnswer(inv -> {
            inv.<FilterChain>getArgument(2)
               .doFilter(inv.<ServletRequest>getArgument(0),
                         inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());

        // Flag ON → real PermissionServiceImpl.hasBits() performs actual bitwise check
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean()))
                .thenReturn(true);
    }

    // ── 200 OK ────────────────────────────────────────────────────────────────

    @Test
    void getStats_adminRoleWithAdminBit_returns200() throws Exception {
        when(adminVideoService.getStats()).thenReturn(stats());

        mockMvc.perform(get(STATS_URL)
                        .with(user(principal(UserRole.ADMIN, Permission.ADMIN.getBit()))))
                .andExpect(status().isOk());
    }

    @Test
    void getStats_adminRoleWithAllBits_returns200() throws Exception {
        when(adminVideoService.getStats()).thenReturn(stats());
        int allBits = 0;
        for (Permission p : Permission.values()) allBits |= p.getBit();

        mockMvc.perform(get(STATS_URL)
                        .with(user(principal(UserRole.ADMIN, allBits))))
                .andExpect(status().isOk());
    }

    // ── 403: ROLE_ADMIN present but ADMIN bit missing (@HasPermission blocks) ─

    @Test
    void getStats_adminRoleButReadBitOnly_returns403() throws Exception {
        mockMvc.perform(get(STATS_URL)
                        .with(user(principal(UserRole.ADMIN, Permission.READ.getBit()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStats_adminRoleButZeroBits_returns403() throws Exception {
        mockMvc.perform(get(STATS_URL)
                        .with(user(principal(UserRole.ADMIN, 0))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStats_adminRoleWithWriteExecDeleteButNotAdmin_returns403() throws Exception {
        int bits = Permission.WRITE.getBit() | Permission.EXEC.getBit() | Permission.DELETE.getBit();
        mockMvc.perform(get(STATS_URL)
                        .with(user(principal(UserRole.ADMIN, bits))))
                .andExpect(status().isForbidden());
    }

    // ── 403: ROLE_USER (URL-level rule blocks before method security runs) ────

    @Test
    void getStats_userRoleWithAdminBit_returns403() throws Exception {
        mockMvc.perform(get(STATS_URL)
                        .with(user(principal(UserRole.USER, Permission.ADMIN.getBit()))))
                .andExpect(status().isForbidden());
    }

    // ── 403: no authentication ────────────────────────────────────────────────

    @Test
    void getStats_noAuthentication_returns403() throws Exception {
        mockMvc.perform(get(STATS_URL))
                .andExpect(status().isForbidden());
    }

    // ── flag OFF: PermissionServiceImpl.hasBits returns true regardless of bits

    @Test
    void getStats_flagOff_adminRoleGrantedEvenWithZeroBits() throws Exception {
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean()))
                .thenReturn(false);  // flag OFF → hasBits bypasses check → grants access
        when(adminVideoService.getStats()).thenReturn(stats());

        mockMvc.perform(get(STATS_URL)
                        .with(user(principal(UserRole.ADMIN, 0))))
                .andExpect(status().isOk());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AppUserDetails principal(UserRole role, int permBits) {
        User user = User.builder()
                .id(1L).userName("tester@test.com").password("pw")
                .role(role).permissions(permBits)
                .build();
        return new AppUserDetails(user);
    }

    private AdminStatsDto stats() {
        return AdminStatsDto.builder()
                .totalVideos(10L).totalUsers(5L).pendingCount(1L).flaggedCount(0L)
                .build();
    }
}
