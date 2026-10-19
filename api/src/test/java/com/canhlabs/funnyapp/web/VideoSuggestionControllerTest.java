package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.config.I18nConfig;
import com.canhlabs.funnyapp.dto.auth.JwtVerificationResultDto;
import com.canhlabs.funnyapp.dto.suggestion.SuggestionDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.enums.SuggestionFilter;
import com.canhlabs.funnyapp.enums.SuggestionStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.UnauthorizedException;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.filter.JwtProvider;
import com.canhlabs.funnyapp.service.VideoSuggestionService;
import com.canhlabs.funnyapp.utils.AppConstant;
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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real JWTAuthenticationFilter in front of the controller: guests must get 401 on every route. */
@WebMvcTest(VideoSuggestionController.class)
@Import({I18nConfig.class, VideoSuggestionControllerTest.TestSecurity.class})
class VideoSuggestionControllerTest {

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
    }

    private static final String BASE = AppConstant.API.BASE_URL + "/suggestions";
    private static final String TOKEN = "Bearer good-token";
    private static final String BODY = "{\"url\":\"https://youtu.be/dQw4w9WgXcQ\",\"title\":\"T\",\"note\":\"N\"}";

    @MockitoBean VideoSuggestionService suggestionService;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean com.canhlabs.funnyapp.cache.CredentialsVersionCache credentialsVersionCache;
    @Autowired MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtVerificationResultDto ok = new JwtVerificationResultDto();
        ok.setData(UserDetailDto.builder().id(7L).email("u@x.com").build());
        when(jwtProvider.verifyToken(any())).thenAnswer(inv -> {
            if (TOKEN.equals(inv.getArgument(0))) {
                return ok;
            }
            throw new UnauthorizedException("TOKEN_INVALID", 601);
        });
    }

    private static MockHttpServletRequestBuilder withPath(MockHttpServletRequestBuilder b, String path) {
        return b.servletPath(path); // MockMvc leaves servletPath empty; the JWT filter matches on it
    }

    private static SuggestionDto dto(long id) {
        return SuggestionDto.builder().id(id).sourceUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                .platform(ImportPlatform.YOUTUBE).title("T").note("N").status(SuggestionStatus.PENDING)
                .createdAt(Instant.parse("2026-10-15T01:02:03Z")).build();
    }

    // ── 401 for guests ────────────────────────────────────────────────────────

    @Test
    void submit_guest_401() throws Exception {
        mockMvc.perform(withPath(post(BASE), BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(suggestionService);
    }

    @Test
    void mine_guest_401() throws Exception {
        mockMvc.perform(withPath(get(BASE + "/mine"), BASE + "/mine")).andExpect(status().isUnauthorized());
        verifyNoInteractions(suggestionService);
    }

    @Test
    void submit_invalidToken_401() throws Exception {
        mockMvc.perform(withPath(post(BASE), BASE).header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(suggestionService);
    }

    // ── submit ────────────────────────────────────────────────────────────────

    @Test
    void submit_returns201WithDto_andPassesCurrentUser() throws Exception {
        when(suggestionService.submit(7L, "https://youtu.be/dQw4w9WgXcQ", "T", "N")).thenReturn(dto(5));

        mockMvc.perform(withPath(post(BASE), BASE).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.platform").value("YOUTUBE"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.live").value(false))
                .andExpect(jsonPath("$.data.liveVideoId").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-15T01:02:03Z"));
    }

    @Test
    void submit_titleOver200_400Validation_andServiceNotCalled() throws Exception {
        String longTitle = "x".repeat(201);

        mockMvc.perform(withPath(post(BASE), BASE).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://youtu.be/dQw4w9WgXcQ\",\"title\":\"" + longTitle + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("error.validation.failed"));
        verifyNoInteractions(suggestionService);
    }

    @Test
    void submit_noteOver300_400Validation() throws Exception {
        mockMvc.perform(withPath(post(BASE), BASE).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://youtu.be/dQw4w9WgXcQ\",\"note\":\"" + "n".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("error.validation.failed"));
        verifyNoInteractions(suggestionService);
    }

    @Test
    void submit_invalidUrl_400WithLocalizedMessageAndCode() throws Exception {
        when(suggestionService.submit(any(), any(), any(), any())).thenThrow(
                CustomException.of(HttpStatus.BAD_REQUEST, 4301, "error.suggestion.invalidUrl"));

        mockMvc.perform(withPath(post(BASE), BASE).header("Authorization", TOKEN)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("error.suggestion.invalidUrl"))
                .andExpect(jsonPath("$.error.message").value("Chỉ chấp nhận link https của YouTube hoặc Facebook"));

        mockMvc.perform(withPath(post(BASE), BASE).header("Authorization", TOKEN)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.error.message").value("Only https YouTube or Facebook links are accepted"));
    }

    @Test
    void submit_duplicate_409() throws Exception {
        when(suggestionService.submit(any(), any(), any(), any())).thenThrow(
                CustomException.of(HttpStatus.CONFLICT, 4092, "error.suggestion.duplicate"));

        mockMvc.perform(withPath(post(BASE), BASE).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("error.suggestion.duplicate"));
    }

    @Test
    void submit_dailyLimit_429WithLimitInMessage() throws Exception {
        when(suggestionService.submit(any(), any(), any(), any())).thenThrow(
                CustomException.of(HttpStatus.TOO_MANY_REQUESTS, 4291, "error.suggestion.dailyLimit", 5));

        mockMvc.perform(withPath(post(BASE), BASE).header("Authorization", TOKEN)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("error.suggestion.dailyLimit"))
                .andExpect(jsonPath("$.error.message").value("Bạn chỉ có thể đề xuất tối đa 5 video mỗi ngày"));
    }

    // ── mine ──────────────────────────────────────────────────────────────────

    @Test
    void mine_returnsPage_withDefaults() throws Exception {
        SuggestionDto live = dto(5);
        live.setStatus(SuggestionStatus.APPROVED);
        live.setLive(true);
        live.setLiveVideoId(501L);
        when(suggestionService.mine(eq(7L), isNull(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(live)));

        mockMvc.perform(withPath(get(BASE + "/mine"), BASE + "/mine").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(5))
                .andExpect(jsonPath("$.data.content[0].live").value(true))
                .andExpect(jsonPath("$.data.content[0].liveVideoId").value(501))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));

        verify(suggestionService).mine(7L, null, 0, 20);
    }

    @Test
    void mine_passesStatusPageAndSize() throws Exception {
        when(suggestionService.mine(any(), any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(withPath(get(BASE + "/mine").param("status", "LIVE").param("page", "2").param("size", "5"),
                        BASE + "/mine").header("Authorization", TOKEN))
                .andExpect(status().isOk());

        verify(suggestionService).mine(7L, SuggestionFilter.LIVE, 2, 5);
    }

    @Test
    void mine_unknownStatus_400() throws Exception {
        mockMvc.perform(withPath(get(BASE + "/mine").param("status", "BOGUS"), BASE + "/mine")
                        .header("Authorization", TOKEN))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(suggestionService);
    }

    // ── rereview ──────────────────────────────────────────────────────────────

    @Test
    void rereview_guest_401() throws Exception {
        mockMvc.perform(withPath(post(BASE + "/5/rereview"), BASE + "/5/rereview")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(suggestionService);
    }

    @Test
    void rereview_passesCurrentUserAndMessage_returnsDto() throws Exception {
        SuggestionDto result = dto(5);
        result.setRereviewCount(1);
        when(suggestionService.requestRereview(5L, 7L, "look again")).thenReturn(result);

        mockMvc.perform(withPath(post(BASE + "/5/rereview"), BASE + "/5/rereview").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"look again\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.rereviewCount").value(1))
                .andExpect(jsonPath("$.data.canRequestRereview").value(false))
                .andExpect(jsonPath("$.data.canReopen").value(false));
    }

    @Test
    void rereview_noBody_okWithNullMessage() throws Exception {
        when(suggestionService.requestRereview(5L, 7L, null)).thenReturn(dto(5));

        mockMvc.perform(withPath(post(BASE + "/5/rereview"), BASE + "/5/rereview").header("Authorization", TOKEN))
                .andExpect(status().isOk());

        verify(suggestionService).requestRereview(5L, 7L, null);
    }

    @Test
    void rereview_messageOver300_400() throws Exception {
        mockMvc.perform(withPath(post(BASE + "/5/rereview"), BASE + "/5/rereview").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + "m".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(suggestionService);
    }

    @Test
    void rereview_errors_passThroughStatusAndLocalizedMessage() throws Exception {
        when(suggestionService.requestRereview(1L, 7L, null)).thenThrow(
                CustomException.of(HttpStatus.NOT_FOUND, 4042, "error.suggestion.notFound"));
        when(suggestionService.requestRereview(2L, 7L, null)).thenThrow(
                CustomException.of(HttpStatus.CONFLICT, 4097, "error.suggestion.notRejected"));
        when(suggestionService.requestRereview(3L, 7L, null)).thenThrow(
                CustomException.of(HttpStatus.CONFLICT, 4098, "error.suggestion.rereviewLimit"));

        mockMvc.perform(withPath(post(BASE + "/1/rereview"), BASE + "/1/rereview").header("Authorization", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("error.suggestion.notFound"));
        mockMvc.perform(withPath(post(BASE + "/2/rereview"), BASE + "/2/rereview").header("Authorization", TOKEN)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("error.suggestion.notRejected"))
                .andExpect(jsonPath("$.error.message").value("Chỉ đề xuất đã bị từ chối mới có thể gửi yêu cầu duyệt lại"));
        mockMvc.perform(withPath(post(BASE + "/3/rereview"), BASE + "/3/rereview").header("Authorization", TOKEN)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("error.suggestion.rereviewLimit"))
                .andExpect(jsonPath("$.error.message").value("You have used all re-review requests for this suggestion"));
    }
}
