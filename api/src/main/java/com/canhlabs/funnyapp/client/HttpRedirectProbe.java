package com.canhlabs.funnyapp.client;

import com.canhlabs.funnyapp.utils.RedirectProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * One HEAD request with redirects disabled: the caller only learns the {@code Location} header and validates it
 * itself, so a redirect can never make the server fetch an arbitrary host (CWE-918).
 */
@Slf4j
@Component
public class HttpRedirectProbe implements RedirectProbe {

    static final Duration TIMEOUT = Duration.ofSeconds(8);
    /** Short wait for a permit: under load, fail fast instead of piling up blocked request threads. */
    static final Duration PERMIT_WAIT = Duration.ofSeconds(2);
    static final int MAX_CONCURRENT = 4;
    /** Probes run on request threads: cap concurrent outbound calls (CWE-770). */
    private final Semaphore permits;
    private final HttpClient client;

    public HttpRedirectProbe() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build(), new Semaphore(MAX_CONCURRENT));
    }

    HttpRedirectProbe(HttpClient client, Semaphore permits) {
        this.client = client;
        this.permits = permits;
    }

    @Override
    public Optional<String> location(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(PERMIT_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new Unavailable("busy");
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(TIMEOUT)
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 300 || status >= 400) {
                log.info("Short link probe got HTTP {}", status);
                return Optional.empty();
            }
            return response.headers().firstValue("location").flatMap(loc -> resolve(uri, loc));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable("interrupted");
        } catch (IOException e) {
            log.info("Short link probe failed: {}", e.getClass().getSimpleName());
            throw new Unavailable("io");
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    private static Optional<String> resolve(URI base, String location) {
        try {
            return Optional.of(base.resolve(location.trim()).toString());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
