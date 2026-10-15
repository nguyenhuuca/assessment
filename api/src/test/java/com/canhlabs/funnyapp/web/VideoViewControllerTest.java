package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.service.VideoViewService;
import com.canhlabs.funnyapp.service.WatchHistoryService;
import com.canhlabs.funnyapp.utils.AppUtils;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoViewControllerTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void record_guest_usesCloudflareClientIpAndReturns204() {
        VideoViewService service = mock(VideoViewService.class);
        WatchHistoryService history = mock(WatchHistoryService.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("CF-Connecting-IP", "9.9.9.9");
        req.addHeader("X-Forwarded-For", "8.8.8.8");

        var response = new VideoViewController(service, history).record(5L, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).recordView(5L, "IP:9.9.9.9");
        verify(history, never()).recordWatch(anyLong(), anyLong());
    }

    @Test
    void record_loggedInUser_recordsHistoryEvenWhenViewIsDeduped() {
        VideoViewService service = mock(VideoViewService.class);
        WatchHistoryService history = mock(WatchHistoryService.class);
        when(service.recordView(5L, "IP:1.2.3.4")).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("1.2.3.4");

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(UserDetailDto.builder().id(7L).email("u@x.com").build());

            var response = new VideoViewController(service, history).record(5L, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
        verify(history).recordWatch(7L, 5L);
    }

    @Test
    void record_historyFailure_stillReturns204() {
        VideoViewService service = mock(VideoViewService.class);
        WatchHistoryService history = mock(WatchHistoryService.class);
        doThrow(new IllegalStateException("db down")).when(history).recordWatch(7L, 5L);

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(UserDetailDto.builder().id(7L).build());

            var response = new VideoViewController(service, history).record(5L, new MockHttpServletRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
        verify(service).recordView(5L, "IP:127.0.0.1");
    }

    @Test
    void record_userWithoutId_recordsNoHistory() {
        WatchHistoryService history = mock(WatchHistoryService.class);

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(UserDetailDto.builder().email("x@y.z").build());

            new VideoViewController(mock(VideoViewService.class), history).record(5L, new MockHttpServletRequest());
        }
        verify(history, never()).recordWatch(anyLong(), anyLong());
    }

    @Test
    void viewerKey_authenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("a@test.com", null, AuthorityUtils.createAuthorityList("ROLE_USER")));

        assertThat(VideoViewController.viewerKey(new MockHttpServletRequest())).isEqualTo("USER:a@test.com");
    }

    @Test
    void viewerKey_anonymousToken_fallsBackToIp() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("k", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");

        assertThat(VideoViewController.viewerKey(req)).isEqualTo("IP:10.0.0.1");
    }

    @Test
    void clientIp_forwardedForFirstHop() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", " 1.1.1.1 , 2.2.2.2");

        assertThat(VideoViewController.clientIp(req)).isEqualTo("1.1.1.1");
    }
}
