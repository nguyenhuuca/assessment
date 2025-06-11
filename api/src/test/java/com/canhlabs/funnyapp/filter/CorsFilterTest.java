package com.canhlabs.funnyapp.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorsFilterTest {

    private CorsFilter corsFilter;
    private ServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        corsFilter = new CorsFilter();
        request = mock(ServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    void doFilter_shouldSetCorsHeadersAndContinueChain() throws IOException, ServletException {
        corsFilter.doFilter(request, response, chain);

        verify(response).setHeader("Access-Control-Allow-Origin", "*");
        verify(response).setHeader("Access-Control-Allow-Methods", "POST, PUT, GET, OPTIONS, DELETE");
        verify(response).setHeader("Access-Control-Max-Age", "3600");
        verify(response).setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Content-Length, X-Requested-With");
        verify(chain).doFilter(request, response);
    }
}