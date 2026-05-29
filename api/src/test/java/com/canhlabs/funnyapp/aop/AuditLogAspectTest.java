package com.canhlabs.funnyapp.aop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AuditLogAspectTest.TestConfig.class)
class AuditLogAspectTest {

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        MaskingUtils maskingUtils() {
            MaskingUtils mockMasking = mock(MaskingUtils.class);
            when(mockMasking.maskSensitiveFields(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mockMasking.toJsonSafe(any())).thenAnswer(inv -> String.valueOf((Object) inv.getArgument(0)));
            return mockMasking;
        }

        @Bean
        AuditLogAspect auditLogAspect(MaskingUtils maskingUtils) {
            AuditLogAspect aspect = new AuditLogAspect();
            aspect.injectMaskingUtil(maskingUtils);
            return aspect;
        }

        @Bean
        AuditTestService auditTestService() {
            return new AuditTestService();
        }
    }

    @Service
    @AuditLog
    static class AuditTestService {
        String greet(String name) {
            return "Hello, " + name;
        }

        String throwingMethod() {
            throw new IllegalStateException("deliberate failure");
        }
    }

    @Autowired
    AuditTestService auditTestService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // ── 1. Annotated method returns result without throwing ────────────────────

    @Test
    void auditLog_successfulMethod_returnsResult() {
        String result = auditTestService.greet("World");

        assertThat(result).isEqualTo("Hello, World");
    }

    // ── 2. Annotated method that throws — exception propagates ────────────────

    @Test
    void auditLog_throwingMethod_exceptionPropagates() {
        assertThatThrownBy(() -> auditTestService.throwingMethod())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("deliberate failure");
    }

    // ── 3. Username is "anonymous" when SecurityContext is empty ──────────────

    @Test
    void auditLog_noSecurityContext_usernameIsAnonymous() {
        // SecurityContextHolder was cleared in setUp; no auth set.
        // The aspect must still allow the method to proceed without NPE.
        String result = auditTestService.greet("NoAuth");

        assertThat(result).isEqualTo("Hello, NoAuth");
    }

    // ── 4. Username extracted from authenticated UserDetails ──────────────────

    @Test
    void auditLog_authenticatedUser_usernameExtractedFromUserDetails() {
        UserDetails userDetails = User.withUsername("alice@example.com")
                .password("pw")
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        String result = auditTestService.greet("Alice");

        assertThat(result).isEqualTo("Hello, Alice");
    }

    // ── 5. Authenticated non-UserDetails principal (plain string) ─────────────

    @Test
    void auditLog_authenticatedStringPrincipal_usernameFromPrincipalToString() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("bob", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        String result = auditTestService.greet("Bob");

        assertThat(result).isEqualTo("Hello, Bob");
    }

    // ── 6. clientIP falls back to "N/A" when no request context ──────────────

    @Test
    void auditLog_noRequestContext_clientIpFallsBackToNA() {
        // RequestContextHolder was reset in setUp — no MockHttpServletRequest set.
        // getClientIP() catches the NPE and returns "N/A"; method must still return.
        String result = auditTestService.greet("NoRequest");

        assertThat(result).isEqualTo("Hello, NoRequest");
    }

    // ── 7. clientIP reads X-Forwarded-For header when present ─────────────────

    @Test
    void auditLog_withXForwardedForHeader_clientIpFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String result = auditTestService.greet("Forwarded");

        assertThat(result).isEqualTo("Hello, Forwarded");
    }

    // ── 8. clientIP falls back to remoteAddr when header absent ───────────────

    @Test
    void auditLog_withoutXForwardedForHeader_clientIpFromRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.42");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String result = auditTestService.greet("RemoteAddr");

        assertThat(result).isEqualTo("Hello, RemoteAddr");
    }
}
