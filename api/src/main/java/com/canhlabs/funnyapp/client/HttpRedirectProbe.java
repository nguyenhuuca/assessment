package com.canhlabs.funnyapp.client;

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
    /** Probes run on request threads: cap concurrent outbound calls (CWE-770). */
    private final Semaphore permits = new Semaphore(4);
    private final HttpClient client;

    public HttpRedirectProbe() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    HttpRedirectProbe(HttpClient client) {
        this.client = client;
    }

    @Override
    public Optional<String> location(String url) {
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                return Optional.empty();
            }
            URI uri = URI.create(url);
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
            return response.headers().firstValue("location").map(loc -> uri.resolve(loc.trim()).toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            log.info("Short link probe failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }
}
