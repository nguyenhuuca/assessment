package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.utils.ImportUrlValidator.ImportUrl;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * {@link ImportUrlValidator#parse} plus Facebook short links: {@code facebook.com/share/v|r/<token>} and
 * {@code fb.watch/<token>} are expanded through Facebook's redirect to the reel / watch URL yt-dlp can extract.
 * The redirect target goes through the same allowlist, and the user's original link is kept as the source URL.
 * Expansions (including failures) are cached per short link, so repeated submits do not hit Facebook again.
 */
@Component
public class ImportUrlResolver {

    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    static final int CACHE_SIZE = 1_000;

    private final ImportUrlValidator validator;
    private final RedirectProbe redirectProbe;
    /** short link → expanded URL; empty = Facebook did not redirect to an allowed video. */
    private final Cache<String, Optional<ImportUrl>> expansions;

    @Autowired
    public ImportUrlResolver(ImportUrlValidator validator, RedirectProbe redirectProbe) {
        this(validator, redirectProbe, CacheBuilder.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(CACHE_SIZE)
                .build());
    }

    ImportUrlResolver(ImportUrlValidator validator, RedirectProbe redirectProbe, Cache<String, Optional<ImportUrl>> expansions) {
        this.validator = validator;
        this.redirectProbe = redirectProbe;
        this.expansions = expansions;
    }

    public Optional<ImportUrl> resolve(String raw) {
        Optional<String> shortLink = validator.expandableShortLink(raw);
        if (shortLink.isEmpty()) {
            return validator.parse(raw);
        }
        Optional<ImportUrl> expanded = expand(shortLink.get())
                .map(u -> new ImportUrl(raw.trim(), u.normalizedUrl(), u.platform(), u.videoId()));
        // fb.watch was accepted before expansion existed: keep it when Facebook does not redirect to a video
        return expanded.isPresent() ? expanded : validator.parse(raw);
    }

    private Optional<ImportUrl> expand(String shortLink) {
        try {
            // Guava loads a key once even under concurrent submits of the same link
            return expansions.get(shortLink, () -> redirectProbe.location(shortLink)
                    .flatMap(validator::parse)
                    .filter(u -> u.platform() == ImportPlatform.FACEBOOK
                            && validator.expandableShortLink(u.normalizedUrl()).isEmpty()));
        } catch (ExecutionException | UncheckedExecutionException e) {
            // RedirectProbe.Unavailable (busy / timeout): not cached, the next submit probes again
            return Optional.empty();
        }
    }
}
