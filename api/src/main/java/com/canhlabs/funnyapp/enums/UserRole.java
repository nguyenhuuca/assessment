package com.canhlabs.funnyapp.enums;

/**
 * Declared lowest to highest; this order is the only place role rank is defined.
 * Used by {@link #outranks} (account management) and WebSecurityConfig.roleHierarchy() (hasRole checks).
 * Append new roles in rank position; never reorder existing ones without reviewing both.
 */
public enum UserRole {
    USER,
    ADMIN,
    /** Highest role; exactly one account (DB-enforced). */
    SUPER_ADMIN;

    public boolean outranks(UserRole other) {
        return other != null && ordinal() > other.ordinal();
    }

    /** Permission bits an account gets when it is given this role (admins: every bit, users: none). */
    public int defaultPermissions() {
        return this == USER ? 0 : Permission.ALL_BITS;
    }
}
