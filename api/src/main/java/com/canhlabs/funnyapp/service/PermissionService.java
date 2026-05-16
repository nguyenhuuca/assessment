package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.enums.Permission;

public interface PermissionService {

    boolean hasPermission(int userPermissions, Permission perm);
    boolean hasAllPermissions(int userPerm, Permission[] perms);
    boolean hasAnyPermission(int userPerm, Permission[] perms);
}
