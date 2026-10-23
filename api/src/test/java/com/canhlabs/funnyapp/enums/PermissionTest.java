package com.canhlabs.funnyapp.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

    @Test
    void bits_andMasks() {
        assertThat(Permission.AUTOPILOT_AUTO_MERGE.getBit()).isEqualTo(32);
        assertThat(Permission.AUTOPILOT_AUTO.getBit()).isEqualTo(64);
        assertThat(Permission.ALL_BITS).isEqualTo(31);
        assertThat(Permission.AUTOPILOT_BITS).isEqualTo(96);
    }

    @Test
    void roleDefaults() {
        assertThat(UserRole.USER.defaultPermissions()).isZero();
        assertThat(UserRole.CONTRIBUTOR.defaultPermissions()).isZero();
        assertThat(UserRole.ADMIN.defaultPermissions()).isEqualTo(31);
        assertThat(UserRole.SUPER_ADMIN.defaultPermissions()).isEqualTo(31);
        assertThat(UserRole.values()).containsExactly(
                UserRole.USER, UserRole.CONTRIBUTOR, UserRole.ADMIN, UserRole.SUPER_ADMIN);
        assertThat(UserRole.ADMIN.outranks(UserRole.CONTRIBUTOR)).isTrue();
        assertThat(UserRole.CONTRIBUTOR.outranks(UserRole.USER)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "0,AUTO,false", "0,AUTO_MERGE,false",
            "32,AUTO,false", "32,AUTO_MERGE,true",
            "64,AUTO,true", "64,AUTO_MERGE,true",
            "96,AUTO,true", "96,AUTO_MERGE,true",
            "31,AUTO,false", "31,AUTO_MERGE,false"})
    void allowsAutoLabel_followsBr8(int mask, ReportAutoLabel label, boolean expected) {
        assertThat(Permission.allowsAutoLabel(mask, label)).isEqualTo(expected);
    }

    @Test
    void allowsAutoLabel_nullLabelAlwaysAllowed() {
        assertThat(Permission.allowsAutoLabel(0, null)).isTrue();
    }
}
