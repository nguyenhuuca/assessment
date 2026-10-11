package com.canhlabs.funnyapp.enums;

/** Declared lowest to highest: an account can only manage accounts and assign roles strictly below its own. */
public enum UserRole {
    USER,
    ADMIN,
    /** Highest role; exactly one account (DB-enforced). */
    SUPER_ADMIN;

    public boolean outranks(UserRole other) {
        return other != null && ordinal() > other.ordinal();
    }
}
