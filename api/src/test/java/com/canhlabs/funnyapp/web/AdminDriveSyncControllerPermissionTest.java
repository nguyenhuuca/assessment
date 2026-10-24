package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.admin.DriveSyncListingDto;
import com.canhlabs.funnyapp.dto.admin.DriveSyncRunDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.filter.WebSecurityConfig;
import com.canhlabs.funnyapp.service.DriveSyncService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADMIN can view (GET), only SUPER_ADMIN can start a force sync (POST); same security wiring as production. */
@WebMvcTest(AdminDriveSyncController.class)
@Import(AdminDriveSyncControllerPermissionTest.TestSecurity.class)
class AdminDriveSyncControllerPermissionTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurity {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/v1/funny-app/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
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

        @Bean
        PermissionService permissionServiceImpl(FeatureFlagService featureFlagService) {
            PermissionServiceImpl svc = new PermissionServiceImpl();
            svc.injectFeatureFlagService(featureFlagService);
            return svc;
        }
    }

    private static final String BASE = AppConstant.API.BASE_URL + "/admin/drive-sync";
    private static final String BODY = "{\"fileIds\":[\"f1\"]}";

    @MockitoBean FeatureFlagService featureFlagService;
    @MockitoBean JWTAuthenticationFilter jwtFilter;
    @MockitoBean DriveSyncService driveSyncService;

    @Autowired MockMvc mockMvc;
    @Autowired PermissionService permissionService;

    @BeforeEach
    void setUp() throws Exception {
        // the real PermissionServiceImpl caches the flag for 15 s; each test sets its own value
        ((PermissionServiceImpl) permissionService).invalidateEnforcementCache();
        doAnswer(inv -> {
            inv.<FilterChain>getArgument(2)
               .doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean())).thenReturn(false);
        when(driveSyncService.listFolder(any(), any())).thenReturn(DriveSyncListingDto.builder().files(List.of()).orphans(List.of()).build());
        when(driveSyncService.startRun(any())).thenReturn(DriveSyncRunDto.builder().runId("r1").active(true).build());
    }

    // ── view: ADMIN and SUPER_ADMIN ─────────────────────────────────────────────

    @Test
    void files_admin_returns200() throws Exception {
        mockMvc.perform(get(BASE + "/files").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void files_superAdmin_returns200() throws Exception {
        mockMvc.perform(get(BASE + "/files").with(authentication(principal(UserRole.SUPER_ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void files_rangeParams_bindAsIsoInstants() throws Exception {
        mockMvc.perform(get(BASE + "/files").param("from", "2026-10-18T00:00:00Z").param("to", "2026-10-18T12:01:00Z")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk());
        verify(driveSyncService).listFolder(Instant.parse("2026-10-18T00:00:00Z"), Instant.parse("2026-10-18T12:01:00Z"));
    }

    @Test
    void files_noParams_passesNulls() throws Exception {
        mockMvc.perform(get(BASE + "/files").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk());
        verify(driveSyncService).listFolder(null, null);
    }

    @Test
    void files_fromAfterTo_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/files").param("from", "2026-10-18T12:00:00Z").param("to", "2026-10-18T00:00:00Z")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isBadRequest());
        verify(driveSyncService, never()).listFolder(any(), any());
    }

    @Test
    void files_malformedInstant_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/files").param("from", "yesterday")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isBadRequest());
        verify(driveSyncService, never()).listFolder(any(), any());
    }

    @Test
    void currentRun_admin_returns200() throws Exception {
        mockMvc.perform(get(BASE + "/runs/current").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void files_user_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/files").with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(driveSyncService);
    }

    // ── force sync: SUPER_ADMIN only ────────────────────────────────────────────

    @Test
    void startRun_superAdmin_returns202() throws Exception {
        mockMvc.perform(post(BASE + "/runs").contentType("application/json").content(BODY)
                        .with(authentication(principal(UserRole.SUPER_ADMIN))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.runId").value("r1"));
        verify(driveSyncService).startRun(List.of("f1"));
    }

    @Test
    void startRun_admin_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/runs").contentType("application/json").content(BODY)
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isForbidden());
        verify(driveSyncService, never()).startRun(any());
    }

    @Test
    void startRun_user_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/runs").contentType("application/json").content(BODY)
                        .with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        verify(driveSyncService, never()).startRun(any());
    }

    @Test
    void startRun_enforcementOn_superAdminNeedsAdminBit() throws Exception {
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean())).thenReturn(true);

        mockMvc.perform(post(BASE + "/runs").contentType("application/json").content(BODY)
                        .with(authentication(principal(UserRole.SUPER_ADMIN, Permission.ADMIN.getBit()))))
                .andExpect(status().isAccepted());
        mockMvc.perform(post(BASE + "/runs").contentType("application/json").content(BODY)
                        .with(authentication(principal(UserRole.SUPER_ADMIN, 0))))
                .andExpect(status().isForbidden());
    }

    private Authentication principal(UserRole role) {
        return principal(role, Permission.ADMIN.getBit());
    }

    private Authentication principal(UserRole role, int permBits) {
        UserDetailDto user = UserDetailDto.builder()
                .id(1L).email("tester@test.com")
                .role(role.name()).permissions(permBits)
                .build();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        authentication.setDetails(user);
        return authentication;
    }
}
