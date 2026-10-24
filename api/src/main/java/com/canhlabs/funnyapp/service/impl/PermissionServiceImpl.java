package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.PermissionService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    private static final String ENFORCED_KEY = "enforced";

    private final Cache<String, Boolean> enforcedCache = CacheBuilder.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();

    private FeatureFlagService featureFlagService;

    @Autowired
    public void injectFeatureFlagService(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Override
    public boolean hasPermission(int userPermissions, Permission perm) {
        if (!isEnforced()) {
            log.debug("permission check skipped (flag off): required={}", perm);
            return true;
        }
        boolean granted = (userPermissions & perm.getBit()) != 0;
        log.debug("hasPermission: userBits={} required={} granted={}", userPermissions, perm, granted);
        return granted;
    }

    @Override
    public boolean hasAllPermissions(int userPerm, Permission[] perms) {
        if (!isEnforced()) {
            log.debug("permission check skipped (flag off): required={}", Arrays.toString(perms));
            return true;
        }
        for (Permission p : perms) {
            if ((userPerm & p.getBit()) == 0) {
                log.info("hasAllPermissions DENIED: userBits={} missing={} required={}",
                        userPerm, p, permNames(perms));
                return false;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("hasAllPermissions GRANTED: userBits={} required={}", userPerm, permNames(perms));
        }
        return true;
    }

    @Override
    public boolean hasAnyPermission(int userPerm, Permission[] perms) {
        if (!isEnforced()) {
            log.debug("permission check skipped (flag off): required={}", Arrays.toString(perms));
            return true;
        }
        for (Permission p : perms) {
            if ((userPerm & p.getBit()) != 0) {
                if (log.isDebugEnabled()) {
                    log.debug("hasAnyPermission GRANTED: userBits={} matched={} required={}",
                            userPerm, p, permNames(perms));
                }
                return true;
            }
        }
        log.info("hasAnyPermission DENIED: userBits={} required={}", userPerm, permNames(perms));
        return false;
    }

    // The flag is an operational switch: a flip may take up to 15 s to apply, which saves a lookup per check.
    private boolean isEnforced() {
        try {
            return enforcedCache.get(ENFORCED_KEY, this::loadEnforced);
        } catch (ExecutionException | UncheckedExecutionException e) {
            return loadEnforced();
        }
    }

    private boolean loadEnforced() {
        return featureFlagService.isEnabled(AppConstant.Flags.PERMISSION_ENFORCEMENT, false);
    }

    /** Drops the cached flag so the next check reads it afresh (tests that flip the flag between requests). */
    public void invalidateEnforcementCache() {
        enforcedCache.invalidateAll();
    }

    private static String permNames(Permission[] perms) {
        return Arrays.stream(perms).map(Permission::name).collect(Collectors.joining(", "));
    }
}
