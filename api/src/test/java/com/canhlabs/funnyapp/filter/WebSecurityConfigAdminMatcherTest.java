package com.canhlabs.funnyapp.filter;

import com.canhlabs.funnyapp.dto.admin.ReportDto;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.ReportService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.web.AdminReportController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the REAL {@link WebSecurityConfig} filter chain (not the permission tests' stand-in) to prove the
 * {@code /admin/reports/**} matcher sits before {@code /admin/**} (ADR-0030, spec FR-2 / BR-2 / EC-8).
 * Only the report controller is loaded: a path that passes the matcher but has no handler answers 404,
 * a path the matcher refuses answers 403 before any handler is looked up.
 */
@WebMvcTest(AdminReportController.class)
@Import(WebSecurityConfig.class)
class WebSecurityConfigAdminMatcherTest {

    private static final String ADMIN_URL = AppConstant.API.BASE_URL + "/admin";
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String BODY = "{\"type\":\"BUG\",\"title\":\"Broken\",\"description\":\"Details\"}";

    @MockitoBean FeatureFlagService featureFlagService;
    @MockitoBean JWTAuthenticationFilter jwtFilter;
    @MockitoBean ReportService reportService;

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.<FilterChain>getArgument(2)
               .doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    void contributor_reachesTheThreeReportEndpoints() throws Exception {
        ReportDto dto = ReportDto.builder().id(ID).title("Broken").status(ReportStatus.CREATED).build();
        when(reportService.create(any())).thenReturn(dto);
        when(reportService.list(0, 20)).thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));
        when(reportService.retry(ID)).thenReturn(dto);
        Authentication contributor = principal(UserRole.CONTRIBUTOR);

        mockMvc.perform(get(ADMIN_URL + "/reports").with(authentication(contributor))).andExpect(status().isOk());
        mockMvc.perform(post(ADMIN_URL + "/reports").contentType("application/json").content(BODY)
                .with(authentication(contributor))).andExpect(status().isOk());
        mockMvc.perform(post(ADMIN_URL + "/reports/" + ID + "/retry").with(authentication(contributor)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/accounts", "/videos", "/jobs", "/drive-sync/files", "/comments",
            "/video-imports", "/video-suggestions/pending-count"})
    void contributor_isRefusedOnEveryOtherAdminPath(String path) throws Exception {
        mockMvc.perform(get(ADMIN_URL + path).with(authentication(principal(UserRole.CONTRIBUTOR))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reports", "/accounts"})
    void user_isRefusedOnAdminPaths(String path) throws Exception {
        mockMvc.perform(get(ADMIN_URL + path).with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SUPER_ADMIN"})
    void adminRoles_passTheMatcherOnOtherAdminPaths(String role) throws Exception {
        MvcResult result = mockMvc.perform(get(ADMIN_URL + "/accounts")
                .with(authentication(principal(UserRole.valueOf(role))))).andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(403).isNotEqualTo(401);
    }

    private static Authentication principal(UserRole role) {
        return new UsernamePasswordAuthenticationToken("tester@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
