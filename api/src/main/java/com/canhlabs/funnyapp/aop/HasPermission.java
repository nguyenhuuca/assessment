package com.canhlabs.funnyapp.aop;

import com.canhlabs.funnyapp.enums.Permission;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@permissionServiceImpl.hasAllPermissions(authentication.principal.permissions, #perms)")
public @interface HasPermission {
    Permission[] perms();
}