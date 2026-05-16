package com.canhlabs.funnyapp.enums;

import lombok.Getter;

@Getter
public enum Permission {

    READ(1 << 0),
    WRITE(1 << 1),
    EXEC(1 << 2),
    DELETE(1 << 3),
    ADMIN(1 << 4);

    private final int bit;

    Permission(int bit) {
        this.bit = bit;
    }

    public int getBit() {
        return bit;
    }
}
