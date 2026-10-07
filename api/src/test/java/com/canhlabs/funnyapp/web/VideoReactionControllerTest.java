package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimitAspect;
import com.canhlabs.funnyapp.aop.SlidingWindowRateLimiter;
import com.canhlabs.funnyapp.dto.auth.JwtVerificationResultDto;
import com.canhlabs.funnyapp.dto.reaction.ReactionSummaryDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.ReactionType;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.UnauthorizedException;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.filter.JwtProvider;
import com.canhlabs.funnyapp.service.VideoReactionService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.AppUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses the REAL JWTAuthenticationFilter (only JwtProvider is mocked) so the tests prove how
 * authentication is resolved for the public GET and the protected PUT/DELETE on the same URL.
 */
@WebMvcTest(VideoReactionController.class)
@Import(VideoReactionControllerTest.TestSecurity.class)
class VideoReactionControllerTest {

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

    private static final String URL = AppConstant.API.BASE_URL + "/videos/{videoId}/reaction";
    private static final String TOKEN = "Bearer good-token";

    @MockitoBean
    VideoReactionService reactionService;

    @MockitoBean
    JwtProvider jwtProvider;

    // real JWTAuthenticationFilter needs it; mock answers version 0 = the "cv" of test tokens
    @MockitoBean
    com.canhlabs.funnyapp.cache.CredentialsVersionCache credentialsVersionCache;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Anything but the good token (including a missing header, i.e. null) is rejected like the real provider
        JwtVerificationResultDto ok = new JwtVerificationResultDto();
        ok.setData(UserDetailDto.builder().id(7L).email("u@x.com").build());
        when(jwtProvider.verifyToken(any())).thenAnswer(inv -> {
            if (TOKEN.equals(inv.getArgument(0))) {
                return ok;
            }
            throw new UnauthorizedException("TOKEN_INVALID", 601);
        });
    }

    /** MockMvc leaves servletPath empty; the real container sets it, and the JWT filter matches on it. */
    private static String path(long id) {
        return AppConstant.API.BASE_URL + "/videos/" + id + "/reaction";
    }

    private static ReactionSummaryDto summary(ReactionType mine) {
        return ReactionSummaryDto.builder().videoId(42L).likeCount(10).dislikeCount(2).myReaction(mine).build();
    }

    @Test
    void get_guest_returns200WithNullMyReaction() throws Exception {
        when(reactionService.getSummary(42L)).thenReturn(summary(null));

        mockMvc.perform(get(URL, 42).servletPath(path(42)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.likeCount").value(10))
                .andExpect(jsonPath("$.data.dislikeCount").value(2))
                .andExpect(jsonPath("$.data.myReaction").doesNotExist());
    }

    @Test
    void get_withInvalidToken_isTreatedAsGuest() throws Exception {
        when(reactionService.getSummary(42L)).thenReturn(summary(null));

        mockMvc.perform(get(URL, 42).servletPath(path(42)).header("Authorization", "Bearer garbage"))
                .andExpect(status().isOk());
    }

    @Test
    void get_withValidToken_populatesSecurityContextForService() throws Exception {
        AtomicReference<UserDetailDto> seen = new AtomicReference<>();
        when(reactionService.getSummary(42L)).thenAnswer(inv -> {
            seen.set(AppUtils.getCurrentUser());
            return summary(ReactionType.LIKE);
        });

        mockMvc.perform(get(URL, 42).servletPath(path(42)).header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myReaction").value("LIKE"));

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().getId()).isEqualTo(7L);
    }

    @Test
    void put_authenticated_populatesSecurityContextAndReturns200() throws Exception {
        AtomicReference<UserDetailDto> seen = new AtomicReference<>();
        when(reactionService.react(42L, ReactionType.LIKE)).thenAnswer(inv -> {
            seen.set(AppUtils.getCurrentUser());
            return summary(ReactionType.LIKE);
        });

        mockMvc.perform(put(URL, 42).servletPath(path(42))
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myReaction").value("LIKE"));

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().getId()).isEqualTo(7L);
    }

    @Test
    void put_withoutToken_returns401AndNeverReachesService() throws Exception {
        mockMvc.perform(put(URL, 42).servletPath(path(42))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isUnauthorized());

        verify(reactionService, never()).react(any(), any());
    }

    @Test
    void delete_withoutToken_returns401AndNeverReachesService() throws Exception {
        mockMvc.perform(delete(URL, 42).servletPath(path(42)))
                .andExpect(status().isUnauthorized());

        verify(reactionService, never()).removeReaction(any());
    }

    @Test
    void delete_authenticated_returns200() throws Exception {
        when(reactionService.removeReaction(42L)).thenReturn(summary(null));

        mockMvc.perform(delete(URL, 42).servletPath(path(42)).header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.myReaction").doesNotExist());
    }

    @Test
    void put_missingReaction_returns400() throws Exception {
        mockMvc.perform(put(URL, 42).servletPath(path(42))
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(reactionService, never()).react(any(), any());
    }

    @Test
    void put_invalidReactionValue_returns400() throws Exception {
        mockMvc.perform(put(URL, 42).servletPath(path(42))
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"MEH\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_unknownVideo_returns404() throws Exception {
        when(reactionService.react(eq(999L), any())).thenThrow(CustomException.builder()
                .status(HttpStatus.NOT_FOUND).subCode(404).message("Video not found").build());

        mockMvc.perform(put(URL, 999).servletPath(path(999))
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_unknownVideo_returns404() throws Exception {
        when(reactionService.getSummary(999L)).thenThrow(CustomException.builder()
                .status(HttpStatus.NOT_FOUND).subCode(404).message("Video not found").build());

        mockMvc.perform(get(URL, 999).servletPath(path(999))).andExpect(status().isNotFound());
    }
}
