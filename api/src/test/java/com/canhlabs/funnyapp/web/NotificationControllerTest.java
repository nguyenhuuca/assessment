package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.auth.JwtVerificationResultDto;
import com.canhlabs.funnyapp.dto.notification.NotificationDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.NotificationType;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.UnauthorizedException;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.filter.JwtProvider;
import com.canhlabs.funnyapp.service.NotificationService;
import com.canhlabs.funnyapp.service.notification.NotificationStream;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses the REAL JWTAuthenticationFilter (only JwtProvider mocked): the notification routes are ordinary
 * authenticated routes, so the filter must answer 401 when the Authorization header is missing.
 */
@WebMvcTest(NotificationController.class)
@Import(NotificationControllerTest.TestSecurity.class)
class NotificationControllerTest {

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

    private static final String BASE = AppConstant.API.BASE_URL + "/notifications";
    private static final String TOKEN = "Bearer good-token";

    @MockitoBean NotificationService notificationService;
    @MockitoBean NotificationStream notificationStream;
    @MockitoBean JwtProvider jwtProvider;
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

    /** MockMvc leaves servletPath empty; the real container sets it and the JWT filter matches on it. */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withPath(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String path) {
        return b.servletPath(path);
    }

    // ── 401 without JWT ───────────────────────────────────────────────────────

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(withPath(get(BASE), BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);
    }

    @Test
    void unreadCount_withoutToken_returns401() throws Exception {
        mockMvc.perform(withPath(get(BASE + "/unread-count"), BASE + "/unread-count"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);
    }

    @Test
    void stream_withoutAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(withPath(get(BASE + "/stream").accept(MediaType.TEXT_EVENT_STREAM), BASE + "/stream"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationStream);
    }

    @Test
    void stream_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(withPath(get(BASE + "/stream").header("Authorization", "Bearer garbage")
                        .accept(MediaType.TEXT_EVENT_STREAM), BASE + "/stream"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationStream);
    }

    @Test
    void markRead_withoutToken_returns401() throws Exception {
        String path = BASE + "/" + UUID.randomUUID() + "/read";
        mockMvc.perform(withPath(patch(path), path)).andExpect(status().isUnauthorized());
        verify(notificationService, never()).markRead(any());
    }

    @Test
    void markAllRead_withoutToken_returns401() throws Exception {
        mockMvc.perform(withPath(patch(BASE + "/read-all"), BASE + "/read-all")).andExpect(status().isUnauthorized());
        verify(notificationService, never()).markAllRead();
    }

    // ── list / paging ─────────────────────────────────────────────────────────

    @Test
    void list_returnsPageWrappedInResultObject() throws Exception {
        UUID id = UUID.randomUUID();
        NotificationDto dto = NotificationDto.builder().id(id).type(NotificationType.COMMENT_REPLY).videoId("22")
                .actorDisplay("bob").actorCount(3).snippet("haha").read(false)
                .updatedAt(Instant.parse("2026-10-02T10:00:00Z")).build();
        when(notificationService.list(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(withPath(get(BASE).header("Authorization", TOKEN), BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data.content[0].type").value("COMMENT_REPLY"))
                .andExpect(jsonPath("$.data.content[0].actorDisplay").value("bob"))
                .andExpect(jsonPath("$.data.content[0].actorCount").value(3))
                .andExpect(jsonPath("$.data.content[0].snippet").value("haha"))
                .andExpect(jsonPath("$.data.content[0].read").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void list_defaultsToPage0Size20() throws Exception {
        when(notificationService.list(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(withPath(get(BASE).header("Authorization", TOKEN), BASE)).andExpect(status().isOk());

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).list(cap.capture());
        assertThat(cap.getValue().getPageNumber()).isZero();
        assertThat(cap.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void list_pagingParamsPassedAndSizeIsCapped() throws Exception {
        when(notificationService.list(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(withPath(get(BASE).param("page", "2").param("size", "1000")
                .header("Authorization", TOKEN), BASE)).andExpect(status().isOk());

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).list(cap.capture());
        assertThat(cap.getValue().getPageNumber()).isEqualTo(2);
        assertThat(cap.getValue().getPageSize()).isEqualTo(NotificationController.MAX_PAGE_SIZE);
    }

    @Test
    void list_negativePageAndZeroSize_areClamped_andClientSortIgnored() throws Exception {
        when(notificationService.list(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(withPath(get(BASE).param("page", "-4").param("size", "0").param("sort", "userId,asc")
                .header("Authorization", TOKEN), BASE)).andExpect(status().isOk());

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).list(cap.capture());
        assertThat(cap.getValue().getPageNumber()).isZero();
        assertThat(cap.getValue().getPageSize()).isEqualTo(1);
        assertThat(cap.getValue().getSort().isSorted()).isFalse();
    }

    // ── unread count ──────────────────────────────────────────────────────────

    @Test
    void unreadCount_returnsCount() throws Exception {
        when(notificationService.unreadCount()).thenReturn(5L);

        mockMvc.perform(withPath(get(BASE + "/unread-count").header("Authorization", TOKEN), BASE + "/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.count").value(5));
    }

    // ── read / read-all ───────────────────────────────────────────────────────

    @Test
    void markRead_own_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        String path = BASE + "/" + id + "/read";

        mockMvc.perform(withPath(patch(path).header("Authorization", TOKEN), path)).andExpect(status().isNoContent());

        verify(notificationService).markRead(id);
    }

    @Test
    void markRead_otherUsersNotification_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        String path = BASE + "/" + id + "/read";
        doThrow(CustomException.builder().status(HttpStatus.NOT_FOUND).subCode(4041).message("Notification not found").build())
                .when(notificationService).markRead(id);

        mockMvc.perform(withPath(patch(path).header("Authorization", TOKEN), path)).andExpect(status().isNotFound());
    }

    @Test
    void markRead_invalidUuid_returns400() throws Exception {
        String path = BASE + "/not-a-uuid/read";

        mockMvc.perform(withPath(patch(path).header("Authorization", TOKEN), path)).andExpect(status().isBadRequest());

        verify(notificationService, never()).markRead(any());
    }

    @Test
    void markAllRead_returns204() throws Exception {
        mockMvc.perform(withPath(patch(BASE + "/read-all").header("Authorization", TOKEN), BASE + "/read-all"))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllRead();
    }

    // ── stream ────────────────────────────────────────────────────────────────

    @Test
    void stream_authenticated_registersForCurrentUserWithProxySafeHeaders() throws Exception {
        // Headers and the first frame are flushed together on the first send (the real publisher sends the
        // current unread count on connect, buffered until the response is initialised)
        when(notificationStream.register(7L)).thenAnswer(inv -> {
            SseEmitter emitter = new SseEmitter();
            emitter.send(SseEmitter.event().name("unread").data("{\"unread\":2}"));
            return emitter;
        });

        MvcResult result = mockMvc.perform(withPath(get(BASE + "/stream").header("Authorization", TOKEN)
                        .accept(MediaType.TEXT_EVENT_STREAM), BASE + "/stream"))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-cache");
        assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("event:unread\ndata:{\"unread\":2}\n\n");
        verify(notificationStream).register(7L);
    }
}
