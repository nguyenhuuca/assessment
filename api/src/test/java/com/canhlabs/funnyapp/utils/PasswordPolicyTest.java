package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static void assertRejected(String password, String email, String code) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password, email))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(code);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void commonList_isLoadedAndLowercased() {
        assertThat(PasswordPolicy.commonPasswordCount()).isGreaterThanOrEqualTo(1000);
    }

    @Test
    void validPassword_passes() {
        assertThatCode(() -> PasswordPolicy.validate("correct-horse-battery", "alice@example.com")).doesNotThrowAnyException();
    }

    @Test
    void tooShort_nineCharsOrNullOrEmpty() {
        assertRejected("abcdefghi", "a@b.com", "PASSWORD_TOO_SHORT");
        assertRejected(null, "a@b.com", "PASSWORD_TOO_SHORT");
        assertRejected("", "a@b.com", "PASSWORD_TOO_SHORT");
    }

    @Test
    void exactlyTenChars_isAccepted() {
        assertThatCode(() -> PasswordPolicy.validate("zq7!xv9#kw", "a@b.com")).doesNotThrowAnyException();
    }

    @Test
    void lengthIsCountedInCharactersNotBytes() {
        // 10 emoji = 10 code points but 40 bytes: long enough and within 72 bytes
        assertThatCode(() -> PasswordPolicy.validate("😀".repeat(10), "a@b.com")).doesNotThrowAnyException();
    }

    @Test
    void tooLong_isMeasuredInUtf8Bytes() {
        assertThatCode(() -> PasswordPolicy.validate("x7".repeat(36), "a@b.com")).doesNotThrowAnyException(); // 72 bytes
        assertRejected("x7".repeat(36) + "k", "a@b.com", "PASSWORD_TOO_LONG"); // 73 bytes
        // 19 emoji = 76 bytes but only 19 characters
        assertRejected("😀".repeat(19), "a@b.com", "PASSWORD_TOO_LONG");
    }

    @Test
    void containsEmailLocalPart_caseInsensitive() {
        assertRejected("MyALICEsecret99", "alice@example.com", "PASSWORD_CONTAINS_EMAIL");
    }

    @Test
    void shortEmailLocalPart_isIgnored() {
        assertThatCode(() -> PasswordPolicy.validate("zzab-zq7!xv9", "ab@example.com")).doesNotThrowAnyException();
    }

    @Test
    void nullEmail_skipsEmailCheck() {
        assertThatCode(() -> PasswordPolicy.validate("correct-horse-battery", null)).doesNotThrowAnyException();
    }

    @Test
    void commonPassword_rejectedCaseInsensitive() {
        assertRejected("password123", "zed@b.com", "PASSWORD_TOO_COMMON");
        assertRejected("PassWord1234", "zed@b.com", "PASSWORD_TOO_COMMON");
        assertRejected("1234567890", "zed@b.com", "PASSWORD_TOO_COMMON");
    }

    @Test
    void checkOrder_shortBeforeCommon() {
        assertRejected("admin", "zed@b.com", "PASSWORD_TOO_SHORT");
    }
}
