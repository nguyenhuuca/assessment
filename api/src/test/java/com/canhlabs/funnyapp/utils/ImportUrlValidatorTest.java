package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.enums.ImportPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ImportUrlValidatorTest {

    private static final String YT = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private final ImportUrlValidator validator = new ImportUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=30s&list=PL123",
            "https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ",
            "  https://youtu.be/dQw4w9WgXcQ  ",
            "https://www.youtube.com:443/watch?v=dQw4w9WgXcQ"
    })
    void youtubeVariants_normalizeToWatchUrl(String raw) {
        var parsed = validator.parse(raw);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().normalizedUrl()).isEqualTo(YT);
        assertThat(parsed.get().platform()).isEqualTo(ImportPlatform.YOUTUBE);
        assertThat(parsed.get().videoId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void sourceUrl_isTheTrimmedInput() {
        assertThat(validator.parse("  https://youtu.be/dQw4w9WgXcQ ").orElseThrow().sourceUrl())
                .isEqualTo("https://youtu.be/dQw4w9WgXcQ");
    }

    @Test
    void facebookReel_normalizes() {
        var parsed = validator.parse("https://m.facebook.com/reel/1234567890123?s=x").orElseThrow();

        assertThat(parsed.normalizedUrl()).isEqualTo("https://www.facebook.com/reel/1234567890123");
        assertThat(parsed.platform()).isEqualTo(ImportPlatform.FACEBOOK);
        assertThat(parsed.videoId()).isEqualTo("1234567890123");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.facebook.com/somepage/videos/1234567890123/",
            "https://facebook.com/videos/1234567890123",
            "https://www.facebook.com/watch/?v=1234567890123",
            "https://www.facebook.com/watch?v=1234567890123&ref=x"
    })
    void facebookVideos_normalizeToWatchUrl(String raw) {
        assertThat(validator.parse(raw).orElseThrow().normalizedUrl())
                .isEqualTo("https://www.facebook.com/watch/?v=1234567890123");
    }

    @Test
    void fbWatch_keptAsIs() {
        var parsed = validator.parse("https://fb.watch/abcDEF_12-x/?mibextid=1").orElseThrow();

        assertThat(parsed.normalizedUrl()).isEqualTo("https://fb.watch/abcDEF_12-x");
        assertThat(parsed.platform()).isEqualTo(ImportPlatform.FACEBOOK);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "ftp://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "//www.youtube.com/watch?v=dQw4w9WgXcQ",
            "www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
            "https://evilyoutube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
            "https://evil.com/www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://evil.com/?u=https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com@evil.com/watch?v=dQw4w9WgXcQ",
            "https://user:pass@www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com\\@evil.com/watch?v=dQw4w9WgXcQ",
            "https://127.0.0.1/watch?v=dQw4w9WgXcQ",
            "https://[::1]/watch?v=dQw4w9WgXcQ",
            "https://169.254.169.254/latest/meta-data",
            "https://2130706433/",
            "https://www.youtube.com.:443/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com:8443/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com./watch?v=dQw4w9WgXcQ",
            "https://vimeo.com/12345",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ%0a--exec",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ extra",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ\nhttps://evil.com",
            "https://www.youtube.com/playlist?list=PL123",
            "https://www.youtube.com/watch",
            "https://www.youtube.com/",
            "https://youtu.be/",
            "https://youtu.be/a/b",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ/extra",
            "https://www.facebook.com/reel/abc",
            "https://www.facebook.com/somepage",
            "https://www.facebook.com/watch/?v=notdigits",
            "https://fb.watch/",
            "https://fb.watch/a",
    })
    void rejected(String raw) {
        assertThat(validator.parse(raw)).isEmpty();
    }

    @Test
    void tooLong_rejected() {
        String longUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&x=" + "a".repeat(2048);

        assertThat(validator.parse(longUrl)).isEmpty();
    }

    @Test
    void exactlyMaxLength_accepted() {
        String base = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&x=";
        String url = base + "a".repeat(ImportUrlValidator.MAX_URL_LENGTH - base.length());

        assertThat(url).hasSize(ImportUrlValidator.MAX_URL_LENGTH);
        assertThat(validator.parse(url)).isPresent();
    }
}
