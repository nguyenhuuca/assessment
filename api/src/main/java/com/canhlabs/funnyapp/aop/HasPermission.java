package com.canhlabs.funnyapp.aop;

import com.canhlabs.funnyapp.enums.Permission;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@permissionServiceImpl.hasPermission(authentication.principal.permissions, {perm})")
public @interface HasPermission {
    // AnnotationTemplateExpressionDefaults expands Permission.ADMIN to the bare identifier ADMIN.
    // PermissionEnumPropertyAccessor (registered in WebSecurityConfig) makes ADMIN resolvable
    // in the SpEL context by mapping enum names to their Permission constants.
    Permission perm();
}