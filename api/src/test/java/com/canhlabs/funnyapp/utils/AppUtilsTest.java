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

    @Test
    void getNonNull_returnsEmptyString_whenNull() {
        assertThat(AppUtils.getNonNull(null)).isEqualTo("");
    }

    @Test
    void getNonNull_returnsString_whenNotNull() {
        assertThat(AppUtils.getNonNull(123)).isEqualTo("123");
        assertThat(AppUtils.getNonNull("abc")).isEqualTo("abc");
    }

    @Test
    void isValidEmail_returnsTrue_forValidEmail() {
        assertThat(AppUtils.isValidEmail("test@example.com")).isTrue();
        assertThat(AppUtils.isValidEmail("user.name+tag@domain.co.uk")).isTrue();
    }

    @Test
    void isValidEmail_returnsFalse_forInvalidEmail() {
        assertThat(AppUtils.isValidEmail("invalid-email")).isFalse();
        assertThat(AppUtils.isValidEmail("@no-local-part.com")).isFalse();
        assertThat(AppUtils.isValidEmail(null)).isFalse();
    }

    @Test
    void hashCode_returnsZero_whenStringIsNull() {
        assertThat(AppUtils.hashCode(null)).isZero();
    }

    @Test
    void hashCode_returnsZero_whenStringIsEmpty() {
        assertThat(AppUtils.hashCode("")).isZero();
    }

    @Test
    void hashCode_returnsConsistentHash_forSameString() {
        String input = "f8409762-f8c3-48dd-9039-840034e8fddd";
        int hash1 = AppUtils.hashCode(input);
        int hash2 = AppUtils.hashCode(input);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashCode_returnsDifferentHash_forDifferentStrings() {
        int hash1 = AppUtils.hashCode("string1");
        int hash2 = AppUtils.hashCode("string2");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hashCode_handlesSpecialCharactersInString() {
        String input = "!@#$%^&*()_+";
        int hash = AppUtils.hashCode(input);
        assertThat(hash).isNotZero();
    }

    @Test
    void hashCode_handlesUnicodeCharactersInString() {
        String input = "こんにちは世界"; // "Hello World" in Japanese
        int hash = AppUtils.hashCode(input);
        assertThat(hash).isNotZero();
    }
}