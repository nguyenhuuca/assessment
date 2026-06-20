package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimitAspect;
import com.canhlabs.funnyapp.aop.SlidingWindowRateLimiter;
import com.canhlabs.funnyapp.dto.usersettings.DeleteAccountRequest;
import com.canhlabs.funnyapp.dto.usersettings.UpdateUserSettingsRequest;
import com.canhlabs.funnyapp.dto.usersettings.UserSettingsDto;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.service.UserSettingsService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserSettingsController.class)
@Import(UserSettingsControllerTest.TestSecurity.class)
class UserSettingsControllerTest {

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
    private static final String ACCOUNT_URL = AppConstant.API.BASE_URL + "/user/account";

    @MockitoBean
    UserSettingsService userSettingsService;

    @MockitoBean
    JWTAuthenticationFilter jwtFilter;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        // JWT filter: transparent pass-through
        doAnswer(inv -> {
            inv.<FilterChain>getArgument(2)
               .doFilter(inv.<ServletRequest>getArgument(0),
                         inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    // ── GET /user/settings ────────────────────────────────────────────────────

    @Test
    void getSettings_returnsOkWithDto() throws Exception {
        UserSettingsDto dto = UserSettingsDto.builder()
                .email("a@b.com")
                .defaultQuality("AUTO")
                .notifyNewContent(true)
                .notifyEmail(true)
                .mfaEnabled(false)
                .passwordEnabled(true)
                .mfaAvailable(true)
                .build();

        when(userSettingsService.getSettings()).thenReturn(dto);

        mockMvc.perform(get(SETTINGS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.defaultQuality").value("AUTO"))
                .andExpect(jsonPath("$.data.email").value("a@b.com"));
    }

    // ── PATCH /user/settings ──────────────────────────────────────────────────

    @Test
    void updateSettings_withValidBody_returnsOk() throws Exception {
        UserSettingsDto updatedDto = UserSettingsDto.builder()
                .email("a@b.com")
                .defaultQuality("1080P")
                .notifyEmail(false)
                .notifyNewContent(true)
                .build();

        when(userSettingsService.updateSettings(any(UpdateUserSettingsRequest.class))).thenReturn(updatedDto);

        String body = """
                {
                  "notifyEmail": false,
                  "defaultQuality": "1080P"
                }
                """;

        mockMvc.perform(patch(SETTINGS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.defaultQuality").value("1080P"));

        verify(userSettingsService).updateSettings(any(UpdateUserSettingsRequest.class));
    }

    // ── DELETE /user/account ──────────────────────────────────────────────────

    @Test
    void deleteAccount_returnsNoContent() throws Exception {
        doNothing().when(userSettingsService).deleteAccount(any(DeleteAccountRequest.class));

        String body = """
                {
                  "confirmation": "a@b.com"
                }
                """;

        mockMvc.perform(delete(ACCOUNT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(userSettingsService).deleteAccount(any(DeleteAccountRequest.class));
    }
}
