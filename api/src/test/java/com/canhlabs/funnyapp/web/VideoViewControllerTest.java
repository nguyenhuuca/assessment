package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.service.VideoViewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VideoViewControllerTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void record_guest_usesCloudflareClientIpAndReturns204() {
        VideoViewService service = mock(VideoViewService.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("CF-Connecting-IP", "9.9.9.9");
        req.addHeader("X-Forwarded-For", "8.8.8.8");

        var response = new VideoViewController(service).record(5L, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).recordView(5L, "IP:9.9.9.9");
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
