package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.enums.ImportPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ImportUrlResolverTest {

    private static final String SHARE = "https://www.facebook.com/share/v/1HLVN1zZ6T/";
    private static final String REEL_REDIRECT =
            "https://www.facebook.com/reel/1987308575243562/?rdid=x&share_url=https%3A%2F%2Fwww.facebook.com%2Fshare%2Fv%2F1HLVN1zZ6T%2F";

    private final List<String> probed = new ArrayList<>();

    private ImportUrlResolver resolver(Map<String, String> redirects) {
        RedirectProbe probe = url -> {
            probed.add(url);
            return Optional.ofNullable(redirects.get(url));
        };
        return new ImportUrlResolver(new ImportUrlValidator(), probe);
    }

    @Test
    void shareVideoLink_expandsToReel_keepingTheOriginalAsSource() {
        var parsed = resolver(Map.of(SHARE, REEL_REDIRECT)).resolve("  https://m.facebook.com/share/v/1HLVN1zZ6T?mibextid=abc ").orElseThrow();

        assertThat(parsed.normalizedUrl()).isEqualTo("https://www.facebook.com/reel/1987308575243562");
        assertThat(parsed.platform()).isEqualTo(ImportPlatform.FACEBOOK);
        assertThat(parsed.videoId()).isEqualTo("1987308575243562");
        assertThat(parsed.sourceUrl()).isEqualTo("https://m.facebook.com/share/v/1HLVN1zZ6T?mibextid=abc");
        // the probe gets the canonical link rebuilt from the token, never the caller's query string or host
        assertThat(probed).containsExactly(SHARE);
    }

    @Test
    void shareReelLink_expandsToWatchUrl() {
        String share = "https://www.facebook.com/share/r/AbC_d-12/";
        var parsed = resolver(Map.of(share, "https://www.facebook.com/watch/?v=1234567890123")).resolve(share).orElseThrow();

        assertThat(parsed.normalizedUrl()).isEqualTo("https://www.facebook.com/watch/?v=1234567890123");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.facebook.com/login/?next=x",
            "https://evil.com/reel/1987308575243562",
            "http://www.facebook.com/reel/1987308575243562",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://fb.watch/abcdef/",
            "https://www.facebook.com/share/v/other/"
    })
    void shareLink_redirectingOutsideTheAllowlist_isRejected(String target) {
        assertThat(resolver(Map.of(SHARE, target)).resolve(SHARE)).isEmpty();
    }

    @Test
    void shareLink_withoutRedirect_isRejected() {
        assertThat(resolver(Map.of()).resolve(SHARE)).isEmpty();
    }

    @Test
    void fbWatch_expandsWhenFacebookRedirectsToAVideo() {
        var parsed = resolver(Map.of("https://fb.watch/abcDEF_12/", "https://www.facebook.com/reel/1234567890123/"))
                .resolve("https://fb.watch/abcDEF_12/?mibextid=1").orElseThrow();

        assertThat(parsed.normalizedUrl()).isEqualTo("https://www.facebook.com/reel/1234567890123");
        assertThat(parsed.sourceUrl()).isEqualTo("https://fb.watch/abcDEF_12/?mibextid=1");
    }

    @Test
    void fbWatch_keepsTheShortLinkWhenNotExpandable() {
        var parsed = resolver(Map.of("https://fb.watch/abcDEF_12/", "https://www.facebook.com/watch/"))
                .resolve("https://fb.watch/abcDEF_12").orElseThrow();

        assertThat(parsed.normalizedUrl()).isEqualTo("https://fb.watch/abcDEF_12");
    }

    @Test
    void directLinks_doNotProbe() {
        var parsed = resolver(Map.of()).resolve("https://youtu.be/dQw4w9WgXcQ").orElseThrow();

        assertThat(parsed.normalizedUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(resolver(Map.of()).resolve("https://www.facebook.com/reel/1234567890123")).isPresent();
        assertThat(probed).isEmpty();
    }

    @Test
    void expansions_areCachedPerShortLink_includingFailures() {
        ImportUrlResolver r = resolver(Map.of(SHARE, REEL_REDIRECT));

        assertThat(r.resolve(SHARE)).isPresent();
        assertThat(r.resolve("https://m.facebook.com/share/v/1HLVN1zZ6T?x=1")).isPresent();
        assertThat(r.resolve("https://www.facebook.com/share/v/nope123/")).isEmpty();
        assertThat(r.resolve("https://www.facebook.com/share/v/nope123/")).isEmpty();

        assertThat(probed).containsExactly(SHARE, "https://www.facebook.com/share/v/nope123/");
    }

    @Test
    void unavailableProbe_isNotCached_soTheNextSubmitRetries() {
        int[] calls = {0};
        RedirectProbe flaky = url -> {
            if (calls[0]++ == 0) {
                throw new RedirectProbe.Unavailable("busy");
            }
            return Optional.of(REEL_REDIRECT);
        };
        ImportUrlResolver r = new ImportUrlResolver(new ImportUrlValidator(), flaky);

        assertThat(r.resolve(SHARE)).isEmpty();
        assertThat(r.resolve(SHARE)).isPresent();
        assertThat(calls[0]).isEqualTo(2);
    }

    @Test
    void unavailableProbe_onFbWatch_keepsTheShortLink() {
        ImportUrlResolver r = new ImportUrlResolver(new ImportUrlValidator(), url -> {
            throw new RedirectProbe.Unavailable("io");
        });

        assertThat(r.resolve("https://fb.watch/abcDEF_12").orElseThrow().normalizedUrl()).isEqualTo("https://fb.watch/abcDEF_12");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://www.facebook.com/share/v/1HLVN1zZ6T/",
            "https://evil.com/share/v/1HLVN1zZ6T/",
            "https://www.facebook.com/share/x/1HLVN1zZ6T/",
            "https://www.facebook.com/share/v/a/",
            "https://www.facebook.com/share/v/1HLVN1zZ6T/extra",
            "https://www.facebook.com/share/v/bad%2Ftoken/"
    })
    void invalidShareLinks_areRejectedWithoutProbing(String raw) {
        assertThat(resolver(Map.of()).resolve(raw)).isEmpty();
        assertThat(probed).isEmpty();
    }
}
