package com.canhlabs.funnyapp.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitleSanitizerTest {

    @Test
    void nullAndBlank_becomeEmpty() {
        assertThat(TitleSanitizer.sanitize(null)).isEmpty();
        assertThat(TitleSanitizer.sanitize("   \t\n ")).isEmpty();
    }

    @Test
    void normalTitle_unchanged() {
        assertThat(TitleSanitizer.sanitize("Mèo con dễ thương")).isEqualTo("Mèo con dễ thương");
    }

    @Test
    void forbiddenCharacters_stripped() {
        assertThat(TitleSanitizer.sanitize("a/b\\c:d*e?f\"g<h>i|j")).isEqualTo("abcdefghij");
    }

    @Test
    void controlCharacters_stripped() {
        assertThat(TitleSanitizer.sanitize("a\u0000b\u0007c\u007fd e")).isEqualTo("abcde");
    }

    @Test
    void whitespace_collapsedAndTrimmed() {
        assertThat(TitleSanitizer.sanitize("  hello \t\n  world   again  ")).isEqualTo("hello world again");
    }

    @Test
    void pathTraversal_neutralised() {
        assertThat(TitleSanitizer.sanitize("../../etc/passwd")).isEqualTo("....etcpasswd");
    }

    @Test
    void longTitle_cutAtMax() {
        String result = TitleSanitizer.sanitize("x".repeat(500));

        assertThat(result).hasSize(TitleSanitizer.MAX_LENGTH);
    }

    @Test
    void cutDoesNotSplitSurrogatePair() {
        String title = "x".repeat(TitleSanitizer.MAX_LENGTH - 1) + "😀more";

        String result = TitleSanitizer.sanitize(title);

        assertThat(result).isEqualTo("x".repeat(TitleSanitizer.MAX_LENGTH - 1));
    }

    @Test
    void cutRetrimsTrailingSpace() {
        String title = "x".repeat(TitleSanitizer.MAX_LENGTH - 1) + " tail";

        assertThat(TitleSanitizer.sanitize(title)).isEqualTo("x".repeat(TitleSanitizer.MAX_LENGTH - 1));
    }

    @Test
    void onlyForbiddenChars_becomeEmpty() {
        assertThat(TitleSanitizer.sanitize("///:::")).isEmpty();
    }
}
