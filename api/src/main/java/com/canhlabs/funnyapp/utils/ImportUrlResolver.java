package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.client.RedirectProbe;
import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.utils.ImportUrlValidator.ImportUrl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link ImportUrlValidator#parse} plus Facebook short links: {@code facebook.com/share/v|r/<token>} and
 * {@code fb.watch/<token>} are expanded through Facebook's redirect to the reel / watch URL yt-dlp can extract.
 * The redirect target goes through the same allowlist, and the user's original link is kept as the source URL.
 */
@Component
@RequiredArgsConstructor
public class ImportUrlResolver {

    private final ImportUrlValidator validator;
    private final RedirectProbe redirectProbe;

    public Optional<ImportUrl> resolve(String raw) {
        Optional<ImportUrl> direct = validator.parse(raw);
        Optional<String> shortLink = validator.expandableShortLink(raw);
        if (shortLink.isEmpty()) {
            return direct;
        }
        Optional<ImportUrl> expanded = redirectProbe.location(shortLink.get())
                .flatMap(validator::parse)
                .filter(u -> u.platform() == ImportPlatform.FACEBOOK && validator.expandableShortLink(u.normalizedUrl()).isEmpty())
                .map(u -> new ImportUrl(raw.trim(), u.normalizedUrl(), u.platform(), u.videoId()));
        // fb.watch was accepted before expansion existed: keep it when Facebook does not redirect to a video
        return expanded.isPresent() ? expanded : direct;
    }
}
