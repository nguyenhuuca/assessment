package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.PermissionService;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PermissionServiceImpl implements PermissionService {

    private FeatureFlagService featureFlagService;

    @Autowired
    public void injectFeatureFlagService(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Override
    public boolean hasPermission(int userPermissions, Permission perm) {
        if (!isEnforced()) return true;
        return (userPermissions & perm.getBit()) != 0;
    }

    @Override
    public boolean hasAllPermissions(int userPerm, Permission[] perms) {
        if (!isEnforced()) return true;
        for (Permission p : perms) {
            if ((userPerm & p.getBit()) == 0) return false;
        }
        return true;
    }

    @Override
    public boolean hasAnyPermission(int userPerm, Permission[] perms) {
        if (!isEnforced()) return true;
        for (Permission p : perms) {
            if ((userPerm & p.getBit()) != 0) return true;
        }
        return false;
    }

    private boolean isEnforced() {
        return featureFlagService.isEnabled(AppConstant.Flags.PERMISSION_ENFORCEMENT, false);
    }
}
