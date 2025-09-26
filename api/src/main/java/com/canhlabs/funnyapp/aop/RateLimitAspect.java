package com.canhlabs.funnyapp.aop;

import com.canhlabs.funnyapp.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class RateLimitAspect {

    private final SlidingWindowRateLimiter limiter;

    @Autowired
    public RateLimitAspect(SlidingWindowRateLimiter limiter) {
        this.limiter = limiter;
    }


    @Around("@annotation(rateLimited)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String clientKey = getClientKey(request, joinPoint);
        String apiKey = request.getRequestURI();

        boolean allowed = limiter.allowRequest(
                clientKey,
                apiKey,
                rateLimited.permit(),
                rateLimited.windowInSeconds() * 1000L
        );

        if (!allowed) {
            throw new RateLimitExceededException("Rate limit exceeded. Please try again later.");
        }

        return joinPoint.proceed();
    }

    private String getClientKey(HttpServletRequest request, ProceedingJoinPoint joinPoint) {
        // get user if authenticated, else use IP address
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return "USER:" + auth.getName();
        }
        return "IP:" + request.getRemoteAddr();
    }
}
