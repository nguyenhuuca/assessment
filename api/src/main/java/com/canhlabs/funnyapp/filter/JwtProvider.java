package com.canhlabs.funnyapp.filter;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.auth.JwtGenerationDto;
import com.canhlabs.funnyapp.dto.auth.JwtVerificationResultDto;
import com.canhlabs.funnyapp.dto.auth.TokenDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.canhlabs.funnyapp.utils.AppUtils.getNonNull;

/**
 * Using to generate/verify token, revoke token in case it become invalid: supend user, in-active user
 */
@Component
@Slf4j
public class JwtProvider {
    private AppProperties props;
    // Built once from the configured secret; volatile so a racing double build in ensureInit() is harmless.
    private volatile SecretKey signingKey;
    private volatile JwtParser parser;

    @Autowired
    public void injectProps(AppProperties props) {
        this.props = props;
    }

    /**
     * Initialises the jjwt classes (Jwts, Jwts$SIG/ENC/KEY, the JSON reader) once on the main thread, before
     * Tomcat accepts requests. Otherwise the first burst of authenticated requests after a deploy runs that class
     * init on virtual threads: the waiters pin every carrier while the initialiser waits for a nested-jar lock,
     * and the app hangs (prod 2026-10-15 and 2026-10-24). A failure here never blocks startup.
     */
    @PostConstruct
    void warmUp() {
        try {
            ensureInit();
            String token = Jwts.builder().subject("warm-up").signWith(signingKey).compact();
            parser.parseSignedClaims(token);
        } catch (Exception e) {
            log.warn("JWT warm-up failed: {}", e.getMessage());
        }
    }


    /**
     * @param request hold the user info that was included to token string,
     *                Default expire time for token is 24h
     * @return token string
     */
    public TokenDto generateToken(JwtGenerationDto request) {
        ensureInit();
        JwtBuilder jwtBuilder = Jwts.builder().claims(generatePayload(request.getPayload()));
        Date expireDate = getExpiredDate(request);
        jwtBuilder.expiration(expireDate);
        jwtBuilder.issuedAt(new Date());
        String token = jwtBuilder.signWith(signingKey).compact();
        return TokenDto.builder()
                .token(token)
                .build();

    }

    /**
     * Checking token invalid
     *
     * @param token to verify that sent by client
     * @return result verification, include http status and sub code
     */
    public JwtVerificationResultDto verifyToken(String token) throws UnauthorizedException {
        JwtVerificationResultDto rs = JwtVerificationResultDto.builder().build();
        try {
            ensureInit();
            var claims = parser
                    .parseSignedClaims(token.replace("Bearer", ""));
            rs.setData(convertValue(claims.getPayload()));
            rs.setSuccessful(true);


        } catch (ExpiredJwtException exExpire) {
            log.error("Token expire: {}", exExpire.getMessage());
            throw new UnauthorizedException("TOKEN_EXPIRED", 602);
        } catch (Exception ex) {
            throw new UnauthorizedException("TOKEN_INVALID", 601);
        }
        return rs;

    }


    /**
     * @param request get expire time from request, default is 24h
     * @return date expire
     */
    private Date getExpiredDate(JwtGenerationDto request) {
        Long expiredDuration = request.getDuration();
        if (expiredDuration == null || expiredDuration <= 0) {
            expiredDuration = (1_000L * 84600 * 30); // milliseconds(30 day)
        }
        return new Date(System.currentTimeMillis() + expiredDuration);
    }


    /**
     * Builds the signing key (secret from the configure file, length = 32 / 256 bit) and the parser once.
     * Idempotent and called on every use, so a swallowed warm-up failure cannot leave them null.
     */
    private void ensureInit() {
        if (signingKey == null || parser == null) {
            SecretKey key = Keys.hmacShaKeyFor(props.getJwtSecretKey().getBytes());
            parser = Jwts.parser().verifyWith(key).build();
            signingKey = key;
        }
    }

    /**
     * Creating the body for token
     *
     * @param request user info will be added into token body
     * @return Map value to add to token builder
     */
    private Map<String, Object> generatePayload(UserDetailDto request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", request.getId());
        payload.put("email", request.getEmail());
        payload.put("permissions", request.getPermissions());
        payload.put("role", request.getRole() != null ? request.getRole() : "USER");
        payload.put("cv", request.getCredentialsVersion());
        return payload;
    }

    /** Tokens issued before PW-4 carry no "cv" claim; they count as version 0 (the column default). */
    private static int parseCredentialsVersion(Object raw) {
        return raw instanceof Number n ? n.intValue() : 0;
    }

    private UserDetailDto convertValue(Claims body) {
        String permissionsRaw = getNonNull(body.get("permissions"));
        return UserDetailDto.builder()
                .id(Long.valueOf(getNonNull(body.get("id"))))
                .email(getNonNull(body.get("email")))
                .role(getNonNull(body.get("role")))
                .permissions(permissionsRaw.isEmpty() ? 0 : Integer.parseInt(permissionsRaw))
                .credentialsVersion(parseCredentialsVersion(body.get("cv")))
                .build();
    }

}
