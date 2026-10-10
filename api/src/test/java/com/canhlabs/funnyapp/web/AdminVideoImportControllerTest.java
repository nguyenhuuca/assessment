package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.admin.CreateVideoImportRequest;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportResultDto;
import com.canhlabs.funnyapp.dto.admin.ImportPreviewDto;
import com.canhlabs.funnyapp.dto.admin.VideoImportJobDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.enums.ImportStatus;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.filter.WebSecurityConfig;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.PermissionService;
import com.canhlabs.funnyapp.service.VideoImportService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-layer tests with the real method security (role + ADMIN permission bit) in front of the controller. */
@WebMvcTest(AdminVideoImportController.class)
@Import(AdminVideoImportControllerTest.TestSecurity.class)
class AdminVideoImportControllerTest {

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

        @Bean
        PermissionService permissionServiceImpl(FeatureFlagService featureFlagService) {
            PermissionServiceImpl svc = new PermissionServiceImpl();
            svc.injectFeatureFlagService(featureFlagService);
            return svc;
        }
    }

    private static final String BASE = AppConstant.API.BASE_URL + "/admin/video-imports";

    @MockitoBean FeatureFlagService featureFlagService;
    @MockitoBean JWTAuthenticationFilter jwtFilter;
    @MockitoBean VideoImportService videoImportService;

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.<FilterChain>getArgument(2)
                    .doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean())).thenReturn(true);
    }

    private Authentication admin() {
        return principal(UserRole.ADMIN, Permission.ADMIN.getBit());
    }

    private Authentication principal(UserRole role, int permBits) {
        UserDetailDto user = UserDetailDto.builder()
                .id(11L).email("tester@test.com").role(role.name()).permissions(permBits).build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        auth.setDetails(user);
        return auth;
    }

    private static VideoImportJobDto jobDto(long id) {
        return VideoImportJobDto.builder().id(id).platform(ImportPlatform.YOUTUBE).status(ImportStatus.PENDING)
                .phase("QUEUED").build();
    }

    // ── happy paths ───────────────────────────────────────────────────────────

    @Test
    void create_admin_passesBodyAndAdminId_andReturnsPerLineResults() throws Exception {
        when(videoImportService.create(any(), eq(11L))).thenReturn(CreateVideoImportResultDto.builder()
                .results(List.of(
                        CreateVideoImportResultDto.LineResult.builder().line(1).jobId(12L).build(),
                        CreateVideoImportResultDto.LineResult.builder().line(2).errorCode("INVALID_URL").build()))
                .build());

        mockMvc.perform(post(BASE).with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"url\":\"https://youtu.be/dQw4w9WgXcQ\",\"title\":\"T\"},{\"url\":\"x\"}],"
                                + "\"scheduledAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].line").value(1))
                .andExpect(jsonPath("$.data.results[0].jobId").value(12))
                .andExpect(jsonPath("$.data.results[0].errorCode").doesNotExist())
                .andExpect(jsonPath("$.data.results[1].errorCode").value("INVALID_URL"))
                .andExpect(jsonPath("$.data.results[1].jobId").doesNotExist());

        verify(videoImportService).create(argThat((CreateVideoImportRequest r) ->
                r.getItems().size() == 2
                        && "https://youtu.be/dQw4w9WgXcQ".equals(r.getItems().get(0).getUrl())
                        && "T".equals(r.getItems().get(0).getTitle())
                        && r.getScheduledAt() != null), eq(11L));
    }

    @Test
    void create_nullScheduledAt_isPassedAsNull() throws Exception {
        when(videoImportService.create(any(), any())).thenReturn(CreateVideoImportResultDto.builder().results(List.of()).build());

        mockMvc.perform(post(BASE).with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"url\":\"u\"}],\"scheduledAt\":null}"))
                .andExpect(status().isOk());

        verify(videoImportService).create(argThat((CreateVideoImportRequest r) -> r.getScheduledAt() == null), any());
    }

    @Test
    void list_defaultsAndFilters() throws Exception {
        when(videoImportService.list(any(), any())).thenReturn(new PageImpl<>(List.of(jobDto(1))));

        mockMvc.perform(get(BASE).param("status", "FAILED").param("page", "1").param("size", "5")
                        .with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].phase").value("QUEUED"));

        verify(videoImportService).list(eq(ImportStatus.FAILED), argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 5));
    }

    @Test
    void list_withoutParams_usesSize20AndNoStatus() throws Exception {
        when(videoImportService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(BASE).with(authentication(admin()))).andExpect(status().isOk());

        verify(videoImportService).list(isNull(), argThat(p -> p.getPageSize() == 20 && p.getPageNumber() == 0));
    }

    @Test
    void preview_returnsTitlePlatformDuration() throws Exception {
        when(videoImportService.preview("https://youtu.be/dQw4w9WgXcQ"))
                .thenReturn(ImportPreviewDto.builder().title("Hi").platform(ImportPlatform.YOUTUBE).durationSec(9L).build());

        mockMvc.perform(post(BASE + "/preview").with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://youtu.be/dQw4w9WgXcQ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Hi"))
                .andExpect(jsonPath("$.data.platform").value("YOUTUBE"))
                .andExpect(jsonPath("$.data.durationSec").value(9));
    }

    @Test
    void runNowCancelRetry_delegateToService() throws Exception {
        when(videoImportService.runNow(5L)).thenReturn(jobDto(5));
        when(videoImportService.cancel(5L)).thenReturn(jobDto(5));
        when(videoImportService.retry(5L)).thenReturn(jobDto(5));

        for (String action : List.of("run-now", "cancel", "retry")) {
            mockMvc.perform(post(BASE + "/5/" + action).with(authentication(admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(5));
        }

        verify(videoImportService).runNow(5L);
        verify(videoImportService).cancel(5L);
        verify(videoImportService).retry(5L);
    }

    @Test
    void conflict_isReportedWith409() throws Exception {
        when(videoImportService.cancel(5L)).thenThrow(
                CustomException.builder().status(HttpStatus.CONFLICT).subCode(4091).message("INVALID_STATE").build());

        mockMvc.perform(post(BASE + "/5/cancel").with(authentication(admin())))
                .andExpect(status().isConflict());
    }

    // ── security ──────────────────────────────────────────────────────────────

    @Test
    void userRole_isForbiddenOnEveryEndpoint() throws Exception {
        Authentication user = principal(UserRole.USER, Permission.ADMIN.getBit());
        String body = "{\"items\":[],\"url\":\"u\"}";

        mockMvc.perform(post(BASE).with(authentication(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE).with(authentication(user))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/preview").with(authentication(user)).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isForbidden());
        for (String action : List.of("run-now", "cancel", "retry")) {
            mockMvc.perform(post(BASE + "/5/" + action).with(authentication(user))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(videoImportService);
    }

    @Test
    void adminRoleWithoutAdminBit_isForbidden() throws Exception {
        Authentication noBit = principal(UserRole.ADMIN, Permission.READ.getBit());

        mockMvc.perform(get(BASE).with(authentication(noBit))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/5/retry").with(authentication(noBit))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE).with(authentication(noBit)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[]}")).andExpect(status().isForbidden());
        verifyNoInteractions(videoImportService);
    }

    @Test
    void unauthenticated_isForbidden() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isForbidden());
        verifyNoInteractions(videoImportService);
    }
}
