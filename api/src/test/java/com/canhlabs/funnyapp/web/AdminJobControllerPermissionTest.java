package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.admin.ScheduledJobDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.ScheduledJobService;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADMIN reads the jobs; only SUPER_ADMIN may change a schedule or run a job (real PermissionServiceImpl). */
@WebMvcTest(AdminJobController.class)
@Import(AdminControllerPermissionTest.TestSecurity.class)
class AdminJobControllerPermissionTest {

    private static final String JOBS_URL = AppConstant.API.BASE_URL + "/admin/jobs";
    private static final String JOB_URL = JOBS_URL + "/log-stats";
    private static final String RUN_URL = JOB_URL + "/runs";

    @MockitoBean FeatureFlagService featureFlagService;
    @MockitoBean JWTAuthenticationFilter jwtFilter;
    @MockitoBean ScheduledJobService scheduledJobService;

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.<FilterChain>getArgument(2)
               .doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean()))
                .thenReturn(true);
    }

    @Test
    void list_admin_returns200() throws Exception {
        when(scheduledJobService.list()).thenReturn(List.of(ScheduledJobDto.builder().name("log-stats").build()));

        mockMvc.perform(get(JOBS_URL).with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("log-stats"));
    }

    @Test
    void list_user_returns403() throws Exception {
        mockMvc.perform(get(JOBS_URL).with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_admin_returns403() throws Exception {
        mockMvc.perform(patch(JOB_URL).contentType("application/json").content("{\"enabled\":false}")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isForbidden());
        verifyNoMoreInteractions(scheduledJobService);
    }

    @Test
    void patch_superAdmin_returns200() throws Exception {
        when(scheduledJobService.update(eq("log-stats"), any()))
                .thenReturn(ScheduledJobDto.builder().name("log-stats").enabled(false).build());

        mockMvc.perform(patch(JOB_URL).contentType("application/json").content("{\"enabled\":false}")
                        .with(authentication(principal(UserRole.SUPER_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("log-stats"));
        verify(scheduledJobService).update(eq("log-stats"), any());
    }

    @Test
    void run_admin_returns403() throws Exception {
        mockMvc.perform(post(RUN_URL).with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isForbidden());
        verifyNoMoreInteractions(scheduledJobService);
    }

    @Test
    void run_superAdmin_returns202() throws Exception {
        when(scheduledJobService.runNow("log-stats"))
                .thenReturn(ScheduledJobDto.builder().name("log-stats").build());

        mockMvc.perform(post(RUN_URL).with(authentication(principal(UserRole.SUPER_ADMIN))))
                .andExpect(status().isAccepted());
        verify(scheduledJobService).runNow("log-stats");
    }

    private Authentication principal(UserRole role) {
        UserDetailDto user = UserDetailDto.builder()
                .id(1L).email("tester@test.com")
                .role(role.name()).permissions(Permission.ADMIN.getBit())
                .build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        authentication.setDetails(user);
        return authentication;
    }
}
