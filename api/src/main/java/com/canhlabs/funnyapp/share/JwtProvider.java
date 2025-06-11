package com.canhlabs.funnyapp.share;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.JwtGenerationDto;
import com.canhlabs.funnyapp.dto.JwtVerificationResultDto;
import com.canhlabs.funnyapp.dto.TokenDto;
import com.canhlabs.funnyapp.dto.UserDetailDto;
import com.canhlabs.funnyapp.share.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.canhlabs.funnyapp.share.AppUtils.getNonNull;

/**
 * Using to generate/verify token, revoke token in case it become invalid: supend user, in-active user
 *
 */
@Component
@Slf4j
public class JwtProvider {
    private AppProperties props;

    @Autowired
    public void injectProps(AppProperties props) {
        this.props = props;
    }


    /**
     *
     * @param request hold the user info that was included to token string,
     * Default expire time for token is 24h
     * @return token string
     */
    public TokenDto generateToken(JwtGenerationDto request) {
        JwtBuilder jwtBuilder = Jwts.builder().setClaims(generatePayload(request.getPayload()));
        Date expireDate = getExpiredDate(request);
        jwtBuilder.setExpiration(expireDate);
        jwtBuilder.setIssuedAt(new Date());
        String token = jwtBuilder.signWith(getKey()).compact();
        return TokenDto.builder()
                .token(token)
                .build();

    }

    /**
     * Checking token invalid
     * @param token to verify that sent by client
     * @return result verification, include http status and sub code
     */
    public JwtVerificationResultDto verifyToken(String token) throws UnauthorizedException {
        JwtVerificationResultDto rs = JwtVerificationResultDto.builder().build();
        try {
            var claims = Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token.replace("Bearer", ""));
            rs.setData(convertValue(claims.getBody()));
            rs.setSuccessful(true);


        } catch (ExpiredJwtException exExpire) {
            log.error("Token expire: {}", exExpire.getMessage());
            throw new UnauthorizedException("TOKEN_EXPIRED", 601);
        } catch (Exception ex) {
            throw new UnauthorizedException("TOKEN_INVALID", 601);
        }
        return  rs;

    }


    /**
     *
     * @param request get expire time from request, default is 24h
     * @return date expire
     */
    private Date getExpiredDate(JwtGenerationDto request) {
        Long expiredDuration = request.getDuration();
        if (expiredDuration == null || expiredDuration <= 0) {
            expiredDuration = (1_000L * 84600); // milliseconds(1 day)
        }
        return new Date(System.currentTimeMillis() + expiredDuration);
    }


    /**
     * Using get jwt secret key from configure file, keys have to length = 32(256bit)
     * @return Key  that use to sign when generate token
     */
    private Key getKey() {
        return Keys.hmacShaKeyFor(props.getJwtSecretKey().getBytes());
    }

    /**
     * Creating the body for token
     * @param request user info will be added into token body
     * @return Map value to add to token builder
     */
    private Map<String, Object> generatePayload(UserDetailDto request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", request.getId());
        payload.put("email", request.getEmail());
        return payload;
    }

    private UserDetailDto convertValue(Claims body) {
        return UserDetailDto.builder()
                .id(Long.valueOf(getNonNull(body.get("id"))))
                .email(getNonNull(body.get("email")))
                .build();
    }

}
