package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.dto.UserDetailDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class AppUtilsTest {


    @Test
    void getCurrentUser_returnsUserDetailDto_whenPresent() {
        UserDetailDto user = UserDetailDto.builder().id(1L).email("test@abc.com").build();
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getDetails()).thenReturn(user);

        SecurityContext context = Mockito.mock(SecurityContext.class);
        Mockito.when(context.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(context);

        UserDetailDto result = AppUtils.getCurrentUser();
        assertThat(result).isEqualTo(user);

        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_returnsNull_whenNoAuthentication() {
        SecurityContext context = Mockito.mock(SecurityContext.class);
        Mockito.when(context.getAuthentication()).thenReturn(null);

        SecurityContextHolder.setContext(context);

        UserDetailDto result = AppUtils.getCurrentUser();
        assertThat(result).isNull();

        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_returnsNull_whenDetailsNotUserDetailDto() {
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getDetails()).thenReturn("not a user");

        SecurityContext context = Mockito.mock(SecurityContext.class);
        Mockito.when(context.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(context);

        UserDetailDto result = AppUtils.getCurrentUser();
        assertThat(result).isNull();

        SecurityContextHolder.clearContext();
    }
}