package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.config.I18nConfig;
import com.canhlabs.funnyapp.dto.suggestion.SuggestionDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.SuggestionFilter;
import com.canhlabs.funnyapp.enums.SuggestionRejectReason;
import com.canhlabs.funnyapp.enums.SuggestionStatus;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.filter.WebSecurityConfig;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.PermissionService;
import com.canhlabs.funnyapp.service.VideoSuggestionService;
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
import org.springframework.http.HttpHeaders;
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

/** Real method security (role + ADMIN permission bit): guest 401, non-admin 403, admin through. */
@WebMvcTest(AdminVideoSuggestionController.class)
@Import({I18nConfig.class, AdminVideoSuggestionControllerTest.TestSecurity.class})
class AdminVideoSuggestionControllerTest {

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
                    .exceptionHandling(e -> e.authenticationEntryPoint(
                            (req, res, ex) -> res.sendError(HttpStatus.UNAUTHORIZED.value())))
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

    private static final String BASE = AppConstant.API.BASE_URL + "/admin/video-suggestions";

    @MockitoBean FeatureFlagService featureFlagService;
    @MockitoBean JWTAuthenticationFilter jwtFilter;
    @MockitoBean VideoSuggestionService suggestionService;

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

    private static SuggestionDto dto(long id, SuggestionStatus status) {
        return SuggestionDto.builder().id(id).sourceUrl("https://youtu.be/dQw4w9WgXcQ").platform(ImportPlatform.YOUTUBE)
                .status(status).suggesterName("bob").importJobId(9L).build();
    }

    // ── access control ────────────────────────────────────────────────────────

    @Test
    void guest_401_onEveryRoute() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/pending-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/1/approve").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/1/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"DUPLICATE\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(suggestionService);
    }

    @Test
    void nonAdmin_403_onEveryRoute() throws Exception {
        Authentication user = principal(UserRole.USER, 0);

        mockMvc.perform(get(BASE).with(authentication(user))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/pending-count").with(authentication(user))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/1/approve").with(authentication(user))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/1/reject").with(authentication(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"DUPLICATE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(suggestionService);
    }

    @Test
    void adminRoleWithoutAdminPermissionBit_403() throws Exception {
        mockMvc.perform(get(BASE).with(authentication(principal(UserRole.ADMIN, 0))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(suggestionService);
    }

    // ── list / count ──────────────────────────────────────────────────────────

    @Test
    void list_defaults() throws Exception {
        when(suggestionService.list(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new PageImpl<>(List.of(dto(1, SuggestionStatus.PENDING))));

        mockMvc.perform(get(BASE).with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].suggesterName").value("bob"))
                .andExpect(jsonPath("$.data.content[0].importJobId").value(9));

        verify(suggestionService).list(isNull(), eq(0), eq(20));
    }

    @Test
    void list_passesFilterPageAndSize() throws Exception {
        when(suggestionService.list(any(), any(Integer.class), any(Integer.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(BASE).param("status", "LIVE").param("page", "3").param("size", "7")
                        .with(authentication(admin())))
                .andExpect(status().isOk());

        verify(suggestionService).list(SuggestionFilter.LIVE, 3, 7);
    }

    @Test
    void pendingCount_returnsNumber() throws Exception {
        when(suggestionService.pendingCount()).thenReturn(4L);

        mockMvc.perform(get(BASE + "/pending-count").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(4));
    }

    // ── approve ───────────────────────────────────────────────────────────────

    @Test
    void approve_passesTitleAndAdminId() throws Exception {
        when(suggestionService.approve(5L, "New title", 11L)).thenReturn(dto(5, SuggestionStatus.APPROVED));

        mockMvc.perform(post(BASE + "/5/approve").with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"New title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.importJobId").value(9));
    }

    @Test
    void approve_withoutBody_titleIsNull() throws Exception {
        when(suggestionService.approve(5L, null, 11L)).thenReturn(dto(5, SuggestionStatus.APPROVED));

        mockMvc.perform(post(BASE + "/5/approve").with(authentication(admin())))
                .andExpect(status().isOk());

        verify(suggestionService).approve(5L, null, 11L);
    }

    @Test
    void approve_titleOver200_400() throws Exception {
        mockMvc.perform(post(BASE + "/5/approve").with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("error.validation.failed"));
        verifyNoInteractions(suggestionService);
    }

    @Test
    void approve_errors_carryStatusCodeAndLocalizedMessage() throws Exception {
        when(suggestionService.approve(1L, null, 11L)).thenThrow(
                CustomException.of(HttpStatus.NOT_FOUND, 4042, "error.suggestion.notFound"));
        when(suggestionService.approve(2L, null, 11L)).thenThrow(
                CustomException.of(HttpStatus.CONFLICT, 4093, "error.suggestion.notPending"));
        when(suggestionService.approve(3L, null, 11L)).thenThrow(
                CustomException.of(HttpStatus.SERVICE_UNAVAILABLE, 4100, "error.import.disabled"));

        mockMvc.perform(post(BASE + "/1/approve").with(authentication(admin())).header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("error.suggestion.notFound"))
                .andExpect(jsonPath("$.error.message").value("Không tìm thấy đề xuất"));
        mockMvc.perform(post(BASE + "/2/approve").with(authentication(admin())).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("error.suggestion.notPending"))
                .andExpect(jsonPath("$.error.message").value("This suggestion has already been reviewed"));
        mockMvc.perform(post(BASE + "/3/approve").with(authentication(admin())).header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("error.import.disabled"))
                .andExpect(jsonPath("$.error.message").value("Tính năng nhập video đang tắt"));
    }

    // ── reject ────────────────────────────────────────────────────────────────

    @Test
    void reject_passesReasonNoteAndAdminId() throws Exception {
        SuggestionDto rejected = dto(6, SuggestionStatus.REJECTED);
        rejected.setRejectReason(SuggestionRejectReason.COPYRIGHT);
        rejected.setRejectNote("claimed");
        when(suggestionService.reject(6L, SuggestionRejectReason.COPYRIGHT, "claimed", 11L)).thenReturn(rejected);

        mockMvc.perform(post(BASE + "/6/reject").with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"COPYRIGHT\",\"note\":\"claimed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("COPYRIGHT"))
                .andExpect(jsonPath("$.data.rejectNote").value("claimed"));
    }

    @Test
    void reject_missingOrUnknownReason_400() throws Exception {
        mockMvc.perform(post(BASE + "/6/reject").with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("error.validation.failed"));
        mockMvc.perform(post(BASE + "/6/reject").with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"NOPE\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(suggestionService);
    }

    @Test
    void reject_noBody_400() throws Exception {
        mockMvc.perform(post(BASE + "/6/reject").with(authentication(admin())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(suggestionService);
    }

    @Test
    void reject_noteLongerThan300_400() throws Exception {
        mockMvc.perform(post(BASE + "/6/reject").with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"note\":\"" + "n".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(suggestionService);
    }

    @Test
    void reject_otherWithoutNote_400WithLocalizedMessage() throws Exception {
        when(suggestionService.reject(6L, SuggestionRejectReason.OTHER, null, 11L)).thenThrow(
                CustomException.of(HttpStatus.BAD_REQUEST, 4001, "error.suggestion.noteRequired"));

        mockMvc.perform(post(BASE + "/6/reject").with(authentication(admin())).header(HttpHeaders.ACCEPT_LANGUAGE, "vi")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("error.suggestion.noteRequired"))
                .andExpect(jsonPath("$.error.message").value("Cần nhập ghi chú khi lý do là Khác"));
    }
}
