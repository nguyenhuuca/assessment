package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.PermissionService;
import com.canhlabs.funnyapp.utils.AppConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    private FeatureFlagService featureFlagService;

    @Autowired
    public void injectFeatureFlagService(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Override
    public boolean hasPermission(int userPermissions, Permission perm) {
        if (!isEnforced()) {
            log.info("permission check skipped (flag off): required={}", perm);
            return true;
        }
        boolean granted = (userPermissions & perm.getBit()) != 0;
        log.info("hasPermission: userBits={} required={} granted={}", userPermissions, perm, granted);
        return granted;
    }

    @Override
    public boolean hasAllPermissions(int userPerm, Permission[] perms) {
        if (!isEnforced()) {
            log.info("permission check skipped (flag off): required={}", Arrays.toString(perms));
            return true;
        }
        for (Permission p : perms) {
            if ((userPerm & p.getBit()) == 0) {
                log.info("hasAllPermissions DENIED: userBits={} missing={} required={}",
                        userPerm, p, permNames(perms));
                return false;
            }
        }
        log.info("hasAllPermissions GRANTED: userBits={} required={}", userPerm, permNames(perms));
        return true;
    }

    @Override
    public boolean hasAnyPermission(int userPerm, Permission[] perms) {
        if (!isEnforced()) {
            log.info("permission check skipped (flag off): required={}", Arrays.toString(perms));
            return true;
        }
        for (Permission p : perms) {
            if ((userPerm & p.getBit()) != 0) {
                log.info("hasAnyPermission GRANTED: userBits={} matched={} required={}",
                        userPerm, p, permNames(perms));
                return true;
            }
        }
        log.info("hasAnyPermission DENIED: userBits={} required={}", userPerm, permNames(perms));
        return false;
    }

    private boolean isEnforced() {
        return featureFlagService.isEnabled(AppConstant.Flags.PERMISSION_ENFORCEMENT, false);
    }

    private static String permNames(Permission[] perms) {
        return Arrays.stream(perms).map(Permission::name).collect(Collectors.joining(", "));
    }
}
