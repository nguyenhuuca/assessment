package com.canhlabs.funnyapp.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
public class AuditLogAspect {

    private MaskingUtils maskingUtil;

    @Autowired
    public void injectMaskingUtil(MaskingUtils maskingUtil) {
        this.maskingUtil = maskingUtil;
    }

    @Around("execution(* *(..)) && (@within(com.canhlabs.funnyapp.aop.AuditLog) || @annotation(com.canhlabs.funnyapp.aop.AuditLog))")
    public Object logAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = method.getDeclaringClass();

        // IP & input
        String clientIp = getClientIP();
        String username = getUsername();
        String methodName = targetClass.getSimpleName() + "." + method.getName();
        String inputArgs = Arrays.stream(joinPoint.getArgs())
                .map(maskingUtil::maskSensitiveFields)
                .map(maskingUtil::toJsonSafe)
                .collect(Collectors.joining(", "));

        Object result;
        try {
            result = joinPoint.proceed();
            Object maskedResult = maskingUtil.maskSensitiveFields(result);
            String outputStr;
            if (maskedResult instanceof List<?> listResult) {
                outputStr = String.format("List(size=%d)", listResult.size());
            } else {
                outputStr = maskingUtil.toJsonSafe(maskedResult);
            }

            long duration = System.currentTimeMillis() - start;

            log.info("AUDIT | IP={} | User={} | Method={} | Input={} | Output={} | Time={}ms",
                    clientIp, username, methodName, inputArgs,
                    new ObjectMapper().writeValueAsString(outputStr),
                    duration);
            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - start;
            log.error("AUDIT | IP={} | User={} | Method={} | Input={} | Exception={} | Time={}ms",
                    clientIp, username, methodName, inputArgs,
                    ex.getMessage(), duration);
            throw ex;
        }
    }

    private static String getUsername() {
        String username = "anonymous";
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails userDetails) {
                username = userDetails.getUsername();
            } else {
                username = principal.toString();
            }
        }
        return username;
    }

    private String getClientIP() {
        try {
            HttpServletRequest request =
                    ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            return forwarded != null ? forwarded.split(",")[0] : request.getRemoteAddr();
        } catch (Exception e) {
            log.error("getClientIP error", e);
            return "N/A";
        }
    }

}