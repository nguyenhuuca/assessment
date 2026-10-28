package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.admin.ReportDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.ReportService;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CONTRIBUTOR, ADMIN and SUPER_ADMIN may use the report API, USER may not (real PermissionServiceImpl).
 * The slice does not load the rate-limit or audit aspects, so those limits are not exercised here.
 */
@AutoConfigureCache
@WebMvcTest(AdminReportController.class)
@Import(AdminControllerPermissionTest.TestSecurity.class)
class AdminReportControllerPermissionTest {

    private static final String REPORTS_URL = AppConstant.API.BASE_URL + "/admin/reports";
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
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean()))
                .thenReturn(true);
    }

    @Test
    void create_user_returns403() throws Exception {
        mockMvc.perform(post(REPORTS_URL).contentType("application/json").content(BODY)
                        .with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    @Test
    void list_user_returns403() throws Exception {
        mockMvc.perform(get(REPORTS_URL).with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    @Test
    void retry_user_returns403() throws Exception {
        mockMvc.perform(post(REPORTS_URL + "/" + ID + "/retry").with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTRIBUTOR", "ADMIN", "SUPER_ADMIN"})
    void create_adminRoles_return200WithContract(UserRole role) throws Exception {
        when(reportService.create(any())).thenReturn(dto(ReportStatus.CREATED, 42));

        mockMvc.perform(post(REPORTS_URL).contentType("application/json").content(BODY)
                        .with(authentication(principal(role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issueNumber").value(42))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
        verify(reportService).create(any());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTRIBUTOR", "ADMIN", "SUPER_ADMIN"})
    void list_adminRoles_return200WithPage(UserRole role) throws Exception {
        when(reportService.list(0, 20)).thenReturn(
                new PageImpl<>(List.of(dto(ReportStatus.CREATED, 7)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(REPORTS_URL).with(authentication(principal(role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].issueNumber").value(7))
                .andExpect(jsonPath("$.data.content[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void list_passesPageAndSize() throws Exception {
        when(reportService.list(2, 5)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        mockMvc.perform(get(REPORTS_URL).param("page", "2").param("size", "5")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk());
        verify(reportService).list(2, 5);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTRIBUTOR", "ADMIN", "SUPER_ADMIN"})
    void retry_adminRoles_return200(UserRole role) throws Exception {
        when(reportService.retry(ID)).thenReturn(dto(ReportStatus.CREATED, 9));

        mockMvc.perform(post(REPORTS_URL + "/" + ID + "/retry").with(authentication(principal(role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issueNumber").value(9))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 409})
    void create_serviceError_comesThroughWithStatus(int code) throws Exception {
        when(reportService.create(any()))
                .thenThrow(CustomException.of(HttpStatus.valueOf(code), code, "error.report.notFound"));

        mockMvc.perform(post(REPORTS_URL).contentType("application/json").content(BODY)
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().is(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404, 409})
    void retry_serviceError_comesThroughWithStatus(int code) throws Exception {
        when(reportService.retry(ID))
                .thenThrow(CustomException.of(HttpStatus.valueOf(code), code, "error.report.notFound"));

        mockMvc.perform(post(REPORTS_URL + "/" + ID + "/retry").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().is(code));
    }

    @Test
    void list_serviceBadRequest_returns400() throws Exception {
        when(reportService.list(-1, 20))
                .thenThrow(CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.report.invalidPage"));

        mockMvc.perform(get(REPORTS_URL).param("page", "-1").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retry_nonUuidId_returns404WithoutCallingService() throws Exception {
        mockMvc.perform(post(REPORTS_URL + "/not-a-uuid/retry").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isNotFound());
        verifyNoInteractions(reportService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void contributorWithNoPermissionBits_canUseAllThreeEndpoints_flagOnAndOff(boolean flag) throws Exception {
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean()))
                .thenReturn(flag);
        when(reportService.create(any())).thenReturn(dto(ReportStatus.CREATED, 1));
        when(reportService.list(0, 20)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(reportService.retry(ID)).thenReturn(dto(ReportStatus.CREATED, 2));
        Authentication contributor = principal(UserRole.CONTRIBUTOR, 0);

        mockMvc.perform(post(REPORTS_URL).contentType("application/json").content(BODY)
                        .with(authentication(contributor))).andExpect(status().isOk());
        mockMvc.perform(get(REPORTS_URL).with(authentication(contributor))).andExpect(status().isOk());
        mockMvc.perform(post(REPORTS_URL + "/" + ID + "/retry").with(authentication(contributor)))
                .andExpect(status().isOk());
    }

    private static ReportDto dto(ReportStatus status, Integer issueNumber) {
        return ReportDto.builder().id(ID).title("Broken").status(status).issueNumber(issueNumber).build();
    }

    private Authentication principal(UserRole role) {
        return principal(role, Permission.ADMIN.getBit());
    }

    private Authentication principal(UserRole role, int permissions) {
        UserDetailDto user = UserDetailDto.builder()
                .id(1L).email("tester@test.com")
                .role(role.name()).permissions(permissions)
                .build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        authentication.setDetails(user);
        return authentication;
    }
}
