package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.enums.UserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lombok's builder ignores field initialisers unless the field is @Builder.Default. A missing default on a NOT NULL
 * column caused a production outage (ee6ad36), so every defaulted User field is asserted here.
 */
class UserBuilderDefaultsTest {

    @Test
    void builder_appliesDefaultsForEveryNotNullColumn() {
        User user = User.builder().userName("a@b.com").build();

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPermissions()).isZero();
        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getCredentialsVersion()).isZero();
    }

    @Test
    void builder_nullableColumnsStayNull() {
        User user = User.builder().userName("a@b.com").build();

        assertThat(user.getPassword()).isNull();
        assertThat(user.getPasswordSetAt()).isNull();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void noArgConstructor_hasSameDefaults() {
        User user = new User();

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getCredentialsVersion()).isZero();
    }
}
