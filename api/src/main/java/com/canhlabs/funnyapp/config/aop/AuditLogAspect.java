package com.canhlabs.funnyapp.config.aop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
public class AuditLogAspect {
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "token", "accessToken", "jwt", "refreshToken", "otp", "secret", "pin"
    );

    @Around("execution(* *(..)) && (@within(com.canhlabs.funnyapp.config.aop.AuditLog) || @annotation(com.canhlabs.funnyapp.config.aop.AuditLog))")
    public Object logAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = method.getDeclaringClass();

        // get annotation from method/ class
        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        if (auditLog == null) {
            auditLog = targetClass.getAnnotation(AuditLog.class);
        }

        // IP & input
        String clientIp = getClientIP();
        String methodName = targetClass.getSimpleName() + "." + method.getName();
        String inputArgs = Arrays.stream(joinPoint.getArgs())
                .map(this::maskSensitiveData)
                .map(Object::toString)
                .collect(Collectors.joining(", "));;

        Object result;
        try {
            result = joinPoint.proceed();
            Object maskedResult = maskSensitiveData(result);
            long duration = System.currentTimeMillis() - start;

            log.info("AUDIT | IP={} | Method={} | Input={} | Output={} | Time={}ms",
                    clientIp, methodName, inputArgs,
                    new ObjectMapper().writeValueAsString(maskedResult),
                    duration);
            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - start;
            log.error("AUDIT | IP={} | Method={} | Input={} | Exception={} | Time={}ms",
                    clientIp, methodName, inputArgs,
                    ex.getMessage(), duration);
            throw ex;
        }
    }

    private String getClientIP() {
        try {
            HttpServletRequest request =
                    ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            return forwarded != null ? forwarded.split(",")[0] : request.getRemoteAddr();
        } catch (Exception e) {
            return "N/A";
        }
    }

    private Object maskSensitiveData(Object data) {
        if (data == null) return null;

        ObjectMapper mapper = new ObjectMapper();
        try {
            Map<String, Object> map = mapper.convertValue(data, new TypeReference<>() {});
            for (String key : map.keySet()) {
                if (SENSITIVE_FIELDS.contains(key)) {
                    map.put(key, "***MASKED***");
                }
            }
            return map;
        } catch (IllegalArgumentException e) {
            // return the original object if it cannot be converted to a map
            return data;
        }
    }
}