package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimitAspect;
import com.canhlabs.funnyapp.aop.SlidingWindowRateLimiter;
import com.canhlabs.funnyapp.dto.auth.JwtVerificationResultDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.watchhistory.WatchHistoryItemDto;
import com.canhlabs.funnyapp.dto.watchhistory.WatchSummaryDto;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.UnauthorizedException;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.filter.JwtProvider;
import com.canhlabs.funnyapp.service.WatchHistoryService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.AppUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real JWTAuthenticationFilter (JwtProvider mocked): proves /watch-history is not public. */
@WebMvcTest(WatchHistoryController.class)
@Import(WatchHistoryControllerTest.TestSecurity.class)
class WatchHistoryControllerTest {

    @TestConfiguration
    static class TestSecurity {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http, JWTAuthenticationFilter jwtFilter) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
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

    private static final String BASE = AppConstant.API.BASE_URL + "/watch-history";
    private static final String TOKEN = "Bearer good-token";
    private static final long USER_ID = 7L;

    @MockitoBean
    WatchHistoryService service;

    @MockitoBean
    JwtProvider jwtProvider;

    @MockitoBean
    com.canhlabs.funnyapp.cache.CredentialsVersionCache credentialsVersionCache;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtVerificationResultDto ok = new JwtVerificationResultDto();
        ok.setData(UserDetailDto.builder().id(USER_ID).email("u@x.com").build());
        when(jwtProvider.verifyToken(any())).thenAnswer(inv -> {
            if (TOKEN.equals(inv.getArgument(0))) {
                return ok;
            }
            throw new UnauthorizedException("TOKEN_INVALID", 601);
        });
    }

    @Test
    void guests_get401_onEveryEndpoint() throws Exception {
        mockMvc.perform(get(BASE).servletPath(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/summary").servletPath(BASE + "/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE + "/5/progress").servletPath(BASE + "/5/progress")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASE + "/5").servletPath(BASE + "/5")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASE).servletPath(BASE)).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void list_usesJwtUserAndDefaults_andReturnsPage() throws Exception {
        WatchHistoryItemDto item = WatchHistoryItemDto.builder().videoId(5L).title("Mèo").fileId("abc")
                .viewCount(3).watchedAt(Instant.parse("2026-10-15T10:00:00Z")).positionSec(20)
                .durationSec(100).completed(false).build();
        when(service.list(USER_ID, null, 0, 20)).thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(BASE).servletPath(BASE).header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].videoId").value(5))
                .andExpect(jsonPath("$.data.content[0].fileId").value("abc"))
                .andExpect(jsonPath("$.data.content[0].completed").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void list_clientCannotChooseTheUser() throws Exception {
        when(service.list(USER_ID, "cat", 2, 500)).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(BASE).servletPath(BASE).header("Authorization", TOKEN)
                        .param("q", "cat").param("page", "2").param("size", "500").param("userId", "99"))
                .andExpect(status().isOk());

        verify(service).list(USER_ID, "cat", 2, 500);
        verify(service, never()).list(org.mockito.ArgumentMatchers.eq(99L), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void summary_returnsList() throws Exception {
        when(service.summary(USER_ID)).thenReturn(List.of(
                WatchSummaryDto.builder().videoId(5L).positionSec(30).durationSec(100).completed(false).build()));

        mockMvc.perform(get(BASE + "/summary").servletPath(BASE + "/summary").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].videoId").value(5))
                .andExpect(jsonPath("$.data[0].positionSec").value(30))
                .andExpect(jsonPath("$.data[0].completed").value(false));
    }

    @Test
    void progress_returns204_andPassesRawValuesForServerSanitizing() throws Exception {
        mockMvc.perform(put(BASE + "/5/progress").servletPath(BASE + "/5/progress").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionSec\":12.5,\"durationSec\":300}"))
                .andExpect(status().isNoContent());

        verify(service).saveProgress(USER_ID, 5L, 12.5, 300.0);
    }

    @Test
    void progress_missingFields_stillReturns204() throws Exception {
        mockMvc.perform(put(BASE + "/5/progress").servletPath(BASE + "/5/progress").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());

        verify(service).saveProgress(USER_ID, 5L, null, null);
    }

    @Test
    void removeOne_ownerIsolation_alwaysScopedToJwtUser() throws Exception {
        mockMvc.perform(delete(BASE + "/5").servletPath(BASE + "/5").header("Authorization", TOKEN)
                        .param("userId", "99"))
                .andExpect(status().isNoContent());

        verify(service).remove(USER_ID, 5L);
        verify(service, never()).remove(org.mockito.ArgumentMatchers.eq(99L), any());
    }

    @Test
    void clear_returns204_forJwtUser() throws Exception {
        mockMvc.perform(delete(BASE).servletPath(BASE).header("Authorization", TOKEN))
                .andExpect(status().isNoContent());

        verify(service).clear(USER_ID);
    }

    @Test
    void currentUserMissing_throws401() {
        WatchHistoryController controller = new WatchHistoryController(service);

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(null);

            assertThatThrownBy(controller::clear).isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getStatus().value()).isEqualTo(401));

            ms.when(AppUtils::getCurrentUser).thenReturn(UserDetailDto.builder().email("a@b.c").build());
            assertThatThrownBy(controller::clear).isInstanceOf(CustomException.class);
        }
        verifyNoInteractions(service);
    }
}
