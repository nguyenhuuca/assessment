package com.canhlabs.funnyapp.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DriveFileLocksTest {

    @Test
    void claimIsExclusiveUntilReleased() {
        DriveFileLocks locks = new DriveFileLocks();

        assertThat(locks.tryClaim("f")).isTrue();
        assertThat(locks.tryClaim("f")).isFalse();
        assertThat(locks.isHeld("f")).isTrue();

        locks.release("f");

        assertThat(locks.isHeld("f")).isFalse();
        assertThat(locks.tryClaim("f")).isTrue();
    }
}
