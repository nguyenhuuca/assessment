package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimitAspect;
import com.canhlabs.funnyapp.aop.SlidingWindowRateLimiter;
import com.canhlabs.funnyapp.config.I18nConfig;
import com.canhlabs.funnyapp.dto.usersettings.UpdateUserSettingsRequest;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.service.UserSettingsService;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Accept-Language drives the message of localized API errors; {@code code} stays stable (ADR-0020 D3/D4). */
@AutoConfigureCache
@WebMvcTest(UserSettingsController.class)
@Import({I18nConfig.class, I18nErrorWebMvcTest.TestSecurity.class})
class I18nErrorWebMvcTest {

    @TestConfiguration
    static class TestSecurity {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .build();
        }

        @Bean
        SlidingWindowRateLimiter slidingWindowRateLimiter() {
            return new SlidingWindowRateLimiter();
        }

        @Bean
        RateLimitAspect rateLimitAspect(SlidingWindowRateLimiter limiter) {
            return new RateLimitAspect(limiter);
        }
    }

    private static final String SETTINGS_URL = AppConstant.API.BASE_URL + "/user/settings";
    private static final String VI_NOT_FOUND = "Không tìm thấy bộ sưu tập";

    @MockitoBean
    UserSettingsService userSettingsService;

    @MockitoBean
    JWTAuthenticationFilter jwtFilter;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.<FilterChain>getArgument(2)
                    .doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
        when(userSettingsService.getSettings()).thenThrow(
                CustomException.of(HttpStatus.NOT_FOUND, 404, "error.collection.notFound"));
    }

    @Test
    void acceptLanguageVi_returnsVietnameseMessage_andStableCode() throws Exception {
        mockMvc.perform(get(SETTINGS_URL).header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value(VI_NOT_FOUND))
                .andExpect(jsonPath("$.error.code").value("error.collection.notFound"))
                .andExpect(jsonPath("$.error.subCode").value(404));
    }

    @Test
    void acceptLanguageVi_withRegionAndQuality_stillVietnamese() throws Exception {
        mockMvc.perform(get(SETTINGS_URL).header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN,vi;q=0.9,en;q=0.8"))
                .andExpect(jsonPath("$.error.message").value(VI_NOT_FOUND));
    }

    @Test
    void acceptLanguageEn_returnsEnglish() throws Exception {
        mockMvc.perform(get(SETTINGS_URL).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$.error.message").value("Collection not found"))
                .andExpect(jsonPath("$.error.code").value("error.collection.notFound"));
    }

    @Test
    void missingOrUnsupportedHeader_defaultsToEnglish() throws Exception {
        mockMvc.perform(get(SETTINGS_URL))
                .andExpect(jsonPath("$.error.message").value("Collection not found"));
        mockMvc.perform(get(SETTINGS_URL).header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(jsonPath("$.error.message").value("Collection not found"));
    }

    @Test
    void exceptionWithoutKey_keepsLegacyMessage_andNoCode() throws Exception {
        doThrow(
                CustomException.builder().status(HttpStatus.BAD_REQUEST).subCode(400).message("legacy text").build()).when(userSettingsService).getSettings();

        mockMvc.perform(get(SETTINGS_URL).header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("legacy text"))
                .andExpect(jsonPath("$.error.code").doesNotExist());
    }

    @Test
    void argsAreInterpolated() throws Exception {
        doThrow(
                CustomException.of(HttpStatus.CONFLICT, 409, "error.collection.limitReached", 50)).when(userSettingsService).getSettings();

        mockMvc.perform(get(SETTINGS_URL).header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(jsonPath("$.error.message").value("Đã đạt giới hạn số bộ sưu tập (50)"));
    }

    @Test
    void unknownKey_fallsBackToExceptionMessage() throws Exception {
        doThrow(
                CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.does.not.exist")).when(userSettingsService).getSettings();

        mockMvc.perform(get(SETTINGS_URL).header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(jsonPath("$.error.message").value("error.does.not.exist"))
                .andExpect(jsonPath("$.error.code").value("error.does.not.exist"));
    }

    @Test
    void malformedJson_isLocalized() throws Exception {
        mockMvc.perform(patch(SETTINGS_URL).header(HttpHeaders.ACCEPT_LANGUAGE, "vi")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Yêu cầu JSON không đúng định dạng"))
                .andExpect(jsonPath("$.error.code").value("error.request.malformedJson"));
    }

    @Test
    void patchLocale_explicitNullIsDistinguishedFromAbsent() throws Exception {
        mockMvc.perform(patch(SETTINGS_URL).contentType(MediaType.APPLICATION_JSON).content("{\"locale\":null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateUserSettingsRequest> captor = ArgumentCaptor.forClass(UpdateUserSettingsRequest.class);
        verify(userSettingsService).updateSettings(captor.capture());
        assertThat(captor.getValue().isLocaleProvided()).isTrue();
        assertThat(captor.getValue().getLocale()).isNull();
    }
}
