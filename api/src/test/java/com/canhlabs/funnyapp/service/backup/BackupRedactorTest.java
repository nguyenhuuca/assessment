package com.canhlabs.funnyapp.service.backup;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackupRedactorTest {

    @Test
    void masksSecretInMiddleOfText() {
        assertThat(BackupRedactor.redact("auth failed for pw=hunter2 on host", 500, List.of("hunter2")))
                .isEqualTo("auth failed for pw=*** on host");
    }

    @Test
    void masksEveryOccurrenceAndEverySecret() {
        assertThat(BackupRedactor.redact("a1 b2 a1 b2", 500, List.of("a1", "b2")))
                .isEqualTo("*** *** *** ***");
    }

    @Test
    void ignoresBlankAndNullSecrets() {
        assertThat(BackupRedactor.redact("keep this", 500, Arrays.asList(null, "", "  ")))
                .isEqualTo("keep this");
    }

    @Test
    void nullSecretCollectionIsAllowed() {
        assertThat(BackupRedactor.redact("text", 500, null)).isEqualTo("text");
    }

    @Test
    void nullTextBecomesEmpty() {
        assertThat(BackupRedactor.redact(null, 10, List.of("x"))).isEmpty();
    }

    @Test
    void truncatesWithEllipsis() {
        assertThat(BackupRedactor.redact("abcdefghij", 4, List.of())).isEqualTo("abcd…");
    }

    @Test
    void doesNotTruncateAtExactLength() {
        assertThat(BackupRedactor.redact("abcd", 4, List.of())).isEqualTo("abcd");
    }

    @Test
    void redactsBeforeTruncatingSoSecretCannotLeakAsPrefix() {
        assertThat(BackupRedactor.redact("xxsecretyy", 4, List.of("secret"))).isEqualTo("xx**…");
    }
}
