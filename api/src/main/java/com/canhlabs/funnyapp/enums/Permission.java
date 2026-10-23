package com.canhlabs.funnyapp.enums;

import lombok.Getter;

@Getter
public enum Permission {

    READ(1 << 0),
    WRITE(1 << 1),
    EXEC(1 << 2),
    DELETE(1 << 3),
    ADMIN(1 << 4),
    /** Report may carry autopilot:auto-merge (SUPER_ADMIN-granted, ADR-0030). */
    AUTOPILOT_AUTO_MERGE(1 << 5),
    /** Report may carry autopilot:auto or autopilot:auto-merge (SUPER_ADMIN-granted, ADR-0030). */
    AUTOPILOT_AUTO(1 << 6);

    /** Every role-default bit (admins get these); the autopilot bits are never part of it. */
    public static final int ALL_BITS = 0b11111;

    /** Bits only the SUPER_ADMIN grants, to CONTRIBUTOR and ADMIN accounts. */
    public static final int AUTOPILOT_BITS = 0b1100000;

    private final int bit;

    Permission(int bit) {
        this.bit = bit;
    }

    public int getBit() {
        return bit;
    }

    /** Whether a non-SUPER_ADMIN caller holding {@code mask} may put {@code label} on a report (spec BR-8). */
    public static boolean allowsAutoLabel(int mask, ReportAutoLabel label) {
        if (label == null) {
            return true;
        }
        return switch (label) {
            case AUTO -> (mask & AUTOPILOT_AUTO.bit) != 0;
            case AUTO_MERGE -> (mask & (AUTOPILOT_AUTO.bit | AUTOPILOT_AUTO_MERGE.bit)) != 0;
        };
    }
}
