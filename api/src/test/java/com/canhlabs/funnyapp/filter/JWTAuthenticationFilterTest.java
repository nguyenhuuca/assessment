package com.canhlabs.funnyapp.filter;

import com.canhlabs.funnyapp.dto.auth.JwtVerificationResultDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JWTAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private JWTAuthenticationFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new JWTAuthenticationFilter();
        filter.injectJwt(jwtProvider);
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_shouldAuthenticate_whenTokenValid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserDetailDto user = UserDetailDto.builder().email("test@example.com").build();
        JwtVerificationResultDto result = new JwtVerificationResultDto();
        result.setData(user);

        when(jwtProvider.verifyToken("valid-token")).thenReturn(result);

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("test@example.com", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_shouldReturnUnauthorized_whenTokenInvalid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UnauthorizedException ex = new UnauthorizedException("Invalid", 401);
        when(jwtProvider.verifyToken("invalid-token")).thenThrow(ex);

        filter.doFilterInternal(request, response, filterChain);

        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldNotFilter_shouldReturnTrue_forWhiteListPath() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/v1/funny-app/user/join");
        when(request.getMethod()).thenReturn("POST");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_shouldReturnTrue_forSwaggerDocPath() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/swagger-ui/index.html");
        when(request.getMethod()).thenReturn("GET");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_reactionGet_isFalse_soOptionalTokenIsStillRead() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/v1/funny-app/videos/42/reaction");
        when(request.getMethod()).thenReturn("GET");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_reactionPut_isFalse() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/v1/funny-app/videos/42/reaction");
        when(request.getMethod()).thenReturn("PUT");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void doFilterInternal_optionalAuthPath_withoutToken_continuesAsGuest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/funny-app/videos/42/reaction");
        request.setServletPath("/v1/funny-app/videos/42/reaction");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtProvider);
    }

    @Test
    void doFilterInternal_optionalAuthPath_withBadToken_continuesAsGuest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/funny-app/videos/42/reaction");
        request.setServletPath("/v1/funny-app/videos/42/reaction");
        request.addHeader(HttpHeaders.AUTHORIZATION, "bad");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtProvider.verifyToken("bad")).thenThrow(new UnauthorizedException("TOKEN_INVALID", 601));

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_reactionPut_badTokenStillRejected() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/v1/funny-app/videos/42/reaction");
        request.setServletPath("/v1/funny-app/videos/42/reaction");
        request.addHeader(HttpHeaders.AUTHORIZATION, "bad");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtProvider.verifyToken("bad")).thenThrow(new UnauthorizedException("TOKEN_INVALID", 601));

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_commentPost_withValidToken_populatesUser() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/funny-app/videos/42/comments");
        request.setServletPath("/v1/funny-app/videos/42/comments");
        request.addHeader(HttpHeaders.AUTHORIZATION, "valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        JwtVerificationResultDto result = new JwtVerificationResultDto();
        result.setData(UserDetailDto.builder().email("author@example.com").build());
        when(jwtProvider.verifyToken("valid-token")).thenReturn(result);

        assertFalse(filter.shouldNotFilter(request));
        filter.doFilterInternal(request, response, filterChain);

        assertEquals("author@example.com", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_commentPost_withoutToken_continuesAsGuest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/funny-app/videos/42/comments");
        request.setServletPath("/v1/funny-app/videos/42/comments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotFilter_shouldReturnFalse_forProtectedPath() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/api/protected");
        when(request.getMethod()).thenReturn("GET");

        assertFalse(filter.shouldNotFilter(request));
    }
}