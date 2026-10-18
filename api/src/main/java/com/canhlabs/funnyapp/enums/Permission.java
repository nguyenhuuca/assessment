package com.canhlabs.funnyapp.enums;

import lombok.Getter;

@Getter
public enum Permission {

    READ(1 << 0),
    WRITE(1 << 1),
    EXEC(1 << 2),
    DELETE(1 << 3),
    ADMIN(1 << 4);

    /** Every defined bit; any other bit in a stored mask is invalid. */
    public static final int ALL_BITS = 0b11111;

    private final int bit;

    Permission(int bit) {
        this.bit = bit;
    }

    public int getBit() {
        return bit;
    }
}
