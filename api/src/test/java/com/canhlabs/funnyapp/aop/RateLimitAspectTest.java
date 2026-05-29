package com.canhlabs.funnyapp.aop;

import com.canhlabs.funnyapp.exception.RateLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RateLimitAspectTest.TestConfig.class)
class RateLimitAspectTest {

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        SlidingWindowRateLimiter slidingWindowRateLimiter() {
            return mock(SlidingWindowRateLimiter.class);
        }

        @Bean
        RateLimitAspect rateLimitAspect(SlidingWindowRateLimiter limiter) {
            return new RateLimitAspect(limiter);
        }

        @Bean
        RateLimitTestService rateLimitTestService() {
            return new RateLimitTestService();
        }
    }

    @Service
    static class RateLimitTestService {
        @RateLimited(permit = 5, windowInSeconds = 60)
        String doWork() {
            return "work-done";
        }
    }

    @Autowired
    RateLimitTestService rateLimitTestService;

    @Autowired
    SlidingWindowRateLimiter limiter;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        // Reset Mockito state: Spring singleton mock accumulates invocations across tests
        reset(limiter);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.setRequestURI("/api/test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        // Default: allow every request
        when(limiter.allowRequest(anyString(), anyString(), anyInt(), anyLong())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // ── 1. Limiter allows → method executes and returns result ────────────────

    @Test
    void rateLimit_limiterAllows_methodExecutesAndReturnsResult() {
        String result = rateLimitTestService.doWork();

        assertThat(result).isEqualTo("work-done");
    }

    // ── 2. Limiter denies → RateLimitExceededException thrown ────────────────

    @Test
    void rateLimit_limiterDenies_throwsRateLimitExceededException() {
        when(limiter.allowRequest(anyString(), anyString(), anyInt(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> rateLimitTestService.doWork())
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Rate limit exceeded. Please try again later.");
    }

    // ── 3. clientKey is "USER:username" for authenticated user ───────────────

    @Test
    void rateLimit_authenticatedUser_clientKeyPrefixedWithUser() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("carol@example.com", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        rateLimitTestService.doWork();

        ArgumentCaptor<String> clientKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).allowRequest(clientKeyCaptor.capture(), anyString(), anyInt(), anyLong());
        assertThat(clientKeyCaptor.getValue()).isEqualTo("USER:carol@example.com");
    }

    // ── 4. clientKey is "IP:remoteAddr" for anonymous user ───────────────────

    @Test
    void rateLimit_anonymousUser_clientKeyPrefixedWithIP() {
        // SecurityContextHolder was cleared in setUp → no auth
        request.setRemoteAddr("172.16.0.99");

        rateLimitTestService.doWork();

        ArgumentCaptor<String> clientKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).allowRequest(clientKeyCaptor.capture(), anyString(), anyInt(), anyLong());
        assertThat(clientKeyCaptor.getValue()).isEqualTo("IP:172.16.0.99");
    }

    // ── 5. permit and windowInSeconds are passed correctly to limiter ─────────

    @Test
    void rateLimit_annotationValues_passedCorrectlyToLimiter() {
        rateLimitTestService.doWork();

        // @RateLimited(permit=5, windowInSeconds=60) → windowMs = 60 * 1000L = 60_000
        verify(limiter).allowRequest(anyString(), eq("/api/test"), eq(5), eq(60_000L));
    }

    // ── 6. apiKey is the request URI ──────────────────────────────────────────

    @Test
    void rateLimit_apiKeyIsRequestUri() {
        request.setRequestURI("/v1/funny-app/videos");

        rateLimitTestService.doWork();

        ArgumentCaptor<String> apiKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).allowRequest(anyString(), apiKeyCaptor.capture(), anyInt(), anyLong());
        assertThat(apiKeyCaptor.getValue()).isEqualTo("/v1/funny-app/videos");
    }

    // ── 7. windowMs conversion: windowInSeconds * 1000L ──────────────────────

    @Test
    void rateLimit_windowMsIsWindowInSecondsTimesThousand() {
        rateLimitTestService.doWork();

        ArgumentCaptor<Long> windowMsCaptor = ArgumentCaptor.forClass(Long.class);
        verify(limiter).allowRequest(anyString(), anyString(), anyInt(), windowMsCaptor.capture());
        // windowInSeconds = 60 → 60 * 1000L = 60_000
        assertThat(windowMsCaptor.getValue()).isEqualTo(60_000L);
    }
}
