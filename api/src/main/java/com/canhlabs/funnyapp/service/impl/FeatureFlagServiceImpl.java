package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.service.FeatureFlagService;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FeatureFlagServiceImpl implements FeatureFlagService {

    @Value("${harness.ff.api-key:}")
    private String apiKey;

    private CfClient cfClient;

    @PostConstruct
    void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("Harness FF api-key not set — feature flags will use default values");
            return;
        }
        cfClient = new CfClient(apiKey);
        try {
            cfClient.waitForInitialization();
            log.info("Harness FF initialized successfully");
        } catch (Exception e) {
            log.warn("Harness FF unreachable at startup, flag evaluations will use default values", e);
        }
    }

    @PreDestroy
    void destroy() throws Exception {
        if (cfClient != null) {
            cfClient.close();
        }
    }

    @Override
    public boolean isEnabled(String flag, boolean defaultValue) {
        if (cfClient == null) return defaultValue;
        Target target = Target.builder()
                .identifier(currentUserId())
                .name(currentUserId())
                .build();
        return cfClient.boolVariation(flag, target, defaultValue);
    }

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "system";
    }
}
