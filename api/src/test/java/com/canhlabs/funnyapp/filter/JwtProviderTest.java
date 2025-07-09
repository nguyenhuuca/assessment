package com.canhlabs.funnyapp.filter;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.JwtGenerationDto;
import com.canhlabs.funnyapp.dto.JwtVerificationResultDto;
import com.canhlabs.funnyapp.dto.TokenDto;
import com.canhlabs.funnyapp.dto.UserDetailDto;
import com.canhlabs.funnyapp.share.exception.UnauthorizedException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private AppProperties appProperties;
    private final String secret = "testsecretkeytestsecretkeytestse"; // 32 chars

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider();
        appProperties = mock(AppProperties.class);
        when(appProperties.getJwtSecretKey()).thenReturn(secret);
        jwtProvider.injectProps(appProperties);
    }

    @Test
    void generateToken_returnsTokenWithCorrectPayload() {
        JwtGenerationDto request = JwtGenerationDto.builder()
                .payload(UserDetailDto.builder().id(42L).email("user@domain.com").build())
                .duration(3600000L)
                .build();

        TokenDto tokenDto = jwtProvider.generateToken(request);

        assertThat(tokenDto).isNotNull();
        assertThat(tokenDto.getToken()).isNotEmpty();
    }

    @Test
    void generateToken_usesDefaultDurationWhenDurationIsNull() {
        JwtGenerationDto request = JwtGenerationDto.builder()
                .payload(UserDetailDto.builder().id(1L).email("a@b.com").build())
                .build();

        TokenDto tokenDto = jwtProvider.generateToken(request);

        assertThat(tokenDto).isNotNull();
        assertThat(tokenDto.getToken()).isNotEmpty();
    }

    @Test
    void verifyToken_returnsSuccessfulResultForValidToken() {
        String token = Jwts.builder()
                .setClaims(Map.of("id", 7L, "email", "test@abc.com"))
                .setExpiration(new Date(System.currentTimeMillis() + 3600000L))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();

        JwtVerificationResultDto result = jwtProvider.verifyToken(token);

        assertThat(result.getSuccessful()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(7L);
        assertThat(result.getData().getEmail()).isEqualTo("test@abc.com");
    }

    @Test
    void verifyToken_throwsUnauthorizedExceptionForExpiredToken() {
        String token = Jwts.builder()
                .setClaims(Map.of("id", 2L, "email", "expired@abc.com"))
                .setExpiration(new Date(System.currentTimeMillis() - 1000L))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();

        assertThatThrownBy(() -> jwtProvider.verifyToken(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("TOKEN_EXPIRED");
    }

    @Test
    void verifyToken_throwsUnauthorizedExceptionForInvalidToken() {
        String token = "invalid.token.value";

        assertThatThrownBy(() -> jwtProvider.verifyToken(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("TOKEN_INVALID");
    }
}