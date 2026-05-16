package com.canhlabs.funnyapp.config;

import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserDetailsTest {

    private AppUserDetails details(UserRole role, int permissions) {
        User user = User.builder()
                .id(1L)
                .userName("user@example.com")
                .password("secret")
                .role(role)
                .permissions(permissions)
                .build();
        return new AppUserDetails(user);
    }

    // ── permissions ────────────────────────────────────────────────────────────

    @Test
    void getPermissions_noPermissions_returnsZero() {
        assertThat(details(UserRole.USER, 0).getPermissions()).isEqualTo(0);
    }

    @Test
    void getPermissions_singleBit_returnsCorrectValue() {
        int perm = Permission.READ.getBit();
        assertThat(details(UserRole.USER, perm).getPermissions()).isEqualTo(perm);
    }

    @Test
    void getPermissions_combinedBits_returnsCorrectValue() {
        int perm = Permission.READ.getBit() | Permission.WRITE.getBit() | Permission.ADMIN.getBit();
        assertThat(details(UserRole.ADMIN, perm).getPermissions()).isEqualTo(perm);
    }

    // ── authorities ────────────────────────────────────────────────────────────

    @Test
    void getAuthorities_userRole_returnsRoleUser() {
        Collection<? extends GrantedAuthority> authorities = details(UserRole.USER, 0).getAuthorities();
        assertThat(authorities).hasSize(1);
        assertThat(authorities.iterator().next().getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    void getAuthorities_adminRole_returnsRoleAdmin() {
        Collection<? extends GrantedAuthority> authorities = details(UserRole.ADMIN, 0).getAuthorities();
        assertThat(authorities).hasSize(1);
        assertThat(authorities.iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");
    }

    // ── UserDetails contract ───────────────────────────────────────────────────

    @Test
    void getUsername_returnsUserName() {
        assertThat(details(UserRole.USER, 0).getUsername()).isEqualTo("user@example.com");
    }

    @Test
    void getPassword_returnsPassword() {
        assertThat(details(UserRole.USER, 0).getPassword()).isEqualTo("secret");
    }

    @Test
    void getId_returnsId() {
        assertThat(details(UserRole.USER, 0).getId()).isEqualTo(1L);
    }

    @Test
    void accountFlags_allReturnTrue() {
        AppUserDetails d = details(UserRole.USER, 0);
        assertThat(d.isAccountNonExpired()).isTrue();
        assertThat(d.isAccountNonLocked()).isTrue();
        assertThat(d.isCredentialsNonExpired()).isTrue();
        assertThat(d.isEnabled()).isTrue();
    }
}
