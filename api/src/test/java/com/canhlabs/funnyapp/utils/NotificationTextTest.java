package com.canhlabs.funnyapp.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTextTest {

    @Test
    void snippet_collapsesWhitespaceAndTrims() {
        assertThat(NotificationText.snippet("  hello \n\t world   again ")).isEqualTo("hello world again");
    }

    @Test
    void snippet_null_isEmpty() {
        assertThat(NotificationText.snippet(null)).isEmpty();
    }

    @Test
    void snippet_exactlyMax_isUntouched() {
        String s = "a".repeat(NotificationText.SNIPPET_MAX);
        assertThat(NotificationText.snippet(s)).isEqualTo(s);
    }

    @Test
    void snippet_longer_isCappedAtMaxIncludingEllipsis() {
        String result = NotificationText.snippet("word ".repeat(100));
        assertThat(result.length()).isLessThanOrEqualTo(NotificationText.SNIPPET_MAX);
        assertThat(result).endsWith("…");
    }

    @Test
    void actorDisplay_isLocalPartNeverFullEmail() {
        assertThat(NotificationText.actorDisplay("bob@example.com")).isEqualTo("bob");
        assertThat(NotificationText.actorDisplay("  alice.k@x.io ")).isEqualTo("alice.k");
    }

    @Test
    void actorDisplay_noAtSignOrBlank() {
        assertThat(NotificationText.actorDisplay("plain")).isEqualTo("plain");
        assertThat(NotificationText.actorDisplay("")).isNull();
        assertThat(NotificationText.actorDisplay(null)).isNull();
    }

    @Test
    void actorDisplay_limitedTo100Chars() {
        assertThat(NotificationText.actorDisplay("a".repeat(150) + "@x.com")).hasSize(100);
    }
}
