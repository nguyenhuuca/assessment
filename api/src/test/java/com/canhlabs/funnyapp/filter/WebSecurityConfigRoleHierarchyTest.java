package com.canhlabs.funnyapp.filter;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WebSecurityConfigRoleHierarchyTest {

    private static Set<String> reachable(String role) {
        return WebSecurityConfig.roleHierarchy()
                .getReachableGrantedAuthorities(AuthorityUtils.createAuthorityList(role))
                .stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    void superAdminImpliesEveryLowerRole() {
        assertThat(reachable("ROLE_SUPER_ADMIN")).containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_CONTRIBUTOR", "ROLE_USER");
    }

    @Test
    void adminImpliesContributorAndUser() {
        assertThat(reachable("ROLE_ADMIN")).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_CONTRIBUTOR", "ROLE_USER");
    }

    @Test
    void contributorImpliesUserOnly() {
        assertThat(reachable("ROLE_CONTRIBUTOR")).containsExactlyInAnyOrder("ROLE_CONTRIBUTOR", "ROLE_USER");
    }

    @Test
    void userImpliesNothingElse() {
        assertThat(reachable("ROLE_USER")).containsExactly("ROLE_USER");
    }
}
