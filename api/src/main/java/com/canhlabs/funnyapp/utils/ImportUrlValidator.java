package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.enums.ImportPlatform;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and normalizes admin supplied import URLs (ADR-0019 D4). Only https URLs on an exact host
 * allowlist are accepted, which keeps the generic extractor / arbitrary hosts (SSRF) out of yt-dlp.
 */
@Component
public class ImportUrlValidator {

    public static final int MAX_URL_LENGTH = 2048;

    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com");
    private static final Set<String> FACEBOOK_HOSTS = Set.of("facebook.com", "www.facebook.com", "m.facebook.com");
    private static final String SHORT_YOUTUBE_HOST = "youtu.be";
    private static final String SHORT_FACEBOOK_HOST = "fb.watch";

    private static final Pattern YT_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern DIGITS = Pattern.compile("\\d{5,30}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{3,64}");
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[\\p{Cntrl}\\s\\\\]");

    /** Result of a successful validation. */
    public record ImportUrl(String sourceUrl, String normalizedUrl, ImportPlatform platform, String videoId) {
    }

    public Optional<ImportUrl> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String url = raw.trim();
        if (url.isEmpty() || url.length() > MAX_URL_LENGTH || UNSAFE_CHARS.matcher(url).find()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getRawUserInfo() != null
                || uri.getRawAuthority() == null
                || uri.getRawAuthority().indexOf('@') >= 0
                || uri.getHost() == null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return Optional.empty();
        }
        String host = uri.getHost().toLowerCase();
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();

        if (YOUTUBE_HOSTS.contains(host)) {
            return youtubeWatchOrShorts(url, path, uri.getRawQuery());
        }
        if (SHORT_YOUTUBE_HOST.equals(host)) {
            return youtubeShortLink(url, path);
        }
        if (FACEBOOK_HOSTS.contains(host)) {
            return facebook(url, path, uri.getRawQuery());
        }
        if (SHORT_FACEBOOK_HOST.equals(host)) {
            return facebookShortLink(url, path);
        }
        return Optional.empty();
    }

    private Optional<ImportUrl> youtubeWatchOrShorts(String source, String path, String query) {
        String id = null;
        if ("/watch".equals(path) || "/watch/".equals(path)) {
            id = queryParam(query, "v");
        } else {
            Matcher m = Pattern.compile("^/shorts/([^/]+)/?$").matcher(path);
            if (m.matches()) {
                id = m.group(1);
            }
        }
        return youtube(source, id);
    }

    private Optional<ImportUrl> youtubeShortLink(String source, String path) {
        Matcher m = Pattern.compile("^/([^/]+)/?$").matcher(path);
        return youtube(source, m.matches() ? m.group(1) : null);
    }

    private Optional<ImportUrl> youtube(String source, String id) {
        if (id == null || !YT_ID.matcher(id).matches()) {
            return Optional.empty();
        }
        return Optional.of(new ImportUrl(source, "https://www.youtube.com/watch?v=" + id, ImportPlatform.YOUTUBE, id));
    }

    private Optional<ImportUrl> facebook(String source, String path, String query) {
        String reelId = firstGroup("^/reel/(\\d+)/?$", path);
        if (reelId != null) {
            return facebookId(source, "https://www.facebook.com/reel/", reelId);
        }
        String videoId = firstGroup("^(?:/[^/]+)?/videos/(\\d+)/?$", path);
        if (videoId == null && ("/watch".equals(path) || "/watch/".equals(path))) {
            videoId = queryParam(query, "v");
        }
        if (videoId == null) {
            return Optional.empty();
        }
        return facebookId(source, "https://www.facebook.com/watch/?v=", videoId);
    }

    private Optional<ImportUrl> facebookId(String source, String prefix, String id) {
        if (!DIGITS.matcher(id).matches()) {
            return Optional.empty();
        }
        return Optional.of(new ImportUrl(source, prefix + id, ImportPlatform.FACEBOOK, id));
    }

    private Optional<ImportUrl> facebookShortLink(String source, String path) {
        String token = firstGroup("^/([^/]+)/?$", path);
        if (token == null || !TOKEN.matcher(token).matches()) {
            return Optional.empty();
        }
        return Optional.of(new ImportUrl(source, "https://fb.watch/" + token, ImportPlatform.FACEBOOK, token));
    }

    private static String firstGroup(String regex, String input) {
        Matcher m = Pattern.compile(regex).matcher(input);
        return m.matches() ? m.group(1) : null;
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
