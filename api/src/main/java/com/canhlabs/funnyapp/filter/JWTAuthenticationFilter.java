package com.canhlabs.funnyapp.filter;

import com.canhlabs.funnyapp.share.JwtProvider;
import com.canhlabs.funnyapp.dto.JwtVerificationResultDto;
import com.canhlabs.funnyapp.dto.UserDetailDto;
import com.canhlabs.funnyapp.share.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

import static com.canhlabs.funnyapp.share.AppConstant.WebIgnoringConfig.SWAGGER_DOC;
import static com.canhlabs.funnyapp.share.AppConstant.WebIgnoringConfig.WHITE_LIST_PATH;


/**
 * Using to filter request need to check security
 */
@Component
@Slf4j
public class JWTAuthenticationFilter extends OncePerRequestFilter {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private JwtProvider jwtProvider;

    @Autowired
    public void injectJwt(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse, @NotNull FilterChain filterChain) throws ServletException, IOException {

        String token = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);
        try {
            UsernamePasswordAuthenticationToken authentication;
            JwtVerificationResultDto verificationResult = jwtProvider.verifyToken(token);
            UserDetailDto user = verificationResult.getData();
            authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null, Collections.emptyList());
            authentication.setDetails(user);
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (UnauthorizedException e) {
            log.error("Error UnauthorizedException", e);
            httpServletResponse.setContentType("application/json;charset=UTF-8");
            httpServletResponse.getWriter().write(e.toJson());
            httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;

        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        boolean isWhitePath = WHITE_LIST_PATH.stream().anyMatch(p -> pathMatcher.match(p.getFullPath(), path) && p.getMethod().equals(method));
        boolean isApiDocPath = SWAGGER_DOC.stream().anyMatch(p -> pathMatcher.match(p, path));
        return isWhitePath || isApiDocPath;
    }

}
